package modules;

import main.Message;
import main.NoiseModule;

/**
 * Fascist
 *
 * @author Michael Mrozek
 *         Created Jun 17, 2009.
 */
public class Fascist extends NoiseModule {
	@Command(".*fascist.*")
	public void fascist(Message message) {
		this.bot.sendMessage("You fucking fascist!");
	}
	
	@Override public String getFriendlyName() {return "Fascist";}
	@Override public String getDescription() {return "Says \"You fucking fascist!\" when somebody says the word fascist";}
	@Override public String[] getExamples() {
		return new String[] {
				"He's a fascist"
		};
	}
}
