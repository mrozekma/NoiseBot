package main;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import com.mrozekma.taut.TautException;
import debugging.Log;

import static main.Utilities.exceptionString;
import static main.Utilities.pluralize;
import static main.Utilities.reverse;

import com.google.gson.internal.StringMap;
import org.json.JSONArray;
import org.json.JSONException;

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

	public static final Map<String, NoiseBot> bots = new HashMap<>();
	public static final Map<String, Set<File>> moduleFileDeps = new HashMap<>();
	public static Git.Revision revision = Git.head();
	public static boolean revisionDirty = Git.isDirty();
	private static Map config;

	protected final String channel;
	protected final boolean quiet;
	protected final String[] fixedModules;
	protected Optional<StringMap> owner = Optional.empty();
	protected final Map<String, NoiseModule> modules = new HashMap<>();

	protected NoiseBot(String channel, boolean quiet, String[] fixedModules) {
		this.channel = channel;
		this.quiet = quiet;
		this.fixedModules = fixedModules;
	}

	protected void setOwner(StringMap owner) {
		this.owner = Optional.of(owner);
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
				this.sendMessage("#coreerror Failed loading module %s: %s", moduleName, exceptionString(e));
			}
		}

		{
			final int moduleCount = this.modules.size();
			final int patternCount = this.modules.values().stream().mapToInt(module -> module.getPatterns().length).sum();

			Log.i("Done loading revision %s", this.revision.getHash());
			if(!this.quiet) {
				this.sendNotice(String.format("NoiseBot revision %s%s (loaded %s watching for %s)", this.revision.getHash(), this.revisionDirty ? "-dirty" : "", pluralize(moduleCount, "module", "modules"), pluralize(patternCount, "pattern", "patterns")));
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
				if(Modifier.isAbstract(c.getModifiers())) {
					throw new ModuleInitException("Unable to instantiate module " + moduleName + ": Module is abstract");
				}

				// Try loading from disk
				module = NoiseModule.load(this, c);

				// Otherwise just make a new one
				if(module == null) {
					module = c.newInstance();
				}
			}

			if(module.getFriendlyName() == null) {
				throw new ModuleInitException("Module " + moduleName + " does not have a friendly name");
			} else if(!module.supportsProtocol(this.getProtocol())) {
				throw new ModuleInitException("Module " + moduleName + " does not support " + this.getProtocol());
			}

			module.init(this);
			this.modules.put(moduleName, module);
		} catch(ModuleInitException e) {
			Log.e(e);
			throw e;
		} catch(ClassCastException e) {
			Log.e(e);
			throw new ModuleInitException("Unable to instantiate module " + moduleName + ": Module does not extend NoiseModule");
		} catch(ClassNotFoundException e) {
			Log.e(e);
			throw new ModuleInitException("Unable to instantiate module " + moduleName + ": Module does not exist");
		} catch(Exception e) {
			Log.e(e);
			throw new ModuleInitException("Unable to instantiate module " + moduleName + ": " + exceptionString(e));
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
		final Map<String, Object> moduleConfig = new HashMap<>();
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
					bot.sendMessage("#coreerror %s", e.getMessage());
				}
			}
		}
	}

	public ViewContext makeViewContext() {
		return new ViewContext(new Message(this, null, null, this.channel));
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
		final StringMap owner;
		if(this.owner.isPresent()) {
			owner = this.owner.get();
		} else if(config.containsKey("owner")) {
			owner = (StringMap)config.get("owner");
		} else {
			return false;
		}

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

	private void sync(String from, String to, Git.Revision oldrev, Git.Revision[] revs, boolean coreChanged) {
		// Reload affected modules
		final List<String> reloads = new LinkedList<>(), failedReloads = new LinkedList<>();
		if(!coreChanged) {
			final String[] moduleNames = Git.affectedModules(this, from, to);
			for(String moduleName : moduleNames) {
				try {
					this.reloadModule(moduleName);
					reloads.add(moduleName);
				} catch(ModuleInitException | ModuleUnloadException e) {
					failedReloads.add(moduleName);
				}
			}
		}

		this.outputSyncInfo(oldrev, revs, coreChanged, reloads.toArray(new String[0]));

		if(!failedReloads.isEmpty()) {
			throw new Git.SyncException(String.format("Unable to reload %s: %s", pluralize(failedReloads.size(),  "module", "modules", false), failedReloads.stream().collect(Collectors.joining(", "))));
		}
	}

	protected void outputSyncInfo(Git.Revision oldrev, Git.Revision[] revs, boolean coreChanged, String[] reloadedModules) {
		this.sendNotice("Synced " + pluralize(revs.length, "revision", "revisions") + ": " + Git.diffLink(oldrev, this.revision));
		for(Git.Revision rev : reverse(revs)) {
			this.sendNotice("    %s", rev);
		}

		if(coreChanged) {
			this.sendNotice("#yellow Core files changed; NoiseBot will restart");
		} else if(reloadedModules.length > 0) {
			Style.pushOverrideMap(new HashMap<String, Style>() {{
				Style.addHelpStyles(getProtocol(), this);
			}});
			try {
				this.sendNotice("Reloaded modules: #([, ] #module %s)", (Object)reloadedModules);
			} finally {
				Style.popOverrideMap();
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

	static void broadcastIssueEvent(String action, JSONObject issue) throws JSONException {
		// If tagged with a particular protocol, only show the issue on bots connected under that protocol
		// (e.g. IRC users probably don't care about Slack-specific issues)
		List<Protocol> protocols = new LinkedList<>();
		if(issue.has("labels")) {
			final JSONArray labels = issue.getJSONArray("labels");
			for(int i = 0; i < labels.length(); i++) {
				final String label = labels.getJSONObject(i).getString("name");
				// Can't use Protocol.valueOf because of case
				for(Protocol protocol : Protocol.values()) {
					if(label.equalsIgnoreCase(protocol.name())) {
						protocols.add(protocol);
					}
				}
			}
		}

		for(NoiseBot bot : bots.values()) {
			if(protocols.isEmpty() || protocols.contains(bot.getProtocol())) {
				bot.onIssueEvent(action, issue);
			}
		}
	}

	protected void onIssueEvent(String action, JSONObject issue) throws JSONException {
		this.sendNotice("Issue ##%d %s: %s -- %s", issue.getInt("number"), action, issue.getString("title"), issue.getString("html_url"));
	}

	// By default, just ignore the style entirely. Child classes will probably override this
	public String format(Style style, String text) {
		return text;
	}

	public abstract SentMessage[] sendMessageBuilders(MessageBuilder... builders);

	public MessageBuilder buildMessage() {
		return this.buildMessageTo(this.channel);
	}

	public MessageBuilder buildMessageTo(String target) {
		return new MessageBuilder(this, target, MessageBuilder.Type.MESSAGE);
	}

	public MessageBuilder editMessage(SentMessage replacing) {
		return new MessageBuilder(this, replacing.target, replacing.type, replacing);
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

	public SentMessage[] sendMessage(String fmt, Object... args) {
		return this.buildMessage().add(fmt, args).send();
	}

	public SentMessage[] sendMessageTo(String target, String fmt, Object... args) {
		return this.buildMessageTo(target).add(fmt, args).send();
	}

	public SentMessage[] sendAction(String fmt, Object... args) {
		return this.buildAction().add(fmt, args).send();
	}

	public SentMessage[] sendActionTo(String target, String fmt, Object... args) {
		return this.buildActionTo(target).add(fmt, args).send();
	}

	public SentMessage[] sendNotice(String fmt, Object... args) {
		return this.buildNotice().add(fmt, args).send();
	}

	public static void broadcastMessage(String message) {
		for(NoiseBot bot : bots.values()) {
			bot.sendMessage("%s", message);
		}
	}

	public static void broadcastNotice(String notice) {
		for(NoiseBot bot : bots.values()) {
			bot.sendNotice("%s", notice);
		}
	}

	public static void broadcastNotice(Collection<Protocol> protocols, String notice) {
		for(NoiseBot bot : bots.values()) {
			if(protocols.contains(bot.getProtocol())) {
				bot.sendNotice("%s", notice);
			}
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
			final Set<String> checkSet = new HashSet<>(args.length);
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
					SlackNoiseBot.createBot(connectionName, data);
					break;
				}
			} catch(IOException | TautException e) {
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
