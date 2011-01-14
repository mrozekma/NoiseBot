package modules;

import main.Message;
import main.NoiseModule;

/**
 * Hug
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Hug extends NoiseModule {
	@Command("\\.hug (.*)")
	public void hug(Message message, String target) {
		this.bot.sendAction("hugs " + target);
	}
	
	@Command("\\.hug")
	public void hugSelf(Message message) {
		this.hug(message, message.getSender());
	}
	
	@Override public String getFriendlyName() {return "Hug";}
	@Override public String getDescription() {return "Hugs the specified user";}
	@Override public String[] getExamples() {
		return new String[] {
				".hug -- Hug the sending user",
				".hug _nick_ -- Hug the specified nick"
		};
	}
}
