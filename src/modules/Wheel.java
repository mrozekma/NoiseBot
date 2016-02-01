package modules;

import java.io.Serializable;
import java.util.*;

import main.JSONObject;
import main.Message;
import main.NoiseModule;
import org.json.JSONException;

import static main.Utilities.getRandom;
import static main.Utilities.sleep;

/**
 * Wheel
 *
 * @author Michael Mrozek
 *         Created Jun 18, 2009.
 */
public class Wheel extends NoiseModule implements Serializable {
	private static final String[] wheels = new String[] {
			"justice", "misfortune", "fire", "blame", "doom", "science", "morality", "fortune", "wheels", "time",
			"futility", "utility", "arbitrary choice", "wood", "chrome"
			// THIS LIST MUST GROW!
	};

	private Map<String, Integer> victims = new HashMap<>();

	@Command(value = "\\.(?:wheel|spin)", allowPM = false)
	public void wheel(Message message) {
		message.respond("Spin, Spin, Spin! the wheel of %s", getRandom(wheels));
		sleep(2);

		final String[] nicks = this.bot.getNicks();
		String choice;
		do {
			choice = getRandom(nicks);
		} while(choice.equals(this.bot.getBotNick()));

		this.victims.put(choice, this.victims.getOrDefault(choice, 0) + 1);
		this.save();

		Slap.slap(this.bot, message, choice);
	}

	@Command("\\.wheelstats")
	public JSONObject wheelStats(Message message) throws JSONException {
		final JSONObject rtn = new JSONObject();
		for(Map.Entry<String, Integer> e : this.victims.entrySet()) {
			rtn.put(e.getKey(), e.getValue().intValue());
		}
		return rtn;
	}

	@View(method = "wheelStats")
	public void plainWheelStatsView(Message message, JSONObject data) throws JSONException {
		if(data.length() == 0) {
			message.respond("No victims yet");
			return;
		}

		class Entry implements Comparable<Entry> {
			final String victim;
			final int attacks;
			Entry(String victim, int attacks) {
				this.victim = victim;
				this.attacks = attacks;
			}
			@Override public int compareTo(Entry o) {
				// attacks comparison is backwards so largest will be first
				return this.attacks != o.attacks ? Integer.compare(o.attacks, this.attacks) : this.victim.compareTo(o.victim);
			}
		}

		int total = 0;
		final Set<Entry> sorted = new TreeSet<>();
		for(Iterator<String> iter = data.keys(); iter.hasNext();) {
			final String key = iter.next();
			final int attacks = data.getInt(key);
			sorted.add(new Entry(key, attacks));
			total += attacks;
		}

		final List<Object> args = new LinkedList<>();
		for(Entry e : sorted) {
			args.add(e.attacks * 100. / total);
			args.add(e.victim);
		}
		message.respond("#([, ] (%2.2f%%) %s)", (Object)args.toArray());
	}

	@Override public String getFriendlyName() {return "Wheel";}
	@Override public String getDescription() {return "Slaps a random user";}
	@Override public String[] getExamples() {
		return new String[] {
				".wheel -- Choose a random user and slap them",
				".wheelstats -- Display statistics"
		};
	}
}
