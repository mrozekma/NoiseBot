package modules;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;
import static main.Utilities.getRandom;

/**
 * Ping
 *
 * @author Michael Mrozek
 *         Created Jun 13, 2009.
 */
public class Ping extends NoiseModule {
	@Command(value = "${bot.nick}!")
	public void direct(Message message) {
		this.bot.sendMessage(message.getSender() + "!");
	}

	@Command(value = "(?:hi|hello|hey) ${bot.nick}", caseSensitive = false)
	public void indirect(Message message) {
		this.bot.sendMessage(getRandom(new String[] {"Hi", "Hello", "Hey"}) + " " + message.getSender());
	}

	@Override public String getFriendlyName() {return "Ping";}
	@Override public String getDescription() {return "Greets the user";}
	@Override public String[] getExamples() {
		return new String[] {
				"Hi " + this.bot.getBotNick(),
				this.bot.getBotNick() + "!"
		};
	}
}
