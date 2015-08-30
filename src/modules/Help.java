package modules;

import static org.jibble.pircbot.Colors.*;

import java.util.Arrays;

import panacea.Condition;
import panacea.MapFunction;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.*;

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
		final String[] parts = map(filter(this.bot.getModules().values().toArray(new NoiseModule[0]), new Condition<NoiseModule>() {
			@Override public boolean satisfies(NoiseModule module) {return module.showInHelp();}
		}), new MapFunction<NoiseModule, String>() {
			@Override public String map(NoiseModule module) {return COLOR_MODULE + module.getFriendlyName() + NORMAL;}
		});
		Arrays.sort(parts);

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
