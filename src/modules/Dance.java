package modules;

import main.Message;
import main.NoiseModule;

/**
 * Dance!
 *
 * @author Andy Spencer (and bash.org)
 *         Created Jun 23, 2011.
 */
public class Dance extends NoiseModule {
	@Command(".*\\b[Dd][Aa][Nn][Cc][Ee].*")
	public void dance(Message message) {
		this.bot.sendMessage("/me dances :D-<");
		this.bot.sendMessage("/me dances :D|-<");
		this.bot.sendMessage("/me dances :D/-<");
	}
	
	@Override public String getFriendlyName() {return "Dance";}
	@Override public String getDescription() {return "Dances when the mood is right";}
	@Override public String[] getExamples() {
		return new String[] {
			"<Zybl0re> get up",
			"<Zybl0re> get on up",
			"<Zybl0re> get up",
			"<Zybl0re> get on up",
			"<phxl|paper> and DANCE"
		};
	}
}
