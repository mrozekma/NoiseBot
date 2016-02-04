package modules;

import main.CommandContext;
import main.JSONObject;
import main.Message;
import main.NoiseModule;
import main.Protocol;
import main.ViewContext;
import org.jibble.pircbot.Colors;
import org.json.JSONException;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Michael Mrozek
 *         Created Jan 9, 2016.
 */
public class BotUtils extends NoiseModule {
	private String clean(String str) {
		//TODO Possibly there should be a virtual NoiseBot method for this sort of thing; not sure
		if(this.bot.getProtocol() == Protocol.IRC) {
			str = Colors.removeFormattingAndColors(str);
		}
		return str;
	}

	@Command("\\.which (.+)")
	public JSONObject which(CommandContext ctx, String command) throws JSONException {
		final Message subMessage = ctx.deriveMessage(this.clean(command));
		final String[] modules = this.bot.getModules().entrySet().stream().filter(e -> e.getValue().matches(subMessage)).map(e -> e.getKey()).toArray(String[]::new);
		return new JSONObject().put("modules", modules);
	}

	@View(method = "which")
	public void whichView(ViewContext ctx, JSONObject data) throws JSONException {
		final String[] modules = data.getStringArray("modules");
		if(modules.length == 0) {
			ctx.respond("No modules handle the specified command");
		} else {
			ctx.respond("#(%s)", (Object)modules);
		}
	}

	@Command("`([^`]+)`")
	public JSONObject rawEcho(CommandContext ctx, String command) throws InvocationTargetException, JSONException {
		final String[] modules = this.which(ctx, command).getStringArray("modules");
		switch(modules.length) {
		case 0:
			return new JSONObject().put("error", "No modules handle the specified command");
		case 1: {
			final Message subMessage = ctx.deriveMessage(this.clean(command));
			// Throws InvocationTargetException
			final MessageResult result = this.bot.getModules().get(modules[0]).processMessage(subMessage);
			return result.data.orElse(new JSONObject().put("error", "Command does not return data")); }
		default:
			return new JSONObject().put("error", "Multiple modules handle the specified command");
		}
	}

	@Override public String getFriendlyName() {return "BotUtils";}
	@Override public String getDescription() {return "I'm Mr. Meeseeks, look at me!";}
	@Override public String[] getExamples() {
		return new String[] {
			".which `.b test`",
			"`.b test`",
		};
	}
}
