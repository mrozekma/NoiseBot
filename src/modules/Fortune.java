package modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

import debugging.Log;
import main.*;
import org.json.JSONException;

import static main.Utilities.getRandom;
import static main.Utilities.getRandomMatch;

/**
 * Fortune
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Fortune extends NoiseModule {
	private static File FORTUNE_FILE = NoiseBot.getDataFile("fortunes");

	private String[] fortunes;

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		try {
			final Vector<String> fortunesVec = new Vector<String>();
			final BufferedReader r = new BufferedReader(new FileReader(FORTUNE_FILE));
			String line;
			while((line = r.readLine()) != null) {
				fortunesVec.add(line);
			}
			this.fortunes = fortunesVec.toArray(new String[0]);
			Log.i("Loaded fortune file: %d lines", this.fortunes.length);
		} catch(FileNotFoundException e) {
			this.bot.sendNotice("No fortune file found");
		} catch(IOException e) {
			this.bot.sendNotice("Problem reading fortune file: " + e.getMessage());
		}
	}

	@Command("\\.fortune|:crystal_ball:")
	public JSONObject fortune(CommandContext ctx) throws JSONException {
		return new JSONObject().put("fortune", getRandom(this.fortunes));
	}

	@Command("(?:\\.fortune|:crystal_ball:) (.*)")
	public JSONObject fortune(CommandContext ctx, String keyword) throws JSONException {
		final JSONObject rtn = new JSONObject().put("keyword", keyword);
		final String match = getRandomMatch(this.fortunes, ".*" + keyword + ".*");
		if(match != null) {
			rtn.put("fortune", match);
		}
		return rtn;
	}

	@View
	public void view(ViewContext ctx, JSONObject data) throws JSONException {
		if(data.has("fortune")) {
			ctx.respond("%s", data.get("fortune"));
		} else {
			ctx.respond("#error No matches");
		}
	}

	@Override public String getFriendlyName() {return "Fortune";}
	@Override public String getDescription() {return "Displays a random fortune from the Plan 9 fortune file";}
	@Override public String[] getExamples() {
		return new String[] {
				".fortune"
		};
	}
}
