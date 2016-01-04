package main;

import com.google.gson.internal.StringMap;
import com.ullink.slack.simpleslackapi.SlackAttachment;
import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackPersona;
import com.ullink.slack.simpleslackapi.SlackUser;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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

	@Override public void sendMessage(MessageBuilder builder) {
		final SlackChannel target = (builder.target.charAt(0) == '#') ? this.server.findChannelByName(builder.target.substring(1)) : null;
		final Consumer<String> fn;
		switch(builder.type) {
		case MESSAGE:
		case NOTICE:
		default:
			fn = (target != null)
			   ? message -> this.server.sendMessage(target, message, null)
			   : message -> this.server.sendMessageToUser(builder.target, message, null);
			break;
		case ACTION:
			fn = (target != null)
			   ? message -> this.server.sendMessage(target, "/me " + message, null)
			   : message -> this.server.sendMessageToUser(builder.target, "/me " + message, null);
		}

		for(String message : builder.getFinalMessages(Optional.empty())) {
			fn.accept(message);
		}
	}

	public void sendAttachment(SlackAttachment attachment) {
		this.server.sendMessage(this.server.findChannelByName(this.channel.substring(1)), null, attachment);
	}

	public void sendAttachmentTo(String target, SlackAttachment attachment) {
		this.server.sendMessageToUser(target, null, attachment);
	}

	public void sendTitled(Color color, String title, String text) {
		final SlackAttachment attachment = new SlackAttachment();
		attachment.setColor(String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()));
		attachment.setTitle(title);
		attachment.setText(text);
		this.sendAttachment(attachment);
	}
}
