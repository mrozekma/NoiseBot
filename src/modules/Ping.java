package modules;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.*;

/**
 * Ping
 *
 * @author Michael Mrozek
 *         Created Jun 13, 2009.
 */
public class Ping extends NoiseModule {
	@Command(NoiseBot.NICK + "!")
	public void direct(Message message) {
		this.bot.sendMessage(message.getSender() + "!");
	}
	
	@Command("(?:[hH]i|[hH]ello|[hH]ey) " + NoiseBot.NICK)
	public void indirect(Message message) {
		this.bot.sendMessage(getRandom(new String[] {"Hi", "Hello", "Hey"}) + " " + message.getSender());
	}

	@Override public String getFriendlyName() {return "Ping";}
	@Override public String getDescription() {return "Greets the user";}
	@Override public String[] getExamples() {
		return new String[] {
				"Hi " + NoiseBot.NICK,
				NoiseBot.NICK + "!"
		};
	}
	@Override public String getOwner() {return "Morasique";}
}
