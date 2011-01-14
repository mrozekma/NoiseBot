package modules;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.Vector;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.*;

/**
 * Spook
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Spook extends NoiseModule {
	private static File SPOOK_FILE = new File("spook.lines");
	
	private String[] lines;
	
	@Override public void init(NoiseBot bot) {
		super.init(bot);
		try {
			final Vector<String> linesVec = new Vector<String>();
			final Scanner s = new Scanner(SPOOK_FILE);
			while(s.hasNextLine()) {
				linesVec.add(substring(s.nextLine(), 0, -1));
			}
			this.lines = linesVec.toArray(new String[0]);
		} catch(FileNotFoundException e) {
			this.bot.sendNotice("No spook lines file found");
		}
	}

	@Command("\\.spook ([0-9]+)")
	public void spook(Message message, int num) {
		num = range(num, 1, 20);
		if(num <= this.lines.length) {
			final Set<String> choices = new LinkedHashSet<String>();
			while(choices.size() < num) {
				choices.add(getRandom(this.lines));
			}
			this.bot.sendMessage(implode(choices.toArray(new String[0]), " "));
		} else {
			this.bot.sendMessage("There are only " + this.lines.length + " entries in the spook lines file");
		}
	}
	
	@Command("\\.spook")
	public void spookDefault(Message message) {this.spook(message, 1);}
	
	@Override public String getFriendlyName() {return "Spook";}
	@Override public String getDescription() {return "Displays a random line from the Emacs spook file";}
	@Override public String[] getExamples() {
		return new String[] {
				".spook"
		};
	}
	@Override public File[] getDependentFiles() {return new File[] {SPOOK_FILE};}
}
