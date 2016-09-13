package modules;

import main.CommandContext;
import main.NoiseModule;

/**
 * Hug
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Hug extends NoiseModule {
	@Command(value = "(?:\\.hug|:hugging_face:) (.*)", allowPM = false)
	public void hug(CommandContext ctx, String target) {
		this.bot.sendAction("hugs " + target);
	}
	
	@Command(value = "\\.hug|:hugging_face:", allowPM = false)
	public void hugSelf(CommandContext ctx) {
		this.hug(ctx, ctx.getMessageSender());
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
