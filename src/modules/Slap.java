package modules;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

/**
 * Slap
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Slap extends NoiseModule {
	@Command("\\.slap (.*)")
	public void slap(Message message, String target) {
		this.bot.sendAction("slaps " + target);
	}
	
	@Command("\\.slap")
	public void slapSelf(Message message) {
		this.slap(message, message.getSender());
	}

	@Override public String getFriendlyName() {return "Slap";}
	@Override public String getDescription() {return "Slaps the specified user";}
	@Override public String[] getExamples() {
		return new String[] {
				".slap -- Slap the sending user",
				".slap _nick_ -- Slap the specified nick"
		};
	}
	@Override public String getOwner() {return "Morasique";}
}
