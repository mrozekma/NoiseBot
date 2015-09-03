package modules;

import java.util.HashMap;
import java.util.Map;

import main.Message;
import main.ModuleInitException;
import main.NoiseBot;
import main.NoiseModule;
import static main.Utilities.formatSeconds;
import static main.Utilities.round;

/**
 * Rate
 *
 * @author Michael Mrozek
 *         Created Jun 21, 2009.
 */
public class Rate extends NoiseModule {
	private long start;
	private Map<String, Integer> counter = new HashMap<String, Integer>();

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		this.start = System.currentTimeMillis();
	}

	@Override public void processMessage(Message message) {
		super.processMessage(message);

		final String nick = message.getSender();
		this.counter.put(nick, (this.counter.containsKey(nick) ? this.counter.get(nick) : 0) + 1);
	}

	private void displayRate(final String who, final int numMessages)
	{
		final long secondsElapsed = (System.currentTimeMillis() - this.start) / 1000;
		final double messagesPerMinute = ((double)numMessages / (double)secondsElapsed) * 60;
		this.bot.sendMessage(who + " sent " + numMessages + " messages in " + formatSeconds(secondsElapsed) + " = " + round(messagesPerMinute, 2) + " messages per minute");
	}

	@Command("\\.rate")
	public void general(Message message) {
		final int numMessages = this.counter.values().stream().mapToInt(i -> i).sum();
		displayRate("All users", numMessages);
	}

	@Command("\\.rate (.+)")
	public void specific(Message message, String nick) {
		if(nick.equals(this.bot.getBotNick())) {
			this.bot.reply(message, "I do not record my own messages");
			return;
		}

		final int numMessages = this.counter.containsKey(nick) ? this.counter.get(nick) : 0;
		displayRate(nick, numMessages);
	}

	@Override public String getFriendlyName() {return "Rate";}
	@Override public String getDescription() {return "Measures how often people speak";}
	@Override public String[] getExamples() {
		return new String[] {
				".rate -- Display how often anyone speaks in the channel",
				".rate _nick_ -- Display how often _nick_ speaks in the channel"
		};
	}
}
