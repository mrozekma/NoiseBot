package modules;

import static org.jibble.pircbot.Colors.*;

import org.jibble.pircbot.User;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;
import static panacea.Panacea.*;

/**
 * Cloud
 *
 * @author Michael Mrozek
 *         Created Aug 16, 2010.
 */
public class Cloud extends NoiseModule {
	private static String[] swords = {
		"==\u00ef\u00bb\u00bf|\u00ef\u00bb\u00bf--------",
		"--\u00ef\u00bb\u00bf|\u00ef\u00bb\u00bf========-",
		"==\u00ef\u00bb\u00bf|\u00ef\u00bb\u00bf_________/",
		"(\u00ef\u00bb\u00bf)\u00ef\u00bb\u00bf===\u00ef\u00bb\u00bf{::::::::::::::::::::>"
	};

	@Command("\\.(?:kill|stab) (.*)")
	public void kill(Message message, String target) {
		this.bot.sendMessage(getRandom(swords) + "  " + target);
	}

	@Command("\\.(?:kill|stab)")
	public void killRandom(Message message) {
		final User[] users = this.bot.getUsers();
		String choice;
		do {
			choice = getRandom(users).getNick();
		} while(choice.equals(this.bot.getBotNick()));

		kill(message, choice);
	}

	@Override public String getFriendlyName() {return "Cloud";}
	@Override public String getDescription() {return "Kills stuff with Cloud's sword";}
	@Override public String[] getExamples() {
		return new String[] {
				".kill _tommost_ -- Kill _tommost_",
				".stab _tommost_ -- Same as above",
				".kill -- Kill a random user",
				".stab -- Same as above"
		};
	}
}
