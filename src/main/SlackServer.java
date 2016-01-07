package main;

import com.ullink.slack.simpleslackapi.*;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import com.ullink.slack.simpleslackapi.replies.SlackMessageReply;
import debugging.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Michael Mrozek
 *         Created Dec 31, 2015.
 */
public class SlackServer implements SlackMessagePostedListener {
	private static abstract class ModuleCall {
		public abstract void call(NoiseBot bot, NoiseModule module);
		public void onException(NoiseBot bot, Exception e) {
			Log.e(e);
		}
	}

	private final String token;
	private final SlackSession slack;
	// channel -> bot for that channel
	private final Map<String, NoiseBot> bots = new HashMap<>();

	SlackServer(String token) {
		this.token = token;
		this.slack = SlackSessionFactory.createWebSocketSlackSession(token);
		this.slack.addMessagePostedListener(this);
	}

	String getToken() {
		return this.token;
	}

	void addBot(String channel, NoiseBot bot) {
		this.bots.put(channel, bot);
	}

	public boolean connect() {
		Log.i("Connecting to slack");
		if(this.slack.isConnected()) {
			Log.w("Already connected");
			return true;
		}

		try {
			System.out.println("Connecting to slack");
			this.slack.connect();
			for(Map.Entry<String, NoiseBot> e : this.bots.entrySet()) {
				System.out.printf("Joining %s\n", e.getKey());
				this.slack.joinChannel(e.getKey());
				e.getValue().onChannelJoin();
			}
			return true;
		} catch(IOException e) {
			Log.e(e);
			System.err.printf("Network error: %s\n", e.getMessage());
		}

		return false;
	}

	private void moduleDispatch(String channel, ModuleCall call) {
		if(!this.bots.containsKey(channel)) {
			Log.w("Ignore dispatch to unknown channel %s", channel);
		}

		final NoiseBot bot = this.bots.get(channel);
		for(NoiseModule module : bot.getModules().values()) {
			try {
				call.call(bot, module);
			} catch(Exception e) {
				call.onException(bot, e);
			}
		}
	}

	@Override public void onEvent(SlackMessagePosted pack, SlackSession session) {
		final String sender = pack.getSender().getUserName();
		final String channel = "#" + pack.getChannel().getName();
		final String message = pack.getMessageContent();
		Log.in(String.format("<%s -> %s: %s", sender, channel, message));
		this.moduleDispatch(channel, new ModuleCall() {
			@Override public void call(NoiseBot bot, NoiseModule module) {
				module.processMessage(new Message(bot, message, sender, false));
			}

			@Override public void onException(NoiseBot bot, Exception e) {
				super.onException(bot, e);
				bot.sendMessage("coreerror %s", e.getMessage());
			}
		});
	}

	// I'd rather have SlackServer extend SlackSession, but it comes from a factory and SlackWebSocketSessionImpl isn't visible
	// Instead we shallowly wrap every needed method and pass through to this.slack

	public SlackChannel findChannelByName(String channelName) {return this.slack.findChannelByName(channelName);}
	public SlackPersona sessionPersona() {return this.slack.sessionPersona();}
	public SlackUser findUserByUserName(String userName) {return this.slack.findUserByUserName(userName);}
	public SlackMessageHandle<SlackMessageReply> sendMessage(SlackChannel channel, String message, SlackAttachment attachment) {return this.slack.sendMessage(channel, message, attachment);}
	public SlackMessageHandle<SlackMessageReply> sendMessageToUser(String userName, String message, SlackAttachment attachment) {return this.slack.sendMessageToUser(userName, message, attachment);}
}
