package modules;

import static org.jibble.pircbot.Colors.*;

import main.Message;
import main.NoiseModule;

/**
 * Dance!
 *
 * @author Andy Spencer (and bash.org)
 *         Created Jun 23, 2011.
 */
public class Dance extends NoiseModule {
	private static final String COLOR_FIRST_GUY = YELLOW;
	private static final String COLOR_SECOND_GUY = BLUE;
	private static final String COLOR_FIRST_LINE = RED;
	private static final String COLOR_SECOND_LINE = GREEN;
	
	@Command(".*\\b[Dd][Aa][Nn][Cc][Ee].*")
	public void dance(Message message) {
		this.bot.sendAction("dances :D-<");
		this.bot.sendAction("dances :D|-<");
		this.bot.sendAction("dances :D/-<");
	}
	
	@Command(".*\\b[Dd][Ii][Ss][Cc][Oo].*")
	public void disco(Message message) {
		this.bot.sendMessage(String.format("%s\\o   %sLET'S   %so/", COLOR_FIRST_GUY, COLOR_FIRST_LINE, COLOR_SECOND_GUY));
		this.bot.sendMessage(String.format(" %s|>  %sDISCO! %s<|", COLOR_FIRST_GUY, COLOR_SECOND_LINE, COLOR_SECOND_GUY));
		this.bot.sendMessage(String.format("%s< \\         %s/ >", COLOR_FIRST_GUY, COLOR_SECOND_GUY));
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
