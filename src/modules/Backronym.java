package modules;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Vector;

import au.com.bytecode.opencsv.CSVParser;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import static org.jibble.pircbot.Colors.*;

import static panacea.Panacea.*;

/**
 * Backronym
 *
 * @author Michael Mrozek
 *         Created Sep 11, 2010.
 */
public class Backronym extends NoiseModule {
	private static final File DICTIONARY_FILE = new File("/usr/share/dict/words.part");
	private static final String COLOR_ERROR = RED;
	private static final String COLOR_RESPONSE = GREEN;

	private final CSVParser parser = new CSVParser(' ');
	
	private HashMap<Character, String[]> words;
	private String[] dict;
	
	@Override public void init(NoiseBot bot) {
		super.init(bot);

		final HashMap<Character, Vector<String>> words = new HashMap<Character, Vector<String>>(26);
		final Vector<String> dict = new Vector<String>();
		for(int i = 'a'; i <= 'z'; i++) {
			words.put((char)i, new Vector<String>());
		}
		
		try {
			final Scanner s = new Scanner(DICTIONARY_FILE);
			while(s.hasNextLine()) {
				final String line = s.nextLine();
				final String word = line.substring(2);
				words.get(Character.toLowerCase(word.charAt(0))).add(word);
				dict.add(line);
			}
		} catch(FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException("No dictionary found");
		}
		
		this.words = new HashMap<Character, String[]>(words.size());
		for(Character key : words.keySet()) {
			this.words.put(key, words.get(key).toArray(new String[0]));
		}
		this.dict = dict.toArray(new String[0]);
	}

	@Command("\\.b(?:ackronym)? ([A-Za-z]+)")
	public void backronym(Message message, String letters) {
		if(letters.length() > 16) {
			this.bot.reply(message, COLOR_ERROR + "Maximum length: 16");
			return;
		}
	
		final String[] choices = new String[letters.length()];
		for(int i = 0; i < letters.length(); i++) {
			choices[i] = getRandom(this.words.get(Character.toLowerCase(letters.charAt(i))));
		}
		
		this.bot.sendMessage(implode(choices, " "));
	}
	
	@Command("\\.b(?:ackronym)? (.*[^A-Za-z].*)")
	public void backronymRegex(Message message, String line) {
		try {
			final String[] choices = this.parser.parseLine(line);
			if(choices.length > 16) {
				this.bot.reply(message, COLOR_ERROR + "Maximum length: 16");
				return;
			}

			for(int i = 0; i < choices.length; i++) {
				if (choices[i].length() == 2 && choices[i].charAt(1) == ':')
					choices[i] = choices[i] + ".*";
				else if (choices[i].length() < 2 || choices[i].charAt(1) != ':')
					choices[i] = ".:" + choices[i];

				String matches[] = getMatches(dict, choices[i]);
				if (matches.length > 0)
					choices[i] = getRandom(matches);
				choices[i] = choices[i].substring(2);
			}

			this.bot.sendMessage(implode(choices, " "));
		} catch (Exception e) {
			this.bot.reply(message, COLOR_ERROR + "What do you want me to do, interpret a pony?");
			e.printStackTrace();
		}
	}
	
	@Command("\\.b(?:ackronym)?")
	public void backronymDefault(Message message) {this.backronym(message, message.getSender());}
	
	@Override public String getFriendlyName() {return "Backronym";}
	@Override public String getDescription() {return "Chooses a random word for each letter specified";}
	@Override public String[] getExamples() {
		return new String[] {
				".backronym -- Display a backronym for the sending user",
				".backronym _css_ -- Display a backronym for _css_",
				".b -- Same as .backronym"
		};
	}
	@Override public File[] getDependentFiles() {return new File[] {DICTIONARY_FILE};}
}
