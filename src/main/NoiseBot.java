package main;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import static org.jibble.pircbot.Colors.*;
import static panacea.Panacea.*;

import debugging.Log;

import modules.Help;

import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;

import com.google.gson.internal.StringMap;

import panacea.MapFunction;
import panacea.ReduceFunction;

/**
 * NoiseBot
 *
 * @author Michael Mrozek
 *         Created June 13, 2009.
 */
public class NoiseBot {
	public static final String DEFAULT_CONNECTION = "default";
	private static final String CONFIG_FILENAME = "config";
	private static final String DATA_DIRECTORY = "data";
	public static final String STORE_DIRECTORY = "store";

	public static final Map<String, NoiseBot> bots = new HashMap<String, NoiseBot>();
	public static final Map<String, Set<File>> moduleFileDeps = new HashMap<String, Set<File>>();
	public static Git.Revision revision = Git.head();
	private static Map config;

	private final Server server;
	private final String channel;
	private final boolean quiet;
	private Map<String, NoiseModule> modules = new HashMap<String, NoiseModule>();

	public NoiseBot(Server server, String channel, boolean quiet) {
		this.server = server;
		this.channel = channel;
		this.quiet = quiet;
	}

	public void quit() {
		if(this.server.getChannels().length > 1) {
			Log.i("Parting " + this.channel);
			this.server.partChannel(this.channel);
		} else {
			Log.i("Disconnecting");
			this.server.disconnect();
		}

		try {this.saveModules();} catch(ModuleSaveException e) {Log.e(e);}

		for(Map.Entry<String, NoiseBot> entry : NoiseBot.bots.entrySet()) {
			if(entry.getValue() == this) {
				NoiseBot.bots.remove(entry.getKey());
				break;
			}
		}
		if(NoiseBot.bots.isEmpty()) {
			Log.i("Quitting");
			exit();
		}
	}

	public Map<String, NoiseModule> getModules() {return this.modules;}

	private void loadModules() {
		if(!this.modules.isEmpty()) {
			Log.w(pluralize(this.modules.size(), "module", "modules") + " already loaded");
			return;
		}

		// Always load the module manager
		try {
			this.loadModule("ModuleManager");
		} catch(ModuleLoadException e) {
			Log.e(e);
		}

		final File moduleFile = new File(this.getStoreDirectory(), "modules");
		if(moduleFile.exists()) {
			try {
				final String[] moduleNames = Serializer.deserialize(moduleFile, String[].class);
				Log.i("Loading %d modules from store", moduleNames.length);

				for(String moduleName : moduleNames) {
					if(moduleName.equals("ModuleManager")) {continue;}
					try {
						this.loadModule(moduleName);
					} catch(ModuleLoadException e) {
						Log.e("Failed loading module %s", moduleName);
						Log.e(e);
					}
				}
			} catch(Exception e) {
				Log.e("Failed to load modules");
				Log.e(e);
			}
		}

		{
			final int moduleCount = this.modules.size();
			final int patternCount = reduce(map(this.modules.values().toArray(new NoiseModule[0]), new MapFunction<NoiseModule, Integer>() {
				@Override public Integer map(NoiseModule module) {
					return module.getPatterns().length;
				}
			}), new ReduceFunction<Integer, Integer>() {
				@Override public Integer reduce(Integer source, Integer accum) {
					return source + accum;
				}
			}, 0);

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
			throw new ModuleSaveException("Unable to save some modules: " + implode(map(failedSaves.toArray(new NoiseModule[0]), new MapFunction<NoiseModule, String>() {
				@Override public String map(NoiseModule module) {return module.getClass().getSimpleName();}
			}), ", "));
		}
	}

	public void loadModule(String moduleName) throws ModuleLoadException {
		Log.i("Loading module: %s", moduleName);
		if(this.modules.containsKey(moduleName)) {
			throw new ModuleLoadException("Module " + moduleName + " already loaded");
		}

		try {
			NoiseModule module;

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
				throw new ModuleLoadException("Module " + moduleName + " does not have a friendly name");
			}

			final Map<String, String> moduleConfig = new HashMap<String, String>();
			if(NoiseBot.config.containsKey("modules")) {
				final StringMap data = (StringMap)NoiseBot.config.get("modules");
				if(data.containsKey(moduleName)) {
					final StringMap moduleData = (StringMap)data.get(moduleName);
					for(Object _entry : moduleData.entrySet()) {
						final Map.Entry<String, String> entry = (Map.Entry<String, String>)_entry;
						moduleConfig.put(entry.getKey(), entry.getValue());
					}
				}
			}

			module.init(this, moduleConfig);
			this.modules.put(moduleName, module);
		} catch(ClassCastException e) {
			Log.e(e);
			throw new ModuleLoadException("Defined module " + moduleName + " does not extend NoiseModule");
		} catch(ClassNotFoundException e) {
			Log.e(e);
			throw new ModuleLoadException("Unable to instantiate module " + moduleName + ": Module does not exist");
		} catch(Exception e) {
			Log.e(e);
			throw new ModuleLoadException("Unable to instantiate module " + moduleName + ": " + e.getMessage());
		}
	}

	public void unloadModule(String moduleName) throws ModuleUnloadException {
		Log.i("Unloading module: %s", moduleName);
		final Class c;
		try {
			c = getModuleLoader().loadClass("modules." + moduleName);
		} catch(Exception e) {
			throw new ModuleUnloadException("Unable to unload module " + moduleName + ": Module does not exist");
		}

		if(this.modules.containsKey(moduleName)) {
			final NoiseModule module = this.modules.get(moduleName);
			module.save();
			module.unload();
			this.modules.remove(moduleName);
			moduleFileDeps.remove(moduleName);
		} else {
			throw new ModuleUnloadException("Unable to unload module " + moduleName + ": Module not loaded");
		}

		// Immediately reload the module manager
		if(moduleName.equals("ModuleManager")) {
			try {
				loadModule(moduleName);
			} catch(ModuleLoadException e) {
				throw new ModuleUnloadException(e);
			}
		}
	}

	public String getBotNick() {
		return this.server.getConnection().nick;
	}

	public User[] getUsers() {return this.server.getUsers(this.channel);}
	public String[] getNicks() {
		return map(this.getUsers(), new MapFunction<User, String>() {
			@Override public String map(User source) {
				return source.getNick();
			}
		}, new String[0]);
	}
	public boolean isOnline(String nick) {
		for(User user : this.getUsers()) {
			if(nick.equals(user.getNick())) {
				return true;
			}
		}
		return false;
	}

	public void clearPendingSends() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		// I laugh at abstractions
		final Field outQueueField = this.server.getClass().getSuperclass().getDeclaredField("_outQueue");
		outQueueField.setAccessible(true);
		final org.jibble.pircbot.Queue outQueue = (org.jibble.pircbot.Queue)outQueueField.get(this.server);

		// I laugh at them further
		// Also, implementing a queue using a vector is terrible
		final Field queueField = outQueue.getClass().getDeclaredField("_queue");
		queueField.setAccessible(true);
		final Vector queue = (Vector)queueField.get(outQueue);

		synchronized(queue) {
			final Iterator iter = queue.iterator();
			while(iter.hasNext()) {
				final Object obj = iter.next();
				if(obj.toString().startsWith("PRIVMSG " + this.channel + " ")) {
					iter.remove();
				}
			}
		}
	}

	File getStoreDirectory() {
		final Connection conn = this.server.getConnection();
		return new File(STORE_DIRECTORY, String.format("%s@%s:%d%s", conn.nick, conn.server, conn.port, this.channel));
	}

	public void whois(String nick, WhoisHandler handler) {
		handler.setNick(nick);
		handler.startWaiting();
		this.server.sendRawLine("WHOIS " + nick);
	}

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

	public void sync() {
		final String from = this.revision.getHash(), to = "HEAD";
		final Git.Revision[] revs = Git.diff(from, to);
		final Git.Revision oldrev = this.revision;

		boolean coreChanged = false;
		try {
			final String src = new File("src").getAbsolutePath();
			final String modules = new File("src", "modules").getAbsolutePath();
			for(File f : Git.getFiles(from, to)) {
				final String path = f.getAbsolutePath();
				if(path.startsWith(src) && !path.startsWith(modules)) {
					coreChanged = true;
					break;
				}
			}
		} catch(IOException e) {}

		final String[] moduleNames = Git.affectedModules(this, from, to);
		this.revision = Git.head();
		final String[] coloredNames = map(moduleNames, new MapFunction<String, String>() {
			@Override public String map(String name) {
				return Help.COLOR_MODULE +  name + NORMAL;
			}
		}, new String[0]);

		for(String moduleName : moduleNames) {
			try {
				this.unloadModule(moduleName);
			} catch(ModuleUnloadException e) {}

			// ModuleManager is automatically reloaded in unloadModule()
			if(!moduleName.equals("ModuleManager")) {
				try {
					this.loadModule(moduleName);
				} catch(ModuleLoadException e) {
					throw new Git.SyncException("Unable to load module " + moduleName);
				}
			}
		}

		this.sendNotice("Synced " + pluralize(revs.length, "revision", "revisions") + ":");
		for(Git.Revision rev : reverse(revs))
			this.sendNotice("    " + rev);
		if(moduleNames.length != 0) {
			this.sendNotice("Reloaded modules: " + implode(coloredNames, ", "));
		}
		if(coreChanged) {
			this.sendNotice(YELLOW + "Core files changed; NoiseBot may need to be restarted" + NORMAL);
		}
		this.sendNotice("Changes: " + Git.diffLink(oldrev, this.revision));
	}

	public static void syncAll() {
		for(NoiseBot bot : NoiseBot.bots.values()) {
			bot.sync();
		}
	}

	// Most things should use send*(String)
	// The target versions should only be used for PMing
	public void sendMessage(String target, String message) {this.server.sendMessage(target, message);}
	public void sendAction(String target, String message) {this.server.sendAction(target, message);}
	public void sendNotice(String target, String message) {this.server.sendNotice(target, message);}

	public void sendMessage(String message) {Log.out("M> " + message); this.server.sendMessage(this.channel, message);}
	public void sendAction(String action) {Log.out("A> " + action); this.server.sendAction(this.channel, action);}
	public void sendNotice(String notice) {Log.out("N> " + notice); this.server.sendNotice(this.channel, notice);}
	public void reply(Message sender, String message) {this.reply(sender.getSender(), message);}
	public void reply(String username, String message) {this.sendMessage((username == null ? "" : username + ": ") + message);}
	public void kickVictim(String victim, String reason) {this.server.kick(this.channel, victim, reason);}

	void onChannelJoin() {
		this.loadModules();
	}

	public static void main(String[] args) {
		Log.i("NoiseBot has started");
		try {
			NoiseBot.config = Serializer.deserialize(CONFIG_FILENAME, StringMap.class);
		} catch(FileNotFoundException e) {
			NoiseBot.config = new StringMap();
		}
		final String[] connectionNames = args.length == 0 ? new String[] {DEFAULT_CONNECTION} : args;

		//TODO Actually check that the whole config is sane, instead of checking like 25% of it
		final StringMap configConnections = (StringMap)NoiseBot.config.get("connections");
		{
			boolean bad = false;
			final Set<String> checkSet = new HashSet<String>(args.length);
			checkSet.addAll(Arrays.asList(connectionNames));
			for(Object entry : configConnections.entrySet()) {
				final String name = (String)((Map.Entry)entry).getKey();
				final StringMap values = (StringMap)((Map.Entry)entry).getValue();
				if(name.contains("#")) {
					System.out.printf("Bad connection name (contains #): %s\n", name);
					bad = true;
				}
				checkSet.remove(name);
				if(!values.keySet().containsAll(Arrays.asList(new String[] {"server", "port", "nick", "channels"}))) {
					System.out.printf("Malformed connection: %s\n", name);
					bad = true;
				}
			}
			if(!checkSet.isEmpty()) {
				System.out.printf("Undefined connections: %s\n", implode(checkSet.toArray(new String[0])));
				bad = true;
			}
			if(bad) {
				return;
			}
		}

		for(String connectionName : connectionNames) {
			final StringMap data = (StringMap)configConnections.get(connectionName);
			final String host = (String)data.get("server");
			final int port = (int)Double.parseDouble("" + data.get("port"));
			final String nick = (String)data.get("nick");
			final String pass = data.containsKey("password") ? (String)data.get("password") : null;
			final boolean quiet = data.containsKey("quiet") ? (Boolean)data.get("quiet") : false;
			final Server server = new Server(new Connection(host, port, nick, pass));

			final List<String> channels = (List<String>)data.get("channels");
			for(String channel : channels) {
				final NoiseBot bot = new NoiseBot(server, channel, quiet);
				NoiseBot.bots.put(connectionName + channel, bot);
				server.addBot(channel, bot);
			}

			if(!server.connect()) {
				exit();
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
