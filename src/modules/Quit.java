package modules;

import main.CommandContext;
import main.ModuleSaveException;
import main.NoiseBot;
import main.NoiseModule;

/**
 * Quit
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Quit extends NoiseModule {
	@Command("\\.quit")
	public void quit(CommandContext ctx) {
		this.triggerIfOwner(ctx, () -> Quit.this.bot.quit(0), true);
	}

	@Command("\\.quit!")
	public void quitAll(CommandContext ctx) {
		this.triggerIfOwner(ctx, () -> {
			while(!NoiseBot.bots.isEmpty()) {
				NoiseBot.bots.values().iterator().next().quit(0);
			}
		}, true);
	}

	@Command("\\.restart")
	public void restart(CommandContext ctx) {
		this.triggerIfOwner(ctx, () -> {
			while(!NoiseBot.bots.isEmpty()) {
				final NoiseBot bot = NoiseBot.bots.values().iterator().next();
				bot.sendNotice("Restarting...");
				bot.quit(2);
			}
		}, true);
	}

	@Command("\\.save")
	public void save(CommandContext ctx) {
		try {
			this.bot.saveModules();
			ctx.respond("#success Saved");
		} catch(ModuleSaveException e) {
			ctx.respond("#error Problem saving: %s", e.getMessage());
		}
	}

	@Override public String getFriendlyName() {return "Quit";}
	@Override public String getDescription() {return "Makes the bot leave IRC";}
	@Override public String[] getExamples() {return null;}
	@Override public boolean showInHelp() {return false;}
}
