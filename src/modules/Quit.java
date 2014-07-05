package modules;

import static org.jibble.pircbot.Colors.*;

import main.Message;
import main.ModuleSaveException;
import main.NoiseBot;
import main.NoiseModule;

/**
 * Quit
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Quit extends NoiseModule {
	private static final String COLOR_SUCCESS = GREEN;
	private static final String COLOR_ERROR = RED;

	@Command("\\.quit")
	public void quit(Message message) {
		this.triggerIfOwner(message, new Runnable() {
			@Override public void run() {
				Quit.this.bot.quit(0);
			}
		}, true);
	}

	@Command("\\.quit!")
	public void quitAll(Message message) {
		this.triggerIfOwner(message, new Runnable() {
			@Override public void run() {
				while(!NoiseBot.bots.isEmpty()) {
					NoiseBot.bots.values().iterator().next().quit(0);
				}
			}
		}, true);
	}

	@Command("\\.restart")
	public void restart(Message message) {
		this.triggerIfOwner(message, new Runnable() {
			@Override public void run() {
				while(!NoiseBot.bots.isEmpty()) {
					final NoiseBot bot = NoiseBot.bots.values().iterator().next();
					bot.sendNotice("Restarting...");
					bot.quit(2);
				}
			}
		}, true);
	}

	@Command("\\.save")
	public void save(Message message) {
		try {
			this.bot.saveModules();
			this.bot.sendMessage(COLOR_SUCCESS + "Saved");
		} catch(ModuleSaveException e) {
			this.bot.sendMessage(COLOR_ERROR + "Problem saving: " + e.getMessage());
		}
	}

	@Override public String getFriendlyName() {return "Quit";}
	@Override public String getDescription() {return "Makes the bot leave IRC";}
	@Override public String[] getExamples() {return null;}
	@Override public boolean showInHelp() {return false;}
}
