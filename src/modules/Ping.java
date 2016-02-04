package modules;

import main.CommandContext;
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
	public void direct(CommandContext ctx) {
		ctx.respond("%s!", ctx.getMessageSender());
	}

	@Command(value = "(?:hi|hello|hey) ${bot.nick}", caseSensitive = false)
	public void indirect(CommandContext ctx) {
		ctx.respond("%s %s", getRandom(new String[] {"Hi", "Hello", "Hey"}), ctx.getMessageSender());
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
