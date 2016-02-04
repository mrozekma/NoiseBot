package modules;

import java.io.File;

import eliza.ElizaMain;

import main.*;
import org.json.JSONException;

/**
 * Eliza
 *
 * @author Michael Mrozek
 *         Created Jan 1, 2011.
 */
public class Eliza extends NoiseModule {
	private static final File SCRIPT_FILE = NoiseBot.getDataFile("eliza-script");

	private ElizaMain eliza;

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		this.eliza = new ElizaMain();
		this.eliza.readScript(true, SCRIPT_FILE.getAbsolutePath());
	}

	@Command("${bot.nick}: (.*)")
	public JSONObject eliza(CommandContext ctx, String userMessage) throws JSONException {
		return new JSONObject().put("message", userMessage).put("response", this.eliza.processInput(userMessage));
	}

	@View
	public void view(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("%s: %s", ctx.getMessageSender(), data.get("response"));
	}

	@Override public String getFriendlyName() {return "Eliza";}
	@Override public String getDescription() {return "Implementation of ELIZA";}
	@Override public String[] getExamples() {
		return new String[] {
				this.bot.getBotNick() + ": You are the best"
		};
	}
}
