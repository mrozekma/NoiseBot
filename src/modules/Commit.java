package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.Vector;

import debugging.Log;

import main.Message;
import main.ModuleInitException;
import main.NoiseBot;
import main.NoiseModule;
import static main.Utilities.getRandom;

import org.jibble.pircbot.User;

/**
 * Commit
 *
 * @author Michael Mrozek
 *         Created May 7, 2013.
 */
public class Commit extends NoiseModule {
	// https://raw.github.com/ngerakines/commitment/master/commit_messages.txt
	private static File MESSAGES_FILE = NoiseBot.getDataFile("commit_messages.txt");

	private static final String COLOR_QUOTE = CYAN;
	private static final String COLOR_ERROR = RED;

	private String[] messages;

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		this.messages = new String[0];
		try {
			final Vector<String> messages = new Vector<String>();
			final Scanner s = new Scanner(MESSAGES_FILE);
			while(s.hasNextLine()) {
				messages.add(s.nextLine());
			}
			this.messages = messages.toArray(new String[0]);
			Log.i("Loaded messages file: %d", this.messages.length);
		} catch(FileNotFoundException e) {
			this.bot.sendNotice("No commit messages file found");
		}
	}

	@Command("\\.commit")
	public void commit(Message message) {
		if(this.messages.length > 0) {
			final User user = getRandom(this.bot.getUsers());
			this.bot.sendMessage(getRandom(this.messages).replace("XNAMEX", user.getNick()).replace("XUPPERNAMEX", user.getNick().toUpperCase()));
		}
	}

	@Override public String getFriendlyName() {return "Commit";}
	@Override public String getDescription() {return "Outputs random commit messages from http://github.com/ngerakines/commitment";}
	@Override public String[] getExamples() {
		return new String[] {
				".commit"
		};
	}
}
