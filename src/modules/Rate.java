package modules;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import main.*;
import org.json.JSONException;

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
	private Map<String, Integer> counter = new HashMap<>();

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		this.start = System.currentTimeMillis();
	}

	@Override public MessageResult processMessage(Message message) throws InvocationTargetException {
		final String nick = message.getSender();
		this.counter.put(nick, this.counter.getOrDefault(nick, 0) + 1);
		return super.processMessage(message);
	}

	private JSONObject findRate(int numMessages) throws JSONException {
		final long secondsElapsed = (System.currentTimeMillis() - this.start) / 1000;
		final double messagesPerMinute = ((double)numMessages / (double)secondsElapsed) * 60;
		return new JSONObject()
				.put("num_messages", numMessages)
				.put("seconds_elapsed", secondsElapsed)
				.put("num_messages_per_minute", messagesPerMinute);
	}

	@Command("\\.rate")
	public JSONObject general(Message message) throws JSONException {
		final int numMessages = this.counter.values().stream().mapToInt(i -> i).sum();
		return this.findRate(numMessages);
	}

	@Command("\\.rate (.+)")
	public JSONObject specific(Message message, String nick) throws JSONException {
		if(nick.equals(this.bot.getBotNick())) {
			return new JSONObject().put("who", nick).put("error", "I do not record my own messages");
		}

		final int numMessages = this.counter.getOrDefault(nick, 0);
		return this.findRate(numMessages).put("who", nick);
	}

	@View
	public void plainView(Message message, JSONObject data) throws JSONException {
		message.respond("%s sent %d %s in %s = %s messages per minute",
				data.optString("who", "All users"),
				data.getInt("num_messages"),
				(data.getInt("num_messages") == 1) ? "message" : "messages",
				formatSeconds(data.getLong("seconds_elapsed")),
				round(data.getDouble("num_messages_per_minute"), 2));
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
