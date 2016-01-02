package main;

import com.google.gson.internal.StringMap;
import com.ullink.slack.simpleslackapi.SlackAttachment;
import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackPersona;
import com.ullink.slack.simpleslackapi.SlackUser;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static main.Utilities.substring;

/**
 * @author Michael Mrozek
 *         Created Dec 31, 2015.
 */
public class SlackNoiseBot extends NoiseBot {
	private final SlackServer server;

	public SlackNoiseBot(SlackServer server, String channel, boolean quiet, String[] fixedModules) {
		super(channel, quiet, fixedModules);
		this.server = server;
	}

	static void createBots(String connectionName, StringMap data) throws IOException {
		final String token = (String)data.get("token");
		final boolean quiet = data.containsKey("quiet") ? (Boolean)data.get("quiet") : false;
		final String[] modules = data.containsKey("modules") ? ((List<String>)data.get("modules")).toArray(new String[0]) : null;
		final SlackServer server = new SlackServer(token);

		final List<String> channels = (List<String>)data.get("channels");
		for(String channel : channels) {
			final NoiseBot bot = new SlackNoiseBot(server, channel, quiet, modules);
			NoiseBot.bots.put(connectionName + channel, bot);
			server.addBot(channel, bot);
		}

		if(!server.connect()) {
			throw new IOException("Unable to connect to server");
		}
	}

	private SlackChannel slackChannel() {
		return this.server.findChannelByName(this.channel.substring(1));
	}

	@Override public Protocol getProtocol() {
		return Protocol.Slack;
	}

	@Override public String getBotNick() {
		return this.server.sessionPersona().getUserName();
	}

	@Override public String[] getNicks() {
		return this.slackChannel().getMembers().stream().map(SlackPersona::getUserName).toArray(String[]::new);
	}

	@Override public boolean clearPendingSends() {
		return false;
	}

	@Override File getStoreDirectory() {
		// Could use SlackPersona.getId(), but I don't want this method to depend on being connected
		// Instead taking the last 10 characters of the API token and hoping to avoid collisions
		final String id = substring(this.server.getToken(), -10);
		return new File(STORE_DIRECTORY, String.format("%s@slack%s", id, this.channel));
	}

	@Override public void whois(String nick, WhoisHandler handler) {
		final SlackUser user = this.server.findUserByUserName(nick);
		handler.setUsername(user.getUserName());
		handler.setHostname("slack");
		handler.setGecos(user.getRealName());
		handler.setServer("slack");
		handler.setAccount(user.getUserMail());

		// Synchronous communication -- what an idea
		handler.notifyDone(true);
	}

	@Override public void sendMessage(String target, String message) {
		if(target.charAt(0) == '#') {
			this.server.sendMessage(this.server.findChannelByName(target.substring(1)), message, null);
		} else {
			this.server.sendMessageToUser(target, message, null);
		}
	}

	@Override public void sendAction(String target, String message) {
		this.sendMessage(target, "/me " + message);
	}

	@Override public void sendNotice(String target, String message) {
		//TODO
		this.sendMessage(target, message);
	}
}
