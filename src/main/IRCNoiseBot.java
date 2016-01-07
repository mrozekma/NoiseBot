package main;

import com.google.gson.internal.StringMap;
import debugging.Log;
import org.jibble.pircbot.Colors;
import org.jibble.pircbot.User;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.List;

import static main.Utilities.sleep;

/**
 * IRCNoiseBot
 *
 * @author Michael Mrozek
 *         Created Dec 31, 2015.
 */
public class IRCNoiseBot extends NoiseBot {
	@FunctionalInterface
	private interface MessageSender {
		public void send(String target, String message);
	}

	private final IRCServer server;
	private final Vector outQueue; // This is controlled by the "server"'s parent PircBot instance
	private String whoisString = "----------------------------------------------------------------------------------------------------";

	private static final Map<Color, String> COLORS = new HashMap<Color, String>() {{
		put(null, Colors.NORMAL);
		put(Color.BLACK, Colors.BLACK);
		put(Color.RED, Colors.RED);
		put(Color.YELLOW, Colors.YELLOW);
		put(Color.GREEN, Colors.GREEN);
		put(Color.BLUE, Colors.BLUE);
		put(Color.MAGENTA, Colors.MAGENTA);
		put(Color.CYAN, Colors.CYAN);
		put(Color.WHITE, Colors.WHITE);
	}};

	public IRCNoiseBot(IRCServer server, String channel, boolean quiet) {
		super(channel, quiet, server.getConnection().fixedModules);
		this.server = server;

		Vector outQueue = null;
		try {
			// I laugh at abstractions
			final Field outQueueField = this.server.getClass().getSuperclass().getDeclaredField("_outQueue");
			outQueueField.setAccessible(true);
			final org.jibble.pircbot.Queue _outQueue = (org.jibble.pircbot.Queue)outQueueField.get(this.server);

			// I laugh at them further
			// Also, implementing a queue using a vector is terrible
			final Field queueField = _outQueue.getClass().getDeclaredField("_queue");
			queueField.setAccessible(true);
			outQueue = (Vector)queueField.get(_outQueue);
		} catch(NoSuchFieldException | IllegalAccessException e) {
			Log.e(e);
		}
		this.outQueue = outQueue;
	}

	static void createBots(String connectionName, StringMap data) throws IOException {
		final String host = (String)data.get("server");
		final int port = (int)Double.parseDouble("" + data.get("port"));
		final String nick = (String)data.get("nick");
		final String pass = data.containsKey("password") ? (String)data.get("password") : null;
		final boolean quiet = data.containsKey("quiet") ? (Boolean)data.get("quiet") : false;
		final String[] modules = data.containsKey("modules") ? ((List<String>)data.get("modules")).toArray(new String[0]) : null;
		final IRCServer server = new IRCServer(new Connection(host, port, nick, pass, modules));

		final List<String> channels = (List<String>)data.get("channels");
		for(String channel : channels) {
			final NoiseBot bot = new IRCNoiseBot(server, channel, quiet);
			NoiseBot.bots.put(connectionName + channel, bot);
			server.addBot(channel, bot);
		}

		if(!server.connect()) {
			throw new IOException("Unable to connect to server");
		}
	}

	@Override public void onChannelJoin() {
		this.whois(this.getBotNick(), new WhoisHandler() {
			@Override public void onResponse() {
				IRCNoiseBot.this.whoisString = String.format("%s!%s@%s", this.nick, this.username, this.hostname);
			}

			// On timeout, just leave the whoisString at the old value
		});
		super.onChannelJoin();
	}

	// Note: 'exitCode' is only applicable if this is the last connection
	@Override public void quit(int exitCode) {
		// Wait (for a little while) for outgoing messages to be sent
		if(this.outQueue != null) {
			for(int tries = 0; tries < 5 && !this.outQueue.isEmpty(); tries++) {
				sleep(1);
			}
		}

		if(this.server.getChannels().length > 1) {
			Log.i("Parting " + this.channel);
			this.server.partChannel(this.channel);
		} else {
			Log.i("Disconnecting");
			this.server.disconnect();
		}

		super.quit(exitCode);
	}

	@Override public Protocol getProtocol() {
		return Protocol.IRC;
	}

	@Override public String getBotNick() {
		return this.server.getNick();
	}

	private User[] getUsers() {return this.server.getUsers(this.channel);}
	@Override public String[] getNicks() {
		return Arrays.stream(this.getUsers()).map(User::getNick).toArray(String[]::new);
	}

	@Override public boolean clearPendingSends() {
		if(this.outQueue == null) {
			return false;
		}

		synchronized(this.outQueue) {
			final Iterator iter = this.outQueue.iterator();
			while(iter.hasNext()) {
				final Object obj = iter.next();
				if(obj.toString().startsWith("PRIVMSG " + this.channel + " ")) {
					iter.remove();
				}
			}
		}
		return true;
	}

	@Override File getStoreDirectory() {
		final Connection conn = this.server.getConnection();
		return new File(STORE_DIRECTORY, String.format("%s@%s:%d%s", conn.nick, conn.server, conn.port, this.channel));
	}

	@Override public void whois(String nick, WhoisHandler handler) {
		handler.setNick(nick);
		handler.startWaiting();
		this.server.sendRawLine("WHOIS " + nick);
	}

	@Override public String format(Style style, String text) {
		String before = "";
		if(style.color != null) {
			before += COLORS.getOrDefault(style.color, Colors.NORMAL);
		}
		if(style.bold) {
			before += Colors.BOLD;
		}
		if(style.reverse) {
			before += Colors.REVERSE;
		}
		final String after = before.isEmpty() ? "" : Colors.NORMAL;
		return before + text + after;
	}

	@Override public void sendMessage(final MessageBuilder builder) {
		int maxLen = this.server.getMaxLineLength()
		           - this.whoisString.length()
		           - " PRIVMSG ".length()
		           - builder.target.length()
		           - " :\r\n".length();

		final MessageSender fn;
		switch(builder.type) {
		case MESSAGE:
		default:
			fn = this.server::sendMessage;
			break;
		case ACTION:
			fn = this.server::sendAction;
			break;
		case NOTICE:
			fn = this.server::sendNotice;
			break;
		}
		for(String message : builder.getFinalMessages(Optional.of(maxLen))) {
			fn.send(builder.target, message);
		}
	}
}
