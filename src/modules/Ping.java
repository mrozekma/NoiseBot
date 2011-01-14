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
	@Command("(.*)!")
	public void direct(Message message, String nick) {
		if(!nick.equals(this.bot.getNick())) return;
		this.bot.sendMessage(message.getSender() + "!");
	}
	
	@Command("(?:[hH]i|[hH]ello|[hH]ey) (.*)")
	public void indirect(Message message, String nick) {
		if(!nick.equals(this.bot.getNick())) return;
		this.bot.sendMessage(getRandom(new String[] {"Hi", "Hello", "Hey"}) + " " + message.getSender());
	}

	@Override public String getFriendlyName() {return "Ping";}
	@Override public String getDescription() {return "Greets the user";}
	@Override public String[] getExamples() {
		return new String[] {
				"Hi " + this.bot.getNick(),
				this.bot.getNick() + "!"
		};
	}
}
