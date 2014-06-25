package modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Vector;

import debugging.Log;
import main.Message;
import main.ModuleLoadException;
import main.NoiseBot;
import main.NoiseModule;

import static org.jibble.pircbot.Colors.*;

import static panacea.Panacea.*;

/**
 * Fortune
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Fortune extends NoiseModule {
	private static File FORTUNE_FILE = NoiseBot.getDataFile("fortunes");
	private static final String COLOR_ERROR = RED;

	private String[] fortunes;

	@Override public void init(NoiseBot bot, Map<String, String> config) throws ModuleLoadException {
		super.init(bot, config);
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

	@Command("\\.fortune")
	public void fortune(Message message) {
		this.bot.sendMessage(getRandom(this.fortunes));
	}

	@Command("\\.fortune (.*)")
	public void fortune(Message message, String keyword) {
		final String match = getRandomMatch(this.fortunes, ".*" + keyword + ".*");
		if(match != null) {
			this.bot.sendMessage(match);
		} else {
			this.bot.reply(message, COLOR_ERROR + "No matches");
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
