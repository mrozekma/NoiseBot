package modules;

import static org.jibble.pircbot.Colors.*;

import main.Message;
import main.NoiseModule;
import static panacea.Panacea.*;

/**
 * Cloud
 *
 * @author Michael Mrozek
 *         Created Aug 16, 2010.
 */
public class Cloud extends NoiseModule {
	@Command("\\.kill (.*)")
	public void kill(Message message, String target) {
		this.bot.sendMessage("==|--------  " + target);
	}
	
	@Override public String getFriendlyName() {return "Cloud";}
	@Override public String getDescription() {return "Kills stuff with Cloud's sword";}
	@Override public String[] getExamples() {
		return new String[] {
				".kill tommost",
		};
	}
	@Override public String getOwner() {return "Morasique";}
}
