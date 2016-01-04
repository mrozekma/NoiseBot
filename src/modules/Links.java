package modules;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.jibble.pircbot.Colors.*;

import main.Message;
import main.NoiseModule;
import static main.Utilities.range;

/**
 * Links
 *
 * @author Michael Mrozek
 *         Created Oct 10, 2009.
 */
public class Links extends NoiseModule implements Serializable {
	private static class CachedMessage implements Serializable, Comparable<CachedMessage> {
		private Date date;
		private Message message;
		
		private CachedMessage() {}
		public CachedMessage(Message message) {
			this.date = new Date();
			this.message = message;
		}

		public boolean fromUser(final String user) { return user.equals(this.message.getSender()); }

		@Override public String toString() {return this.date + " " + this.message;}
		@Override public int compareTo(CachedMessage c) { return c.date.compareTo(this.date); }
	}

	private static final String DUP_COLOR = RED;
	private static final String RECAP_COLOR = YELLOW;

	private Map<String, CachedMessage> links = new HashMap<String, CachedMessage>();
	private String[] lastN = {null, null, null, null, null};

	@Command(".*((?:ftp|http|https):\\/\\/(?:\\w+:{0,1}\\w*@)?(?:\\S+)(?::[0-9]+)?(?:\\/|\\/(?:[\\w#!:.?+=&%@!\\-\\/]))?).*")
	public void url(Message message, String url) {
		if(links.containsKey(url)) {
			message.respond(DUP_COLOR + "Duplicate URL: " + links.get(url));
		} else {
			links.put(url, new CachedMessage(message));
			for(int i = lastN.length - 2; i >= 0; i--)
				lastN[i+1] = lastN[i];
			lastN[0] = url;
			this.save();
		}
	}

	@Command("\\.(?:lasturls|links) ([0-9]+)")
	public void lastURLs(Message message, int num) {
		num = range(num, 1, lastN.length);
		for(int i = 0; i < num; i++) {
			if(lastN[i] == null) continue;
			message.respond(RECAP_COLOR + this.links.get(lastN[i]));
		}
	}

	@Command("\\.(?:lasturls|links) (\\w+)")
	public void lastURLs(Message message, String user) {
		final int num = 5;
		links.values().stream()
			.filter(c -> c.fromUser(user)).sorted().limit(num)
			.forEach(c -> message.respond(RECAP_COLOR + c.toString()));
	}

	@Command("\\.(?:lasturls|links)")
	public void lastURLsDefault(Message message) {
		lastURLs(message, lastN.length);
	}

	@Override public String getFriendlyName() {return "Links";}
	@Override public String getDescription() {return "Keeps track of mentioned links and announces when a link has been duplicated";}
	@Override public String[] getExamples() {
		return new String[] {
				"http://www.google.com/",
				"http://www.google.com/ -- Announces that the link has been said in the past",
				".lasturls -- Shows the last " + this.lastN.length + " URLs sent to the channel",
				".lasturls _n_ -- Shows the last _n_ URLs sent to the channel",
				".lasturls _user_ -- Shows the last URLs sent to the channel by _user_",
				".links -- Same as .lasturls"
		};
	}
}
