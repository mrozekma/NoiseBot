package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;
import java.util.Vector;

import debugging.Log;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.*;

/**
 * Commit
 *
 * @author Michael Mrozek
 *         Created May 7, 2013.
 */
public class Commit extends NoiseModule {
	// https://raw.github.com/ngerakines/commitment/master/commit_messages.txt
	private static File MESSAGES_FILE = new File("commit_messages.txt");

	private static final String COLOR_QUOTE = CYAN;
	private static final String COLOR_ERROR = RED;

	private String[] messages;

	@Override public void init(NoiseBot bot) {
		super.init(bot);
		this.messages = new String[0];
		try {
			final Vector<String> messages = new Vector<String>();
			final Scanner s = new Scanner(MESSAGES_FILE);
			while(s.hasNextLine()) {
				messages.add(s.nextLine());
			}
			this.messages = messages.toArray(new String[0]);
			Log.i("Loaded messages file: " + this.messages.length);
		} catch(FileNotFoundException e) {
			this.bot.sendNotice("No commit messages file found");
		}
	}

	@Command("\\.commit")
	public void commit(Message message) {
		if(this.messages.length > 0) {
			this.bot.sendMessage(getRandom(this.messages));
		}
	}

	@Override public String getFriendlyName() {return "Commit";}
	@Override public String getDescription() {return "Outputs random commit messages if you can't think of one to use";}
	@Override public String[] getExamples() {
		return new String[] {
				".commit"
		};
	}
	@Override public File[] getDependentFiles() {return new File[] {MESSAGES_FILE};}
}
