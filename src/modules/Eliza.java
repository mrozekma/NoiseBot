package modules;

import java.io.File;
import java.util.Map;

import eliza.ElizaMain;

import main.Message;
import main.ModuleInitException;
import main.NoiseBot;
import main.NoiseModule;

/**
 * Eliza
 *
 * @author Michael Mrozek
 *         Created Jan 1, 2011.
 */
public class Eliza extends NoiseModule {
	private static final File SCRIPT_FILE = NoiseBot.getDataFile("eliza-script");

	private ElizaMain eliza;

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		this.eliza = new ElizaMain();
		this.eliza.readScript(true, SCRIPT_FILE.getAbsolutePath());
	}

	@Command("${bot.nick}: (.*)")
	public void eliza(Message message, String userMessage) {
		message.respond(this.eliza.processInput(userMessage));
	}

	@Override public String getFriendlyName() {return "Eliza";}
	@Override public String getDescription() {return "Implementation of ELIZA";}
	@Override public String[] getExamples() {
		return new String[] {
				this.bot.getBotNick() + ": You are the best"
		};
	}
}
