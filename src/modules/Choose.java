package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import debugging.Log;

import au.com.bytecode.opencsv.CSVParser;

import main.JSONObject;
import main.Message;
import main.NoiseModule;
import main.Style;
import org.json.JSONException;

import static main.Utilities.getRandom;

/**
 * Choose
 *
 * @author Michael Mrozek
 *         Created Jun 17, 2009.
 */
public class Choose extends NoiseModule {
	private final CSVParser parser = new CSVParser(' ');
	private String lastOpts = null;

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("choice", Style.BLUE);
		}};
	}

	@Command("\\.(?:choose|choice) (.*)")
	public JSONObject choose(Message message, String optsLine) throws JSONException {
		this.lastOpts = optsLine;
		String[] opts;
		try {
			opts = this.parser.parseLine(optsLine);
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Exception attempting to parse choose options");
		}
		opts = Arrays.stream(opts).map(s -> s.trim()).toArray(String[]::new);
		final String choice = getRandom(opts);
		return new JSONObject().put("options", opts).put("choice", choice);
	}

	@Command("\\.rechoose")
	public JSONObject rechoose(Message message) throws JSONException {
		if(this.lastOpts == null) {
			message.respond("Perhaps you should have me make a choice first");
			return null;
		} else {
			return this.choose(message, this.lastOpts);
		}
	}

	@View
	public void view(Message message, JSONObject data) throws JSONException {
		final boolean realChoice = data.getStringArray("options").length > 1;
		message.respond("%s#choice %s", realChoice ? "" : "You're having me choose from a set of one...fine, ", data.getString("choice"));
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
