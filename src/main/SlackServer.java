package main;

import com.ullink.slack.simpleslackapi.*;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;
import com.ullink.slack.simpleslackapi.replies.SlackChannelReply;
import com.ullink.slack.simpleslackapi.replies.SlackMessageReply;
import debugging.Log;

import java.io.IOException;
import java.text.ParseException;
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
				this.slack.joinChannel(e.getKey().substring(1));
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
		//TODO Direct messages
		final String sender = pack.getSender().getUserName();
		final String channel = "#" + pack.getChannel().getName();
		final String message = pack.getMessageContent();
		Log.in(String.format("<%s -> %s: %s", sender, channel, message));
		this.moduleDispatch(channel, new ModuleCall() {
			@Override public void call(NoiseBot bot, NoiseModule module) {
				if(!sender.equals(bot.getBotNick())) {
					module.processMessageAndDisplayResult(new Message(bot, ((SlackNoiseBot)bot).unescape(message), sender, false));
				}
			}

			@Override public void onException(NoiseBot bot, Exception e) {
				super.onException(bot, e);
				bot.sendMessage("#coreerror %s", e.getMessage());
			}
		});
	}

	// I'd rather have SlackServer extend SlackSession, but it comes from a factory and SlackWebSocketSessionImpl isn't visible
	// Instead we shallowly wrap every needed method and pass through to this.slack

	public SlackChannel findChannelByName(String channelName) {return this.slack.findChannelByName(channelName);}
	public SlackChannel findChannelById(String channelId) {return this.slack.findChannelById(channelId);}
	public SlackPersona sessionPersona() {return this.slack.sessionPersona();}
	public SlackUser findUserByUserName(String userName) {return this.slack.findUserByUserName(userName);}
	public SlackUser findUserById(String userId) {return this.slack.findUserById(userId);}
	public SlackMessageHandle<SlackChannelReply> sendMessage(SlackChannel channel, String message, SlackAttachment attachment) {return this.slack.sendMessage(channel, message, attachment);}
	public SlackMessageHandle<SlackChannelReply> sendMessage(SlackChannel channel, String message, SlackAttachment attachment, boolean unfurl) {return this.slack.sendMessage(channel, message, attachment, unfurl);}
	public SlackMessageHandle<SlackMessageReply> sendMessageToUser(String userName, String message, SlackAttachment attachment) {return this.slack.sendMessageToUser(userName, message, attachment);}
	public SlackMessageHandle<SlackChannelReply> deleteMessage(String timeStamp, SlackChannel channel) {return this.slack.deleteMessage(timeStamp, channel);}
	public SlackMessageHandle<SlackChannelReply> updateMessage(String timeStamp, SlackChannel channel, String message) {return this.slack.updateMessage(timeStamp, channel, message);}
	// These methods aren't part of simpleslackapi by default; I added them and built my own JAR:
	public SlackMessageHandle<SlackMessageReply> deleteMessageToUser(String timeStamp, String userName) {return this.slack.deleteMessageToUser(timeStamp, userName);}
	public SlackMessageHandle<SlackChannelReply> updateMessage(String timeStamp, SlackChannel channel, String message, SlackAttachment attachment) {return this.slack.updateMessage(timeStamp, channel, message, attachment);}
	public SlackMessageHandle<SlackMessageReply> updateMessageToUser(String timeStamp, String userName, String message) {return this.slack.updateMessageToUser(timeStamp, userName, message);}
	public SlackMessageHandle<SlackMessageReply> updateMessageToUser(String timeStamp, String userName, String message, SlackAttachment attachment) {return this.slack.updateMessageToUser(timeStamp, userName, message, attachment);}
}

