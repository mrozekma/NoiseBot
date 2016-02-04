package modules;

import debugging.Log;

import main.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Core
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Core extends NoiseModule {
	private static final String MODULE_REGEX = "[a-zA-Z0-9_ -]+";

	@Override protected Map<String, Style> styles() {
		final Map<String, Style> rtn = new HashMap<String, Style>() {{
			put("hash", Style.BLUE.update("bold"));
			put("author", Style.BLUE.update("bold"));
			put("description", Style.RED.update("bold"));
		}};
		Help.addHelpStyles(this.bot.getProtocol(), rtn); // Needed for 'module'
		return rtn;
	}

	@Command(value = "\\.load(\\??) (" + MODULE_REGEX + ")", allowPM = false)
	public void loadModules(CommandContext ctx, String qm, String moduleNames) {
		final boolean showErrors = qm.isEmpty();
		for(String moduleName : moduleNames.split(" ")) {
			try {
				this.bot.loadModule(moduleName);
				try {
					this.bot.saveModules();
				} catch(ModuleSaveException e) {
					Log.e(e);
				}

				this.bot.sendNotice("Module %(#module)s loaded", moduleName);
			} catch(ModuleInitException e) {
				Log.e(e);
				if(showErrors) {
					this.bot.sendNotice("#error %s", e.getMessage());
				}
			}
		}
	}

	@Command(value = "\\.unload(\\??) (" + MODULE_REGEX + ")", allowPM = false)
	public void unloadModules(CommandContext ctx, String qm, String moduleNames) {
		final boolean showErrors = qm.isEmpty();
		for(String moduleName : moduleNames.split(" ")) {
			// Don't allow unloading Core
			if(moduleName.equals(this.getClass().getSimpleName())) {
				this.bot.sendNotice("#error .unload cannot unload the %(#module)s module", this.getFriendlyName());
				continue;
			}

			try {
				this.bot.unloadModule(moduleName, false);
				try {
					this.bot.saveModules();
				} catch(ModuleSaveException e) {
					Log.e(e);
				}

				this.bot.sendNotice("Module %(#module)s unloaded", moduleName);
			} catch(ModuleUnloadException e) {
				Log.e(e);
				if(showErrors) {
					this.bot.sendNotice("#error %s", e.getMessage());
				}
			}
		}
	}

	@Command(value = "\\.reload(\\??) (" + MODULE_REGEX + ")", allowPM = false)
	public void reloadModules(CommandContext ctx, String qm, String moduleNames) {
		final boolean showErrors = qm.isEmpty();
		for(String moduleName : moduleNames.split(" ")) {
			try {
				if(this.bot.getModules().containsKey(moduleName)) {
					this.bot.reloadModule(moduleName);
					this.bot.sendNotice("Module %(#module)s reloaded", moduleName);
				} else {
					this.bot.loadModule(moduleName);
					this.bot.sendNotice("Module %(#module)s loaded", moduleName);
				}
			} catch(ModuleInitException | ModuleUnloadException e) {
				Log.e(e);
				if(showErrors) {
					this.bot.sendNotice("#error %s", e.getMessage());
				}
			}
		}
	}

	@Command("\\.rev")
	public void rev(CommandContext ctx) {
		final Git.Revision rev = this.bot.revision;
		ctx.respond("Currently on revision %(#hash)s by %(#author)s -- %(#description)s", rev.getHash(), rev.getAuthor(), rev.getDescription());
		ctx.respond("%s", Git.revisionLink(rev));
	}

	@Command("\\.owner\\?")
	public void isOwner(CommandContext ctx) {
		this.triggerIfOwner(ctx, () -> ctx.respond("#success You own this NoiseBot"), true);
	}

	@Command("\\.sync")
	public void sync(CommandContext ctx) {
		try {
			Git.attemptUpdate();
			// attemptUpdate() will call NoiseBot.syncAll(), which handles outputting sync info to all channels
		} catch(Git.SyncException e) {
			// Only output the error to the channel/user that requested the sync
			ctx.respond("#error %s", e.getMessage());
		}
	}

	@Command("\\.rehash")
	public void rehash(CommandContext ctx) {
		this.triggerIfOwner(ctx, () -> {
			NoiseBot.broadcastNotice("Reloading configuration");
			NoiseBot.rehash();
		}, true);
	}

	@Override public String getDescription() {return "Core functionality";}
	@Override public String getFriendlyName() {return "Core";}
	@Override public String[] getExamples() {
		return new String[] {
				".load _module_ -- Loads the specified module",
				".unload _module_ -- Unloads the specified module",
				".reload _module_ -- Unloads and then loads the specified module",
				".sync -- Manually pull updates from upstream",
				".rev -- Show the current revision"
		};
	}
}
