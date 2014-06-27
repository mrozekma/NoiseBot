package main;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.jibble.pircbot.ReplyConstants;

/**
 * WhoisHandler
 *
 * @author Michael Mrozek
 *         Created Jun 27, 2014.
 */
public abstract class WhoisHandler {
	public static final int RPL_WHOISUSER = ReplyConstants.RPL_WHOISUSER;
	public static final int RPL_WHOISSERVER = ReplyConstants.RPL_WHOISSERVER;
	public static final int RPL_WHOISACCOUNT = 330;
	public static final int RPL_ENDOFWHOIS = ReplyConstants.RPL_ENDOFWHOIS;

	private static final int TIMEOUT = 5; // seconds
	private static Set<WhoisHandler> handlers = new HashSet<WhoisHandler>();

	protected String nick, username, hostname, gecos, server, account;
	private Timer timer;

	protected WhoisHandler() {
		handlers.add(this);
	}

	@Override protected void finalize() throws Throwable {
		handlers.remove(this);
    }

	public void setNick(String nick) {this.nick = nick;}
	public void setUsername(String username) {this.username = username;}
	public void setHostname(String hostname) {this.hostname = hostname;}
	public void setGecos(String gecos) {this.gecos = gecos;}
	public void setServer(String server) {this.server = server;}
	public void setAccount(String account) {this.account = account;}

	public abstract void onResponse();
	public void onTimeout() {}

	void startWaiting() {
		this.timer = new Timer();
		this.timer.schedule(new TimerTask() {
			@Override public void run() {
				WhoisHandler.this.timer = null;
				WhoisHandler.this.notifyDone(false);
            }
		}, TIMEOUT * 1000);
	}

	void notifyDone(boolean gotResponse) {
		handlers.remove(this);
		if(this.timer != null) {
			this.timer.cancel();
			this.timer = null;
		}
		if(gotResponse) {
			this.onResponse();
		} else {
			this.onTimeout();
		}
	}

	static void onServerResponse(int code, String response) {
		final List<String> parts = new Vector<String>();
		while(response.contains(" ")) {
			if(response.charAt(0) == ':') {
				parts.add(response.substring(1));
				response = "";
			} else {
				final int pos = response.indexOf(' ');
				parts.add(response.substring(0, pos));
				response = response.length() > pos ? response.substring(pos + 1) : "";
			}
		}
		if(response.startsWith(":")) {
			response = response.substring(1);
		}
		if(!response.isEmpty()) {
			parts.add(response);
		}

		for(WhoisHandler handler : handlers.toArray(new WhoisHandler[0])) {
			final String nick = handler.nick;
			if(parts.size() < 2 || !parts.get(1).equals(nick)) {
				continue;
			}

			switch(code) {
				case RPL_WHOISUSER:
					if(parts.size() == 6) {
						handler.setUsername(parts.get(2));
						handler.setHostname(parts.get(3));
						handler.setGecos(parts.get(5));
					}
					break;
				case RPL_WHOISSERVER:
					if(parts.size() == 4) {
						handler.setServer(parts.get(2));
					}
					break;
				case RPL_WHOISACCOUNT:
					if(parts.size() == 4 && parts.get(3).equals("is logged in as")) {
						handler.setAccount(parts.get(2));
					}
					break;
				case RPL_ENDOFWHOIS:
					handler.notifyDone(true);
					break;
			}
		}
	}
}
