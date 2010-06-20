package modules;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.jibble.pircbot.Colors.*;

import main.Message;
import main.NoiseModule;
import static panacea.Panacea.*;

/**
 * Links
 *
 * @author Michael Mrozek
 *         Created Oct 10, 2009.
 */
public class Links extends NoiseModule {
	private static class CachedMessage implements Serializable {
		private Date date;
		private Message message;
		
		public CachedMessage(Message message) {
			this.date = new Date();
			this.message = message;
		}
		
		@Override public String toString() {return this.date + " " + this.message;}
	}

	private static final String CHOICE_COLOR = RED;

	private Map<String, CachedMessage> links = new HashMap<String, CachedMessage>();

	@Command(".*((?:ftp|http|https):\\/\\/(?:\\w+:{0,1}\\w*@)?(?:\\S+)(?::[0-9]+)?(?:\\/|\\/(?:[\\w#!:.?+=&%@!\\-\\/]))?).*")
	public void url(Message message, String url) {
		if(links.containsKey(url)) {
			this.bot.reply(message, CHOICE_COLOR + "Duplicate URL: " + links.get(url));
		} else {
			links.put(url, new CachedMessage(message));
			this.save();
		}
	}

	@Override public String getFriendlyName() {return "Links";}
	@Override public String getDescription() {return "Keeps track of mentioned links and announces when a link has been duplicated";}
	@Override public String[] getExamples() {
		return new String[] {
				"http://www.google.com/",
				"http://www.google.com/ -- Announces that the link has been said in the past"
		};
	}
	@Override public String getOwner() {return "Morasique";}
}
