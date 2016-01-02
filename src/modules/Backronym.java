package modules;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

import debugging.Log;
import au.com.bytecode.opencsv.CSVParser;
import main.*;
import org.json.JSONException;

import static main.Utilities.getMatches;
import static main.Utilities.getRandom;

/**
 * Backronym
 *
 * @author Michael Mrozek
 *         Created Sep 11, 2010.
 */
public class Backronym extends NoiseModule {
	private static final int MAX_LENGTH = 16;
	private static final Color COLOR_ERROR = Color.RED;
	private static final Color COLOR_RESPONSE = Color.GREEN;

	private final CSVParser parser = new CSVParser(' ');

	@Configurable("dictionary-file")
	private File dictionaryFile = null;

	private HashMap<Character, String[]> words;
	private String[] dict;

	@Override public void setConfig(Map<String, Object> config) throws ModuleInitException {
		super.setConfig(config);
		this.words = null;
		this.dict = null;

		if(this.dictionaryFile == null) {
			return;
		}

		final HashMap<Character, Vector<String>> words = new HashMap<>(26);
		final Vector<String> dict = new Vector<>();
		for(int i = 'a'; i <= 'z'; i++) {
			words.put((char)i, new Vector<>());
		}

		try {
			final Scanner s = new Scanner(this.dictionaryFile);
			while(s.hasNextLine()) {
				final String line = s.nextLine();
				final String word = line.substring(2);
				if(word.contains(" ")) {
					continue;
				}
				words.get(Character.toLowerCase(word.charAt(0))).add(word);
				dict.add(line);
			}
		} catch(FileNotFoundException e) {
			Log.e(e);
			throw new ModuleInitException("No dictionary found");
		}

		this.words = new HashMap<>(words.size());
		for(Character key : words.keySet()) {
			this.words.put(key, words.get(key).toArray(new String[0]));
		}
		this.dict = dict.toArray(new String[0]);
	}

	@Command("\\.b(?:ackronym)? ([A-Za-z]+)")
	public JSONObject backronym(Message message, String letters) throws JSONException {
		if(this.words == null) {
			return new JSONObject().put("error", "No dictionary file loaded");
		}
		if(letters.length() > MAX_LENGTH) {
			return new JSONObject().put("error", "Maximum length: " + MAX_LENGTH);
		}

		final JSONObject rtn = new JSONObject();
		rtn.put("original", letters);
		for(int i = 0; i < letters.length(); i++) {
			rtn.append("choices", getRandom(this.words.get(Character.toLowerCase(letters.charAt(i)))));
		}
		return rtn;
	}


	@Command("\\.b(?:ackronym)? (.*[^A-Za-z].*)")
	public JSONObject backronymRegex(Message message, String line) throws JSONException {
		if(this.dict == null) {
			return new JSONObject().put("error", "No dictionary file loaded");
		}
		try {
			final String[] choices = this.parser.parseLine(line);
			if(choices.length > MAX_LENGTH) {
				return new JSONObject().put("error", "Maximum length: " + MAX_LENGTH);
			}

			final JSONObject rtn = new JSONObject();
			rtn.put("original", choices);

			for(int i = 0; i < choices.length; i++) {
				if (choices[i].length() == 2 && choices[i].charAt(1) == ':')
					choices[i] = choices[i] + ".*";
				else if (choices[i].length() < 2 || choices[i].charAt(1) != ':')
					choices[i] = ".:" + choices[i];

				String matches[] = getMatches(this.dict, choices[i]);
				if (matches.length > 0)
					choices[i] = getRandom(matches);
				choices[i] = choices[i].substring(2);
			}

			rtn.put("choices", choices);
			return rtn;
		} catch(Exception e) {
			Log.e(e);
			return new JSONObject().put("error", "What do you want me to do, interpret a pony?");
		}
	}

	@Command("\\.b(?:ackronym)?")
	public JSONObject backronymDefault(Message message) throws JSONException {return this.backronym(message, message.getSender());}

	@View(Protocol.IRC)
	public void viewIRC(JSONObject data) throws JSONException {
		IRCNoiseBot bot = (IRCNoiseBot)this.bot;
		if(data.has("error")) {
			bot.sendMessage(bot.getColor(COLOR_ERROR) + data.get("error"));
			return;
		}
		bot.sendMessage(bot.getColor(COLOR_RESPONSE) + Arrays.stream(data.getStringArray("choices")).collect(Collectors.joining(" ")));
	}

	@Override public String getFriendlyName() {return "Backronym";}
	@Override public String getDescription() {return "Chooses a random word for each letter specified";}
	@Override public String[] getExamples() {
		return new String[] {
				".backronym -- Display a backronym for the sending user",
				".backronym _css_ -- Display a backronym for _css_",
				".b -- Same as .backronym"
		};
	}
}
