package modules;

import org.jibble.pircbot.User;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.*;

/**
 * Wheel
 *
 * @author Michael Mrozek
 *         Created Jun 18, 2009.
 */
public class Wheel extends NoiseModule {
	@Command("\\.wheel")
	public void wheel(Message message) {
		this.bot.sendMessage("Spin, Spin, Spin! the wheel of justice");
		sleep(2);
		
		final User[] users = this.bot.getUsers();
		String choice;
		do {
			choice = getRandom(users).getNick();
		} while(choice.equals(this.bot.getNick()));
		
		this.bot.sendAction("slaps " + choice);
	}
	
	@Override public String getFriendlyName() {return "Wheel";}
	@Override public String getDescription() {return "Slaps a random user";}
	@Override public String[] getExamples() {
		return new String[] {
				".wheel -- Choose a random user and slap them"
		};
	}
	@Override public String getOwner() {return "Morasique";}
}
