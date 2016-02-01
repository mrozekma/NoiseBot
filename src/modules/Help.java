package modules;

import static main.Utilities.strstr;
import static org.jibble.pircbot.Colors.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import main.*;
import org.json.JSONException;

/**
 * Help
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Help extends NoiseModule {
	// Other modules use these styles, so we make them statically accessible
	static void addHelpStyles(Protocol protocol, Map<String, Style> map) {
		switch(protocol) {
		case IRC:
			map.put("command", Style.BLUE);
			map.put("argument", Style.GREEN);
			map.put("module", Style.RED);
			break;
		case Slack:
			map.put("command", Style.BOLD);
			map.put("argument", Style.ITALIC);
			map.put("module", Style.BOLD);
			break;
		}
	}

	@Override protected Map<String, Style> styles() {
		final Map<String, Style> rtn = new HashMap<>();
		addHelpStyles(this.bot.getProtocol(), rtn);
		return rtn;
	}

	@Command(value = "\\.help", allowPM = true)
	public JSONObject general(Message message) throws JSONException {
		final String[] modules = this.bot.getModules().values().stream().filter(m -> m.showInHelp()).map(m -> m.getFriendlyName()).sorted().toArray(String[]::new);
		return new JSONObject().put("modules", modules);
	}

	@View(method = "general")
	public void plainGeneralView(Message message, JSONObject data) throws JSONException {
		message.respond("Use .#command help #module MODULE #plain to get examples for a specific module:");
		message.respond("List of modules: #([, ] #module %s)", (Object)data.getStringArray("modules"));
	}

	@Command(value = "\\.help (.+)", allowPM = true)
	public JSONObject specific(Message message, String moduleName) throws JSONException {
		for(NoiseModule module : this.bot.getModules().values()) {
			if(!module.showInHelp()) {continue;}
			if(moduleName.equalsIgnoreCase(module.getFriendlyName())) {
				return new JSONObject()
						.put("name", module.getFriendlyName())
						.put("description", module.getDescription())
						.put("examples", module.getExamples());
			}
		}

		return new JSONObject().put("error", "Unknown module: " + moduleName);
	}

	@View(method = "specific")
	public void plainSpecificView(Message message, JSONObject data) throws JSONException {
		this.specificView(message, data, false);
	}

	@View(value = Protocol.Slack, method = "specific")
	public void slackSpecificView(Message message, JSONObject data) throws JSONException {
		message.mergeResponses();
		this.specificView(message, data, true);
	}

	private void specificView(Message message, JSONObject data, boolean showBullets) throws JSONException {
		message.respond("#module %s #plain module -- %s", data.getString("name"), data.getString("description"));
		final String[] examples = data.getStringArray("examples");
		if(examples == null || examples.length == 0) {
			message.respond("No examples available");
		} else {
			message.respond("Examples:");
			for(String example : examples) {
				final MessageBuilder builder = message.buildResponse();
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
				".help _module_ -- Shows the description of _module_ and some usage examples"
		};
	}
}
