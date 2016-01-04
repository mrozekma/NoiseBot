package main;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jibble.pircbot.Colors.*;

import debugging.Log;

import static main.Utilities.pluralize;
import static main.Utilities.reverse;

import modules.Help;

import com.google.gson.internal.StringMap;

/**
 * NoiseBot
 *
 * @author Michael Mrozek
 *         Created June 13, 2009.
 */
public abstract class NoiseBot {
	public static final String DEFAULT_CONNECTION = "default";
	private static final String CONFIG_FILENAME = "config";
	private static final String DATA_DIRECTORY = "data";
	public static final String STORE_DIRECTORY = "store";
	protected static final String COLOR_ERROR = RED + REVERSE;

	public static final Map<String, NoiseBot> bots = new HashMap<>();
	public static final Map<String, Set<File>> moduleFileDeps = new HashMap<>();
	public static Git.Revision revision = Git.head();
	private static Map config;

	protected final String channel;
	protected final boolean quiet;
	protected final String[] fixedModules;
	private Map<String, NoiseModule> modules = new HashMap<>();

	protected NoiseBot(String channel, boolean quiet, String[] fixedModules) {
		this.channel = channel;
		this.quiet = quiet;
		this.fixedModules = fixedModules;
	}

	// Note: 'exitCode' is only applicable if this is the last connection
	public void quit(int exitCode) {
		try {this.saveModules();} catch(ModuleSaveException e) {Log.e(e);}

		for(Map.Entry<String, NoiseBot> entry : NoiseBot.bots.entrySet()) {
			if(entry.getValue() == this) {
				NoiseBot.bots.remove(entry.getKey());
				break;
			}
		}
		if(NoiseBot.bots.isEmpty()) {
			Log.i("Quitting");
			System.exit(exitCode);
		}
	}

	public Map<String, NoiseModule> getModules() {return this.modules;}

	private void loadModules() {
		if(!this.modules.isEmpty()) {
			Log.w(pluralize(this.modules.size(), "module", "modules") + " already loaded");
			return;
		}

		// Always load the core module
		try {
			this.loadModule("Core");
		} catch(ModuleInitException e) {
			Log.e(e);
		}

		final String[] moduleNames;
		final File moduleFile = new File(this.getStoreDirectory(), "modules");
		if(this.fixedModules != null) {
			moduleNames = fixedModules;
		} else if(moduleFile.exists()) {
			try {
				moduleNames = Serializer.deserialize(moduleFile, String[].class);
			} catch(Exception e) {
				Log.e("Failed to load modules");
				Log.e(e);
				return;
			}
		} else {
			moduleNames = new String[0];
		}

		Log.i("Loading %d modules from store", moduleNames.length);

		for(String moduleName : moduleNames) {
			if(moduleName.equals("Core")) {continue;}
			try {
				this.loadModule(moduleName);
			} catch(ModuleInitException e) {
				Log.e("Failed loading module %s", moduleName);
				Log.e(e);
				this.sendMessage(COLOR_ERROR + String.format("Failed loading module %s: %s", moduleName, e.getMessage()));
			}
		}

		{
			final int moduleCount = this.modules.size();
			final int patternCount = this.modules.values().stream().mapToInt(module -> module.getPatterns().length).sum();

			Log.i("Done loading revision %s", this.revision.getHash());
			if(!this.quiet) {
				this.sendNotice(String.format("NoiseBot revision %s (loaded %s watching for %s)", this.revision.getHash(), pluralize(moduleCount, "module", "modules"), pluralize(patternCount, "pattern", "patterns")));
			}
		}
	}

	public void saveModules() throws ModuleSaveException {
		Log.i("Saving %d modules to store", this.modules.size());

		final String[] moduleNames = this.modules.keySet().toArray(new String[0]);

		try {
            Serializer.serialize(new File(this.getStoreDirectory(), "modules"), moduleNames);
		} catch(IOException e) {
			throw new ModuleSaveException("Failed saving module list");
		}

		final Vector<NoiseModule> failedSaves = new Vector<NoiseModule>();
		for(NoiseModule module : this.modules.values()) {
			if(!module.save()) {
				failedSaves.add(module);
				Log.e("Failed saving module %s", module.getClass().getSimpleName());
			}
		}

		if(!failedSaves.isEmpty()) {
			throw new ModuleSaveException("Unable to save some modules: " + failedSaves.stream().map(module -> module.getClass().getSimpleName()).collect(Collectors.joining(", ")));
		}
	}

	public void loadModule(String moduleName) throws ModuleInitException {
		Log.i("Loading module: %s", moduleName);
		if(this.modules.containsKey(moduleName)) {
			throw new ModuleInitException("Module " + moduleName + " already loaded");
		} else if(!moduleName.equals("Core") && this.fixedModules != null && !Arrays.asList(this.fixedModules).contains(moduleName)) {
			throw new ModuleInitException("This bot does not allow dynamic module loading");
		}

		NoiseModule module;
		try {
			// Reloading the class will refresh its file dependencies
			synchronized(moduleFileDeps) {
				moduleFileDeps.remove(moduleName);

				final Class<? extends NoiseModule> c = (Class<? extends NoiseModule>)getModuleLoader().loadClass("modules." + moduleName);

				// Try loading from disk
				module = NoiseModule.load(this, c);

				// Otherwise just make a new one
				if(module == null) {
					module = c.newInstance();
				}
			}

			if(module.getFriendlyName() == null) {
				throw new ModuleInitException("Module " + moduleName + " does not have a friendly name");
			}

			module.init(this);
			this.modules.put(moduleName, module);
		} catch(ClassCastException e) {
			Log.e(e);
			throw new ModuleInitException("Defined module " + moduleName + " does not extend NoiseModule");
		} catch(ClassNotFoundException e) {
			Log.e(e);
			throw new ModuleInitException("Unable to instantiate module " + moduleName + ": Module does not exist");
		} catch(Exception e) {
			Log.e(e);
			throw new ModuleInitException("Unable to instantiate module " + moduleName + ": " + e.getMessage());
		}

		this.setModuleConfig(module);
	}

	public void unloadModule(String moduleName, boolean reloading) throws ModuleUnloadException {
		Log.i("Unloading module: %s", moduleName);

		if(!reloading && this.fixedModules != null) {
			throw new ModuleUnloadException("This bot does not allow dynamic module unloading");
		}

		final Class c;
		try {
			c = getModuleLoader().loadClass("modules." + moduleName);
		} catch(Exception e) {
			throw new ModuleUnloadException("Unable to unload module " + moduleName + ": Module does not exist");
		}

		if(!this.modules.containsKey(moduleName)) {
			throw new ModuleUnloadException("Unable to unload module " + moduleName + ": Module not loaded");
		}

		final NoiseModule module = this.modules.get(moduleName);
		module.save();
		module.unload();
		this.modules.remove(moduleName);
		moduleFileDeps.remove(moduleName);

		// Immediately reload the core module
		if(!reloading && moduleName.equals("Core")) {
			try {
				this.loadModule(moduleName);
			} catch(ModuleInitException e) {
				throw new ModuleUnloadException(e);
			}
		}
	}

	public void reloadModule(String moduleName) throws ModuleInitException, ModuleUnloadException {
		this.unloadModule(moduleName, true);
		this.loadModule(moduleName);
	}

	private void setModuleConfig(NoiseModule module) throws ModuleInitException {
		final String moduleName = module.getClass().getSimpleName();
		final Map<String, Object> moduleConfig = new HashMap<String, Object>();
		if(NoiseBot.config.containsKey("modules")) {
			final StringMap data = (StringMap)NoiseBot.config.get("modules");
			if(data.containsKey(moduleName)) {
				final StringMap moduleData = (StringMap)data.get(moduleName);
				for(Object _entry : moduleData.entrySet()) {
					final Map.Entry<String, Object> entry = (Map.Entry<String, Object>)_entry;
					moduleConfig.put(entry.getKey(), entry.getValue());
				}
			}
		}

		module.setConfig(moduleConfig);
	}

	private static void loadConfig() {
		try {
			NoiseBot.config = Serializer.deserialize(CONFIG_FILENAME, StringMap.class);
		} catch(FileNotFoundException e) {
			NoiseBot.config = new StringMap();
		}
	}

	public static void rehash() {
		//TODO Possibly act on other changed config values, like connection details
		NoiseBot.loadConfig();

		for(NoiseBot bot : NoiseBot.bots.values()) {
			for(NoiseModule module : bot.modules.values()) {
				try {
					bot.setModuleConfig(module);
				} catch(ModuleInitException e) {
					bot.sendMessage(COLOR_ERROR + e.getMessage());
				}
			}
		}
	}

	public abstract Protocol getProtocol();

	public abstract String getBotNick();

	public abstract String[] getNicks();

	public boolean isOnline(String nick) {
		return Arrays.asList(this.getNicks()).contains(nick);
	}

	public abstract boolean clearPendingSends();

	abstract File getStoreDirectory();

	public abstract void whois(String nick, WhoisHandler handler);

	boolean isOwner(String nick, String hostname, String account) {
		if(!config.containsKey("owner")) {
			return false;
		}

		final StringMap owner = (StringMap)config.get("owner");
		if(owner.isEmpty()) {
			return false;
		}

		if(owner.containsKey("nick") && !owner.get("nick").equals(nick)) {
			return false;
		}
		if(owner.containsKey("hostname") && !owner.get("hostname").equals(hostname)) {
			return false;
		}
		if(owner.containsKey("account") && !owner.get("account").equals(account)) {
			return false;
		}

		return true;
	}

	public void sync(String from, String to, Git.Revision oldrev, Git.Revision[] revs, boolean coreChanged) {
		this.sendNotice("Synced " + pluralize(revs.length, "revision", "revisions") + ": " + Git.diffLink(oldrev, this.revision));
		for(Git.Revision rev : reverse(revs)) {
			this.sendNotice("    " + rev);
		}

		if(coreChanged) {
			this.sendNotice(YELLOW + "Core files changed; NoiseBot will restart" + NORMAL);
		} else {
			final String[] moduleNames = Git.affectedModules(this, from, to);
			for(String moduleName : moduleNames) {
				try {
					this.reloadModule(moduleName);
				} catch(ModuleInitException e) {
					throw new Git.SyncException("Unable to load module " + moduleName);
				} catch(ModuleUnloadException e) {
					throw new Git.SyncException("Unable to unload module " + moduleName);
				}
			}
			if(moduleNames.length != 0) {
				final Stream<String> coloredNamesStream = Arrays.stream(moduleNames).map(name -> Help.COLOR_MODULE + name + NORMAL);
				this.sendNotice("Reloaded modules: " + coloredNamesStream.collect(Collectors.joining(", ")));
			}
		}
	}

	public static void syncAll() {
		final String from = revision.getHash(), to = "HEAD";
		final Git.Revision[] revs = Git.diff(from, to);
		final Git.Revision oldrev = revision;

		boolean coreChanged = false;
		try {
			final String lib = new File("lib").getAbsolutePath();
			final String src = new File("src").getAbsolutePath();
			final String modules = new File("src", "modules").getAbsolutePath();
			for(File f : Git.getFiles(from, to)) {
				final String path = f.getAbsolutePath();
				if(path.startsWith(lib) || (path.startsWith(src) && !path.startsWith(modules))) {
					coreChanged = true;
					break;
				}
			}
		} catch(IOException e) {}

		revision = Git.head();
		for(NoiseBot bot : NoiseBot.bots.values()) {
			bot.sync(from, to, oldrev, revs, coreChanged);
		}

		if(coreChanged) {
			while(!NoiseBot.bots.isEmpty()) {
				final NoiseBot bot = NoiseBot.bots.values().iterator().next();
				bot.quit(2);
			}
		}
	}

	// By default, just ignore the style entirely. Child classes will probably override this
	public String format(Style style, String text) {
		return text;
	}

	public abstract void sendMessage(MessageBuilder builder);

	public MessageBuilder buildMessage() {
		return this.buildMessageTo(this.channel);
	}

	public MessageBuilder buildMessageTo(String target) {
		return new MessageBuilder(this, target, MessageBuilder.Type.MESSAGE);
	}

	public MessageBuilder buildAction() {
		return this.buildActionTo(this.channel);
	}

	public MessageBuilder buildActionTo(String target) {
		return new MessageBuilder(this, target, MessageBuilder.Type.ACTION);
	}

	public MessageBuilder buildNotice() {
		return new MessageBuilder(this, this.channel, MessageBuilder.Type.NOTICE);
	}

	public void sendMessage(String fmt, String... args) {
		this.buildMessage().add(fmt, args).send();
	}

	public void sendMessage(Style style, String fmt, String... args) {
		this.buildMessage().add(style, fmt, args).send();
	}

	public void sendMessageTo(String target, String fmt, String... args) {
		this.buildMessageTo(target).add(fmt, args).send();
	}

	public void sendMessageTo(String target, Style style, String fmt, String... args) {
		this.buildMessageTo(target).add(style, fmt, args).send();
	}

	public void sendAction(String fmt, String... args) {
		this.buildAction().add(fmt, args).send();
	}

	public void sendActionTo(String target, String fmt, String... args) {
		this.buildActionTo(target).add(fmt, args).send();
	}

	public void sendNotice(String fmt, String... args) {
		this.buildNotice().add(fmt, args).send();
	}

	public static void broadcastMessage(String message) {
		for(NoiseBot bot : bots.values()) {
			bot.sendMessage(message);
		}
	}

	public static void broadcastNotice(String notice) {
		for(NoiseBot bot : bots.values()) {
			bot.sendNotice(notice);
		}
	}

	void onChannelJoin() {
		this.loadModules();
	}

	public static void main(String[] args) {
		Log.i("NoiseBot has started");
		NoiseBot.loadConfig();
		final String[] connectionNames = args.length == 0 ? new String[] {DEFAULT_CONNECTION} : args;

		//TODO Actually check that the whole config is sane, instead of checking like 25% of it
		final StringMap configConnections = (StringMap)NoiseBot.config.get("connections");
		{
			boolean bad = false;
			final Set<String> checkSet = new HashSet<String>(args.length);
			checkSet.addAll(Arrays.asList(connectionNames));
			for(Object entry : configConnections.entrySet()) {
				final String name = (String)((Map.Entry)entry).getKey();
				if(name.contains("#")) {
					System.out.printf("Bad connection name (contains #): %s\n", name);
					bad = true;
				}
				checkSet.remove(name);
			}
			if(!checkSet.isEmpty()) {
				System.out.printf("Undefined connections: %s\n", checkSet.stream().collect(Collectors.joining(", ")));
				bad = true;
			}
			if(bad) {
				System.exit(1);
			}
		}

		for(String connectionName : connectionNames) {
			final StringMap data = (StringMap)configConnections.get(connectionName);
			final String type = data.containsKey("type") ? (String)data.get("type") : "irc";

			try {
				Protocol prot = null;
				for(Protocol candidate : Protocol.values()) {
					if(candidate.name().equalsIgnoreCase(type)) {
						prot = candidate;
						break;
					}
				}
				if(prot == null) {
					System.out.printf("Unknown connection type: %s\n", type);
					System.exit(1);
				}
				switch(prot) {
				case IRC:
					IRCNoiseBot.createBots(connectionName, data);
					break;
				case Slack:
					SlackNoiseBot.createBots(connectionName, data);
					break;
				}
			} catch(IOException e) {
				System.out.printf("%s\n", e.getMessage());
				System.exit(1);
			}
		}

		if(config.containsKey("github-webhook-secret")) {
			Git.startGithubListener((String)config.get("github-webhook-secret"));
		}
	}

	public static File getDataFile(String filename) {
		final File rtn = new File(DATA_DIRECTORY, filename);
		synchronized(moduleFileDeps) {
			final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			if(stack.length > 2) {
				final StackTraceElement caller = stack[2];
				if(caller.getClassName().startsWith("modules.")) {
					final String moduleName = caller.getClassName().substring(8);
					if(!moduleFileDeps.containsKey(moduleName)) {
						moduleFileDeps.put(moduleName, new HashSet<File>());
					}
					moduleFileDeps.get(moduleName).add(rtn);
				}
			}
		}
		return rtn;
	}

	private static ClassLoader getModuleLoader() {
		return new ClassLoader(NoiseBot.class.getClassLoader()) {
			public Class loadClass (String name, boolean resolve) throws ClassNotFoundException {
				Class c = null;
//				if((c = findLoadedClass(name)) != null) {return c;}
				File f = new File("bin", name.replace('.', File.separatorChar) + ".class");
				if(name.startsWith("modules.") && f.exists()) {
					int length = (int) f.length();
					byte[] classbytes = new byte[length];
					try {
						DataInputStream in = new DataInputStream(new FileInputStream(f));
						in.readFully(classbytes);
						in.close();
					} catch(FileNotFoundException e) {
						throw new ClassNotFoundException(e.getMessage());
					} catch(IOException e) {
						throw new ClassNotFoundException(e.getMessage());
					}

					c = defineClass(name, classbytes, 0, length);
					if(resolve) {resolveClass(c);}
					return c;
				}

				if((c = findSystemClass(name)) != null) {return c;}
				throw new ClassNotFoundException("Unknown class " + name);
			}
		};
	}
}
