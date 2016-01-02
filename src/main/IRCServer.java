package main;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;
import org.jibble.pircbot.Colors;

import debugging.Log;

import static main.Utilities.sleep;

/**
 * IRCServer
 *
 * @author Michael Mrozek
 *         Created Jun 29, 2014.
 */
public class IRCServer extends PircBot {
	private static abstract class ModuleCall {
		public abstract void call(NoiseBot bot, NoiseModule module);
		public void onException(NoiseBot bot, Exception e) {
			Log.e(e);
		}
	}

	private final Connection connection;
	// channel -> bot for that channel
	private final Map<String, NoiseBot> bots = new HashMap<>();
	private String selfWhoisString = null;

	IRCServer(Connection connection) {
		this.connection = connection;

		try {
			this.setEncoding("UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.err.println("Unable to set encoding: " + e.getMessage());
		}

		this.setName(this.connection.nick);
		this.setLogin(this.connection.nick);
	}

	public String getWhoisString() {
		return this.selfWhoisString;
	}

	public void setWhoisString(String whoisString) {
		this.selfWhoisString = whoisString;
	}

	void addBot(String channel, NoiseBot bot) {
		this.bots.put(channel, bot);
	}

	Connection getConnection() {
		return this.connection;
	}

	public boolean connect() {
		Log.i("Connecting to %s:%d as %s", this.connection.server, this.connection.port, this.connection.nick);
		if(this.isConnected()) {
			Log.w("Already connected");
			return true;
		}

		try {
			System.out.printf("Connecting to %s:%d as %s\n", this.connection.server, this.connection.port, this.connection.nick);
			this.connect(this.connection.server, this.connection.port, this.connection.password);
			for(String channel : this.bots.keySet()) {
				System.out.printf("Joining %s\n", channel);
				this.joinChannel(channel);
			}
			return true;
		} catch(NickAlreadyInUseException e) {
			System.err.printf("The nick %s is already in use\n", this.connection.nick);
		} catch(IrcException e) {
			Log.e(e);
			System.err.printf("Unexpected IRC error: %s\n", e.getMessage());
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

	@Override protected void onMessage(final String channel, final String sender, final String login, final String hostname, final String message) {
		Log.in(String.format("<%s (%s@%s) -> %s: %s", sender, login, hostname, channel, message));
		this.moduleDispatch(channel, new ModuleCall() {
			@Override public void call(NoiseBot bot, NoiseModule module) {
				module.processMessage(new Message(Colors.removeFormattingAndColors(message.trim()), sender, false));
            }

			@Override public void onException(NoiseBot bot, Exception e) {
				super.onException(bot, e);
				bot.sendNotice(e.getMessage());
			}
		});
	}

	@Override protected void onPrivateMessage(final String sender, final String login, final String hostname, final String message) {
		Log.in(String.format("<%s (%s@%s) -> (direct): %s", sender, login, hostname, message));

		String channel = null;
		final String realMessage;
		if(message.startsWith("#")) { // Channel specified manually
			if(message.indexOf(' ') < 0) {
				return;
			}
			channel = message.substring(0, message.indexOf(' '));
			realMessage = message.substring(channel.length() + 1);
		} else { // Determine channel from membership
			for(Map.Entry<String, NoiseBot> entry : this.bots.entrySet()) {
				if(Arrays.asList(entry.getValue().getNicks()).contains(sender)) {
					if(channel == null) {
						channel = entry.getKey();
					} else {
						// We can use any instance to reply
						entry.getValue().sendMessage(sender, "You are in multiple channels served by this bot. You must prefix PMs with #channel to specify which channel you mean");
						return;
					}
				}
			}
			if(channel == null) {
				// We can use any instance to reply
				this.bots.values().iterator().next().sendMessage(sender, "You aren't in any channels served by this bot. You must prefix PMs with #channel to specify which channel you mean");
				return;
			}
			realMessage = message;
		}

		this.moduleDispatch(channel, new ModuleCall() {
			@Override public void call(NoiseBot bot, NoiseModule module) {
				module.processMessage(new Message(Colors.removeFormattingAndColors(realMessage.trim()), sender, true));
            }

			@Override public void onException(NoiseBot bot, Exception e) {
				super.onException(bot, e);
				bot.sendNotice(sender, e.getMessage());
			}
		});
	}

	@Override protected void onJoin(final String channel, final String sender, final String login, final String hostname) {
		if(!this.bots.containsKey(channel)) {
			return;
		}
		final NoiseBot bot = this.bots.get(channel);

		if(sender.equals(bot.getBotNick())) { // Done joining channel
			Log.v("Done joining channel: %s", channel);
			if(this.connection.password != null) { // Bot is identified; try getting voice in this channel (might fail)
				this.sendMessage("ChanServ", "VOICE " + channel);
			}
			bot.onChannelJoin();
		} else {
			Log.v("Joined %s: %s (%s2%s)", channel, sender, login, hostname);
			this.moduleDispatch(channel, new ModuleCall() {
				@Override public void call(NoiseBot bot, NoiseModule module) {
					module.onJoin(sender, login, hostname);
				}
			});
		}
	}

	@Override protected void onPart(final String channel, final String sender, final String login, final String hostname) {
		Log.v("Parted %s: %s (%s@%s)", channel, sender, login, hostname);
		this.moduleDispatch(channel, new ModuleCall() {
			@Override public void call(NoiseBot bot, NoiseModule module) {
				module.onPart(sender, login, hostname);
			}
		});
	}

	@Override protected void onUserList(final String channel, final User[] users) {
		this.moduleDispatch(channel, new ModuleCall() {
			@Override public void call(NoiseBot bot, NoiseModule module) {
				module.onUserList(users);
			}
		});
	}

	@Override protected void onKick(final String channel, final String kickerNick, final String kickerLogin, final String kickerHostname, final String recipientNick, final String reason) {
		Log.v("Kick %s: %s (%s@%s) -> %s: %s", channel, kickerNick, kickerLogin, kickerHostname, recipientNick, reason);
		this.moduleDispatch(channel, new ModuleCall() {
			@Override public void call(NoiseBot bot, NoiseModule module) {
				module.onKick(kickerNick, kickerLogin, kickerHostname, recipientNick, reason);
			}
		});
	}

	@Override protected void onTopic(final String channel, final String topic, final String setBy, final long date, final boolean changed) {
		Log.v("Topic %s: %s: %s", channel, setBy, topic);
		this.moduleDispatch(channel, new ModuleCall() {
			@Override public void call(NoiseBot bot, NoiseModule module) {
				module.onTopic(topic, setBy, date, changed);
			}
		});
	}

	@Override protected void onNickChange(final String oldNick, final String login, final String hostname, final String newNick) {
		Log.v("Nick change: %s -> %s (%s@%s)", oldNick, newNick, login, hostname);
		for(NoiseBot bot : this.bots.values()) {
			if(Arrays.asList(bot.getNicks()).contains(newNick)) {
				for(NoiseModule module : bot.getModules().values()) {
					module.onNickChange(oldNick, login, hostname, newNick);
				}
			}
		}
	}

	@Override protected void onOp(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient) {
		if(!this.bots.containsKey(channel)) {
			return;
		}
		final NoiseBot bot = this.bots.get(channel);
		if(recipient.equalsIgnoreCase(bot.getBotNick())) {
			Log.v("Bot opped -- requesting deop");
			this.sendMessage("ChanServ", "DEOP " + channel);
		}
	}

	// This isn't called if we tell PircBot to disconnect, only if the server disconnects us
	@Override protected void onDisconnect() {
		Log.i("Disconnected");

		while(!this.connect()) {
			sleep(30);
		}
    }

	@Override protected void onServerResponse(int code, String response) {
		switch(code) {
			case WhoisHandler.RPL_WHOISUSER:
			case WhoisHandler.RPL_WHOISSERVER:
			case WhoisHandler.RPL_WHOISACCOUNT:
			case WhoisHandler.RPL_ENDOFWHOIS:
				WhoisHandler.onServerResponse(code, response);
				break;
		}
	}
}
