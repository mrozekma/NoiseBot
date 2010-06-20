package modules;

import static org.jibble.pircbot.Colors.*;

import main.Message;
import main.ModuleLoadException;
import main.ModuleUnloadException;
import main.NoiseModule;

/**
 * ModuleManager
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class ModuleManager extends NoiseModule {
	private static final String MODULE_REGEX = "[a-zA-Z0-9_ -]+";
	
	private static final String COLOR_ERROR = RED + REVERSE;
	
	@Command("\\.load (" + MODULE_REGEX + ")")
	public void loadModules(Message message, String moduleNames) {
		for(String moduleName : moduleNames.split(" ")) {
			try {
				this.bot.loadModule(moduleName);
				this.bot.sendNotice("Module " + Help.COLOR_MODULE + moduleName + NORMAL + " loaded");
			} catch(ModuleLoadException e) {
				this.bot.sendNotice(COLOR_ERROR + e.getMessage());
			}
		}
	}

	@Command("\\.unload (" + MODULE_REGEX + ")")
	public void unloadModules(Message message, String moduleNames) {
		for(String moduleName : moduleNames.split(" ")) {
			try {
				this.bot.unloadModule(moduleName);
				this.bot.sendNotice("Module " + Help.COLOR_MODULE + moduleName + NORMAL + " unloaded");
			} catch(ModuleUnloadException e) {
				this.bot.sendNotice(COLOR_ERROR + e.getMessage());
			}
		}
	}

	@Command("\\.reload (" + MODULE_REGEX + ")")
	public void reloadModules(Message message, String moduleNames) {
		this.unloadModules(message, moduleNames);
		this.loadModules(message, moduleNames);
	}

	@Override public String getDescription() {return "Manages modules";}
	@Override public String getFriendlyName() {return "Module Manager";}
	@Override public String[] getExamples() {
		return new String[] {
				".load _module_ -- Loads the specified module",
				".unload _module_ -- Unloads the specified module",
				".reload _module_ -- Unloads and then loads the specified module"
		};
	}
	@Override public String getOwner() {return "Morasique";}
}
