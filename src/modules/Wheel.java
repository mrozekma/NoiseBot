package modules;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jibble.pircbot.User;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;
import static main.Utilities.getRandom;
import static main.Utilities.sleep;

import static modules.Slap.slapUser;

/**
 * Wheel
 *
 * @author Michael Mrozek
 *         Created Jun 18, 2009.
 */
public class Wheel extends NoiseModule implements Serializable {
	private Map<String, Integer> victims = new HashMap<String, Integer>();

	@Command("\\.(?:wheel|spin)")
	public void wheel(Message message) {
		final String[] wheels = new String[] {
			"justice", "misfortune", "fire", "blame", "doom", "science", "morality", "fortune", "wheels", "time",
			"futility", "utility", "arbitrary choice", "wood", "chrome"
			 // THIS LIST MUST GROW!
		};

		this.bot.sendMessage("Spin, Spin, Spin! the wheel of " + getRandom(wheels));
		sleep(2);

		final String[] nicks = this.bot.getNicks();
		String choice;
		do {
			choice = getRandom(nicks);
		} while(choice.equals(this.bot.getBotNick()));

		this.victims.put(choice, (this.victims.containsKey(choice) ? this.victims.get(choice) : 0) + 1);
		this.save();

		this.bot.sendAction(slapUser(choice));
	}

	@Command("\\.wheelstats")
	public void wheelStats(Message message) {
		if(victims.isEmpty()) {
			this.bot.sendMessage("No victims yet");
			return;
		}

		// Reversed to order max -> min
		final Stream<String> nickStream = victims.keySet().stream().sorted((s1, s2) -> victims.get(s2).compareTo(victims.get(s1)));
		final int total = victims.values().stream().mapToInt(i -> i).sum();
		final String[] parts = nickStream.map(nick -> String.format("(%2.2f%%) %s", ((double)victims.get(nick)/(double)total*100.0), nick)).toArray(String[]::new);

		this.bot.sendMessageParts(", ", parts);
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
