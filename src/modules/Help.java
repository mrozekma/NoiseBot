package modules;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import main.*;
import org.json.JSONException;

/**
 * Help
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Help extends NoiseModule {
	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			Style.addHelpStyles(bot.getProtocol(), this);
		}};
	}

	@Command("\\.help|:question:")
	public JSONObject general(CommandContext ctx) throws JSONException {
		final String[] modules = this.bot.getModules().values().stream().filter(m -> m.showInHelp()).map(m -> m.getFriendlyName()).sorted().toArray(String[]::new);
		return new JSONObject().put("modules", modules);
	}

	@View(method = "general")
	public void plainGeneralView(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("Use .#command help #module MODULE #plain to get examples for a specific module:");
		ctx.respond("List of modules: #([, ] #module %s)", (Object)data.getStringArray("modules"));
	}

	@Command(value = "(?:\\.help|:question:) (.+)", allowPM = true)
	public JSONObject specific(CommandContext ctx, String search) throws JSONException, InvocationTargetException {
		// First, try module names
		for(NoiseModule module : this.bot.getModules().values()) {
			if(!module.showInHelp()) {continue;}
			if(search.equalsIgnoreCase(module.getFriendlyName())) {
				return new JSONObject()
						.put("name", module.getFriendlyName())
						.put("description", module.getDescription())
						.put("examples", module.getExamples());
			}
		}

		// Then try command pattern matching. This depends on BotUtils
		final NoiseModule botUtilsModule = this.bot.getModules().get("BotUtils");
		if(botUtilsModule != null) {
			final MessageResult result = botUtilsModule.processMessage(ctx.deriveMessage(".which " + search));
			final String[] modules = result.data.get().getStringArray("modules");
			if(modules.length > 0) {
				//TODO Show all modules if the command matches more than one?
				return this.specific(ctx, modules[0]);
			}
		}

		return new JSONObject().put("error", "Unknown module: " + search);
	}

	@View(method = "specific")
	public void plainSpecificView(ViewContext ctx, JSONObject data) throws JSONException {
		this.specificView(ctx, data, false);
	}

	@View(value = Protocol.Slack, method = "specific")
	public void slackSpecificView(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.getMessage().mergeResponses();
		this.specificView(ctx, data, true);
	}

	private void specificView(ViewContext ctx, JSONObject data, boolean showBullets) throws JSONException {
		ctx.respond("#module %s #plain module -- %s", data.getString("name"), data.getString("description"));
		final String[] examples = data.getStringArray("examples");
		if(examples == null || examples.length == 0) {
			ctx.respond("No examples available");
		} else {
			ctx.respond("Examples:");
			for(String example : examples) {
				final MessageBuilder builder = ctx.buildResponse();
				builder.add(showBullets ? MessageBuilder.BULLET + " " : "  ");
				for(String piece : example.split(" ")) {
					if(piece.length() > 1 && piece.startsWith(".")) {
						builder.add("#command %s ", new Object[] {piece});
					} else if(piece.length() > 2 && piece.startsWith("_") && piece.endsWith("_")) {
						builder.add("#argument %s ", new Object[] {piece.substring(1, piece.length() - 1)});
					} else {
						builder.add("%s ", new Object[] {piece});
					}
				}
				builder.send();
			}
		}
	}

	@Override public String getFriendlyName() {return "Help";}
	@Override public String getDescription() {return "Provides help for all public commands";}
	@Override public String[] getExamples() {
		return new String[] {
				".help -- Shows a list of all modules and their descriptions",
				".help _module_ -- Shows the description of _module_ and some usage examples",
				".help _command_ -- Shows the help for the module that handles _command_",
		};
	}
}
