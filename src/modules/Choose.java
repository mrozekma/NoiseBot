package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import debugging.Log;

import au.com.bytecode.opencsv.CSVParser;

import main.Message;
import main.NoiseModule;
import static main.Utilities.getRandom;

/**
 * Choose
 *
 * @author Michael Mrozek
 *         Created Jun 17, 2009.
 */
public class Choose extends NoiseModule {
	private static final String COLOR_ERROR = RED;
	private static final String COLOR_CHOICE = BLUE;

	private final CSVParser parser = new CSVParser(' ');
	private String lastOpts = null;

	@Command("\\.(?:choose|choice) (.*)")
	public void choose(Message message, String optsLine) {
		this.lastOpts = optsLine;
		final String[] opts;
		try {
			opts = this.parser.parseLine(optsLine);
		} catch(IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Exception attempting to parse choose options");
			Log.e(e);
			return;
		}

		final Set<String> options = Arrays.stream(opts).map(s -> s.trim()).collect(Collectors.toSet());
		if(options.size() > 1) {
			message.respond(COLOR_CHOICE + getRandom(options.toArray(new String[0])));
		} else {
			message.respond("You're having me choose from a set of one...fine, " + COLOR_CHOICE + options.iterator().next());
		}
	}

	@Command("\\.rechoose")
	public void rechoose(Message message) {
		if(this.lastOpts == null) {
			this.bot.sendMessage("Perhaps you should have me make a choice first");
		} else {
			this.choose(message, this.lastOpts);
		}
	}

	@Override public String getFriendlyName() {return "Choose";}
	@Override public String getDescription() {return "Returns a randomly selected entry from the specified list";}
	@Override public String[] getExamples() {
		return new String[] {
				".choose _opt1_ _opt2_... -- Returns a random entry from the comma-separated list. Double-quote any arguments if they have spaces",
				".choice _opt1_ _opt2_ ... -- Same as above",
				".rechoose -- Run the last .choose again"
		};
	}
}
