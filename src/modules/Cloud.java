package modules;

import main.CommandContext;
import main.NoiseModule;
import static main.Utilities.getRandom;

/**
 * Cloud
 *
 * @author Michael Mrozek
 *         Created Aug 16, 2010.
 */
public class Cloud extends NoiseModule {
	private static String[] swords = {
		"==\ufeff|\ufeff--------",
		"--\ufeff|\ufeff========-",
		"==\ufeff|\ufeff_________/",
		"(\ufeff)\ufeff===\ufeff{::::::::::::::::::::>"
	};

	@Command("\\.(?:kill|stab) (.*)")
	public void kill(CommandContext ctx, String target) {
		ctx.respond("%s %s", getRandom(swords), target);
	}

	@Command("\\.(?:kill|stab)")
	public void killRandom(CommandContext ctx) {
		final String[] nicks = this.bot.getNicks();
		String choice;
		do {
			choice = getRandom(nicks);
		} while(choice.equals(this.bot.getBotNick()));

		this.kill(ctx, choice);
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
