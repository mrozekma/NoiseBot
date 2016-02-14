package modules;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import debugging.Log;

import au.com.bytecode.opencsv.CSVParser;

import main.*;
import org.json.JSONException;

import static main.Utilities.getRandom;
import static main.Utilities.getRandomInt;

/**
 * Choose
 *
 * @author Michael Mrozek
 *         Created Jun 17, 2009.
 */
public class Choose extends NoiseModule {
	private static final int MAX_DICE = 30;
	private static final int MAX_FACES = 144; // https://commons.wikimedia.org/wiki/Dice_by_number_of_sides

	private final CSVParser parser = new CSVParser(' ');
	private String lastOpts = null;
	private JSONObject lastRoll = null;

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("choice", Style.BLUE);
		}};
	}

	@Command("\\.(?:choose|choice) (.*)")
	public JSONObject choose(CommandContext ctx, String optsLine) throws JSONException {
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
	public JSONObject rechoose(CommandContext ctx) throws JSONException {
		if(this.lastOpts == null) {
			ctx.respond("Perhaps you should have me make a choice first");
			return null;
		} else {
			return this.choose(ctx, this.lastOpts);
		}
	}

	@View(method = {"choose", "rechoose"})
	public void plainViewChoice(ViewContext ctx, JSONObject data) throws JSONException {
		final boolean realChoice = data.getStringArray("options").length > 1;
		ctx.respond("%s#choice %s", realChoice ? "" : "You're having me choose from a set of one...fine, ", data.getString("choice"));
	}

	@Command("\\.roll")
	public JSONObject rollD6(CommandContext ctx) throws JSONException {
		return this.roll(ctx, 1, 6, 0);
	}

	@Command("\\.roll ([0-9]+)?d([0-9]+)(?:\\s*\\+\\s*([0-9]+))?")
	public JSONObject roll(CommandContext ctx, String numDice, String numFaces, String additive) throws JSONException {
		try {
			return this.roll(ctx, (numDice == null) ? 1 : Integer.parseInt(numDice), Integer.parseInt(numFaces), (additive == null) ? 0 : Integer.parseInt(additive));
		} catch(NumberFormatException e) { // One of the arguments won't fit in an int
			return new JSONObject().put("error", "Roll is insane");
		}
	}

	private JSONObject roll(CommandContext ctx, int numDice, int numFaces, int additive) throws JSONException {
		if(numDice > MAX_DICE) {
			return new JSONObject().put("error", String.format("Can't roll more than %d dice", MAX_DICE));
		}
		if(numFaces == 0) {
			return new JSONObject().put("error", "Dice must have faces");
		}
		if(numFaces > MAX_FACES) {
			return new JSONObject().put("error", String.format("Dice can't have more than %d faces", MAX_FACES));
		}
		final JSONObject rtn = new JSONObject().put("num_dice", numDice).put("num_faces", numFaces).put("additive", additive).put("rolls", new Object[0]);
		int sum = additive;
		for(int i = 0; i < numDice; i++) {
			final int choice = getRandomInt(1, numFaces);
			rtn.append("rolls", choice);
			sum += choice;
		}
		rtn.put("total", sum);
		this.lastRoll = rtn;
		return rtn;
	}

	@Command("\\.reroll")
	public JSONObject reroll(CommandContext ctx) throws JSONException {
		if(this.lastRoll == null) {
			ctx.respond("Perhaps you should roll some dice first");
			return null;
		} else {
			return this.roll(ctx, this.lastRoll.getInt("num_dice"), this.lastRoll.getInt("num_faces"), this.lastRoll.getInt("additive"));
		}
	}

	@View(method = {"rollD6", "roll", "reroll"})
	public void plainViewRoll(ViewContext ctx, JSONObject data) throws JSONException {
		final MessageBuilder builder = ctx.buildResponse();
		builder.add("#([ ][%(#choice)d])", new Object[] {data.getJSONArray("rolls").stream().toArray()});
		if(data.getInt("additive") > 0) {
			builder.add(" + %(#choice)d", new Object[] {data.getInt("additive")});
		}
		if(data.getJSONArray("rolls").length() > 1 || data.getInt("additive") > 0) {
			builder.add(" = %(#choice)d", new Object[] {data.getInt("total")});
		}
		builder.send();
	}

	@Override public String getFriendlyName() {return "Choose";}
	@Override public String getDescription() {return "Returns a randomly selected entry from the specified list";}
	@Override public String[] getExamples() {
		return new String[] {
				".choose _opt1_ _opt2_... -- Returns a random entry from the space-separated list. Double-quote any arguments if they have spaces",
				".choice _opt1_ _opt2_ ... -- Same as above",
				".rechoose -- Run the last .choose again",
				".roll 3d4+5 -- Roll some dice",
				".reroll -- Run the last .roll again",
		};
	}
}
