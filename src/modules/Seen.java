package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import main.Message;
import main.NoiseModule;

/**
 * Seen
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Seen extends NoiseModule implements Serializable {
	private static final String COLOR_HERE = GREEN;
	private static final String COLOR_LAST_SEEN = YELLOW;
	private static final String COLOR_NEVER_SEEN = RED;
	
	private Map<String, Date> seenDates = new HashMap<String, Date>();
	private Map<String, Date> talkDates = new HashMap<String, Date>();

	@Command("\\.seen (.+)")
	public void seen(Message message, String target) {
		target = target.replaceAll(" +$", "");
		this.bot.reply(message, this.bot.isOnline(target)
				? COLOR_HERE + target + " is here now" + NORMAL + (this.talkDates.containsKey(target) ? " -- last spoke " + this.talkDates.get(target) : "")
				: (this.seenDates.containsKey(target)
						? COLOR_LAST_SEEN + target + " was last seen " + this.seenDates.get(target)
						: COLOR_NEVER_SEEN + target + " hasn't been seen"));
	}
	
	@Command(".*")
	public void talked(Message message) {
		this.talkDates.put(message.getSender(), new Date());
		this.save();
	}
	
	/*
	@Override protected void joined(String nick) {
		this.seenDates.put(nick, new Date());
		this.save();
	}
	*/

	@Override protected void left(String nick) {
		this.seenDates.put(nick, new Date());
		this.save();
	}

	@Override public String getFriendlyName() {return "Seen";}
	@Override public String getDescription() {return "Reports the last time a nick was in-channel";}
	@Override public String[] getExamples() {
		return new String[] {
				".seen _nick_ -- Returns the last time the specified nick was in-channel"
		};
	}
}
