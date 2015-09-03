package modules;

import static org.jibble.pircbot.Colors.*;

import java.util.Arrays;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

/**
 * Help
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Help extends NoiseModule {
	public static final String COLOR_MODULE = RED;
	public static final String COLOR_COMMAND = BLUE;
	public static final String COLOR_ARGUMENT = GREEN;

	@Command("\\.help")
	@PM("\\.help")
	public void general(Message message) {
		this.bot.respond(message, "Use ." + COLOR_COMMAND + "help" + NORMAL + " " + COLOR_MODULE + "MODULE" + NORMAL + " to get examples for a specific module:");
		final String[] parts = this.bot.getModules().values().stream().filter(m -> m.showInHelp()).map(m -> COLOR_MODULE + m.getFriendlyName() + NORMAL).sorted().toArray(String[]::new);
		// This is hacktastic
		parts[0] = "List of modules: " + parts[0];
		this.bot.respondParts(message, ", ", parts);
	}

	@Command("\\.help (.+)")
	@PM("\\.help (.+)")
	public void specific(Message message, String moduleName) {
		for(NoiseModule module : this.bot.getModules().values()) {
			if(!module.showInHelp()) {continue;}
			if(moduleName.equalsIgnoreCase(module.getFriendlyName())) {
				this.bot.respond(message, COLOR_MODULE + module.getFriendlyName() + NORMAL + " module -- " + module.getDescription());
				this.bot.respond(message, "Examples:");
				String[] examples = module.getExamples();
				if(examples == null || examples.length == 0) {
					this.bot.respond(message, "No examples available");
				} else {
					for(String example : examples) {
						example = example.replaceAll("^\\.([^ ]+) ", "." + COLOR_COMMAND + "$1" + NORMAL + " ");
						example = example.replaceAll(" \\|\\| \\.([^ ]+) ", " || ." + COLOR_COMMAND + "$1" + NORMAL + " ");
						example = example.replaceAll("_([^_]*)_", COLOR_ARGUMENT + "$1" + NORMAL);
						this.bot.respond(message, example);
					}
				}
				return;
			}
		}

		this.bot.respond(message, "Unknown module: " + moduleName);
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
