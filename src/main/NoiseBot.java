package main;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static panacea.Panacea.*;

import modules.Help;

import org.jibble.pircbot.Colors;
import org.jibble.pircbot.DccFileTransfer;
import org.jibble.pircbot.IrcException;
import org.jibble.pircbot.NickAlreadyInUseException;
import org.jibble.pircbot.PircBot;
import org.jibble.pircbot.User;

import panacea.MapFunction;
import panacea.ReduceFunction;

// DONE ping
// DONE seen
// DONE tell
// DONE translate
// DONE wikipedia
// DONE poll
// DONE hug
// DONE fortune
// DONE spook
// DONE choose
// DONE slap
// DONE youtube
// DONE fascist

// TODO quote module, remind module

/**
 * NoiseBot
 *
 * @author Michael Mrozek
 *         Created June 13, 2009.
 */
public class NoiseBot extends PircBot {
	private static final String SERVER = "irc.lug.rose-hulman.edu";
// 	private static final String SERVER = "testnet.freenode.net";
	private static final int PORT = 6667;
	public static final String NICK = "rhnoise";
	public static final String PASSWORD = "7wrafAunerab(on";
	public static final String CHAN = "#rhnoise";
//	public static final String NICK = "Morasique-test";
//	private static final String CHAN = "#morasique";
	public static final String ME = "Morasique";

	public Git.Revision revision = Git.head();
	private Map<String, NoiseModule> modules = new HashMap<String, NoiseModule>();
	
	public void quit() {
		this.disconnect();
		try {this.saveModules();} catch(ModuleSaveException e) {e.printStackTrace();}
		exit();
	}
	
	public NoiseModule[] getModules() {return this.modules.values().toArray(new NoiseModule[0]);}
	
	private void loadModules() {
		// Always load the module manager
		try {
			this.loadModule("ModuleManager");
		} catch(ModuleLoadException e) {
			e.printStackTrace();
		}
		
		final File moduleFile = new File("store", "modules");
		if(moduleFile.exists()) {
			try {
				final String[] moduleNames = (String[])new ObjectInputStream(new FileInputStream(moduleFile)).readObject();
		
				for(String moduleName : moduleNames) {
					if(moduleName.equals("ModuleManager")) {continue;}
					try {
						this.loadModule(moduleName);
					} catch(ModuleLoadException e) {
						System.err.println("Failed loading module " + moduleName + ": " + e.getMessage());
						e.printStackTrace();
					}
				}
			} catch(Exception e) {
				System.err.println("Unable to load modules");
				e.printStackTrace();
			}
		}
		
		for(NoiseModule module : this.modules.values()) {
			for(Class iface : module.getClass().getInterfaces()) {
				if(iface == Serializable.class) {
					final File f = new File("store", module.getClass().getSimpleName());
					if(f.exists()) {
						try {
							final NoiseModule saved = (NoiseModule)new ObjectInputStream(new FileInputStream(f)).readObject();
							for(Field field : saved.getClass().getDeclaredFields()) {
								if((field.getModifiers() & Modifier.TRANSIENT) != 0 || (field.getModifiers() & Modifier.FINAL) != 0) {continue;}
								final Field newField = module.getClass().getDeclaredField(field.getName());
								final boolean accessible = newField.isAccessible();
								if(!accessible) {field.setAccessible(true); newField.setAccessible(true);}
								newField.set(module, field.get(saved));
								if(!accessible) {field.setAccessible(false); newField.setAccessible(false);}
							}
						} catch(InvalidClassException e) {
							System.err.println("Incompatible save file for module " + module.getClass().getSimpleName() + "; ignoring");
						} catch(Exception e) {
							System.err.println("Failed loading module " + module.getClass().getSimpleName());
							e.printStackTrace();
						}
					}
					break;
				}
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
			
			this.sendNotice("NoiseBot revision " + this.revision.getHash());
			this.sendNotice("Done loading " + moduleCount + " modules watching for " + patternCount + " patterns");
		}
	}
	
	public void saveModules() throws ModuleSaveException {
		final File moduleFile = new File("store", "modules");
		final String[] moduleNames = this.modules.keySet().toArray(new String[0]);
		try {
			new ObjectOutputStream(new FileOutputStream(moduleFile)).writeObject(moduleNames);
		} catch(IOException e) {
			throw new ModuleSaveException("Failed saving module list");
		}
		
		final Vector<NoiseModule> failedSaves = new Vector<NoiseModule>();
		for(NoiseModule module : this.modules.values()) {
			if(!module.save()) {
				failedSaves.add(module);
				System.err.println("Failed saving module " + module.getClass().getSimpleName());
			}
		}
		
		if(!failedSaves.isEmpty()) {
			throw new ModuleSaveException("Unable to save some modules: " + implode(map(failedSaves.toArray(new NoiseModule[0]), new MapFunction<NoiseModule, String>() {
				@Override public String map(NoiseModule module) {return module.getClass().getSimpleName();}
			}), ", "));
		}
	}
	
	public void loadModule(String moduleName) throws ModuleLoadException {
		if(this.modules.containsKey(moduleName)) {
			throw new ModuleLoadException("Module " + moduleName + " already loaded");
		}
		
		try {
			final Class c = getModuleLoader().loadClass("modules." + moduleName);
			final NoiseModule module = (NoiseModule)c.newInstance();
			
			if(module.getFriendlyName() == null) {
				throw new ModuleLoadException("Module " + moduleName + " does not have a friendly name");
			}
			
			module.init(this);
			this.modules.put(moduleName, module);
		} catch(ClassCastException e) {
			e.printStackTrace();
			throw new ModuleLoadException("Defined module " + moduleName + " does not extend NoiseModule");
		} catch(ClassNotFoundException e) {
			e.printStackTrace();
			throw new ModuleLoadException("Unable to instantiate module " + moduleName + ": Module does not exist");
		} catch(Exception e) {
			e.printStackTrace();
			throw new ModuleLoadException("Unable to instantiate module " + moduleName + ": " + e.getMessage());
		}
	}
	
	public void unloadModule(String moduleName) throws ModuleUnloadException {
		if(moduleName.equals("ModuleManager")) {
			throw new ModuleUnloadException("Can't unload the module manager");
		}
		
		final Class c;
		try {
			c = getModuleLoader().loadClass("modules." + moduleName);
		} catch(Exception e) {
			throw new ModuleUnloadException("Unable to unload module " + moduleName + ": Module does not exist");
		}
		
		if(this.modules.containsKey(moduleName)) {
			final NoiseModule module = this.modules.get(moduleName);
			module.unload();
			this.modules.remove(moduleName);
		} else {
			throw new ModuleUnloadException("Unable to unload module " + moduleName + ": Module not loaded");
		}
	}

	public User[] getUsers() {return this.getUsers(CHAN);}
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

	public void sendMessage(String message) {this.sendMessage(CHAN, message);}
	public void sendAction(String action) {this.sendAction(CHAN, action);}
	public void sendNotice(String notice) {this.sendNotice(CHAN, notice);}
	public void reply(Message sender, String message) {this.reply(sender.getSender(), message);}
	public void reply(String username, String message) {this.sendMessage((username == null ? "" : username + ": ") + message);}

	@Override protected void onMessage(String channel, String sender, String login, String hostname, String message) {
		System.out.println("Received message from " + sender + " (" + login + " @ " + hostname + ") -> " + channel + ": " + message);
		if(!channel.equals(CHAN)) {return;}

		for(NoiseModule module : this.modules.values()) {
			if(module.isPrivate() && !sender.equals(ME)) {continue;}
			
			try {
				module.processMessage(new Message(message, sender));
			} catch(Exception e) {
				this.sendNotice(e.getMessage());
				e.printStackTrace();
			}
		}
	}
	
	@Override protected void onPrivateMessage(String sender, String login, String hostname, String message) {
		if(message.equals(".info")) {
			this.sendMessage(sender, "I am currently in the channel " + Colors.GREEN + CHAN);
			this.sendMessage(sender, "The following modules are loaded:");
			for(NoiseModule module : sorted(this.getModules())) {
				if(module.isPrivate()) {continue;}
				this.sendMessage(sender, Help.COLOR_MODULE + module.getFriendlyName() + Colors.NORMAL + " -- " + module.getDescription());
			}
		} else {
			this.sendMessage(sender, "The only command available via PM is .info");
		}
	}

	@Override protected void onJoin(String channel, String sender, String login, String hostname) {
		if(sender.equals(NICK)) { // Done joining channel
			this.sendMessage("ChanServ", "VOICE " + CHAN);
			this.loadModules();
		} else {
			for(NoiseModule module : this.modules.values()) {module.onJoin(sender, login, hostname);}
		}
	}

	@Override protected void onPart(String channel, String sender, String login, String hostname) {
		for(NoiseModule module : this.modules.values()) {module.onPart(sender, login, hostname);}
	}
	
	@Override protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
		for(NoiseModule module : this.modules.values()) {module.onQuit(sourceNick, sourceLogin, sourceHostname, reason);}
	}
	
	@Override protected void onUserList(String channel, User[] users) {
		for(NoiseModule module : this.modules.values()) {module.onUserList(users);}
	}

	@Override protected void onKick(String channel, String kickerNick, String kickerLogin, String kickerHostname, String recipientNick, String reason) {
		for(NoiseModule module : this.modules.values()) {module.onKick(kickerNick, kickerLogin, kickerHostname, recipientNick, reason);}
	}

	@Override protected void onTopic(String channel, String topic, String setBy, long date, boolean changed) {
		for(NoiseModule module : this.modules.values()) {module.onTopic(topic, setBy, date, changed);}
	}

	@Override protected void onNickChange(String oldNick, String login, String hostname, String newNick) {
		for(NoiseModule module : this.modules.values()) {module.onNickChange(oldNick, login, hostname, newNick);}
	}

	@Override protected void onIncomingFileTransfer(DccFileTransfer transfer) {
		if(!transfer.getFile().getName().endsWith(".java")) {
			this.sendMessage(transfer.getNick(), "I only accept Java source files");
			return;
		}
		
		final String moduleName = transfer.getFile().getName().replaceAll("\\.java$", "");
		
		try {
			final Class c = getModuleLoader().loadClass("modules." + moduleName);
			if(!Pattern.matches(((NoiseModule)c.newInstance()).getOwner(), transfer.getNick())) {
				this.sendMessage(transfer.getNick(), "There is already a module named " + moduleName + " that you do not own");
				return;
			}
		} catch(ClassNotFoundException e) {
		} catch(InstantiationException e) {
		} catch(IllegalAccessException e) {}
		
		final File store = new File("upload", transfer.getFile().getName());
		transfer.receive(store, false);
	}
	
	@Override protected void onFileTransferFinished(DccFileTransfer transfer, Exception transferException) {
		if(transferException != null) {
			this.sendMessage(transfer.getNick(), "Transfer failed: " + transferException.getMessage());
			return;
		}
		
		final File upload = transfer.getFile();
		final File module = new File("src/modules", upload.getName());
		upload.renameTo(module);
		final String moduleName = transfer.getFile().getName().replaceAll("\\.java$", "");
		
		final JavaCompiler c = ToolProvider.getSystemJavaCompiler();
		try {
			final int result = c.run(null, null, null, "-cp", "pircbot.jar:bin", "-d", "bin", module.getCanonicalPath());
			if(result != 0) {
				System.out.println(module.getCanonicalPath());
				this.sendMessage(transfer.getNick(), "Compile failed; error code " + result);
				return;
			}
			this.sendMessage(transfer.getNick(), "Module " + moduleName + " compiled");
			try {
				this.unloadModule(moduleName);
				this.loadModule(moduleName);
				this.sendNotice("Module " + Help.COLOR_MODULE + moduleName + Colors.NORMAL + " updated and reloaded by " + Help.COLOR_COMMAND + transfer.getNick());
			} catch(ModuleUnloadException e) {
				this.sendNotice("Module " + Help.COLOR_MODULE + moduleName + Colors.NORMAL + " updated by " + Help.COLOR_COMMAND + transfer.getNick() + Colors.NORMAL + "; left unloaded");
			} catch(ModuleLoadException e) {
				this.sendNotice("Module " + Help.COLOR_MODULE + moduleName + Colors.NORMAL + " updated by " + Help.COLOR_COMMAND + transfer.getNick() + Colors.NORMAL + "; unable to reload");
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override protected void onOp(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient) {
		if(recipient.equalsIgnoreCase(NICK)) {
			this.sendMessage("ChanServ", "DEOP " + CHAN);
		}
	}

	public static void main(String[] args) {new NoiseBot();}
	public NoiseBot() {
		System.out.println("NoiseBot has started");
		this.setName(NICK);
		this.setLogin(NICK);
		try {
			System.out.println("Connecting to " + SERVER + ":" + PORT + " as " + NICK);
			this.connect(SERVER, PORT, PASSWORD);
		} catch(NickAlreadyInUseException e) {
			System.err.println("The nick " + NICK + " is already in use");
			System.exit(1);
		} catch(IrcException e) {
			System.err.println("Unexpected IRC error: " + e.getMessage());
			System.exit(1);
		} catch(IOException e) {
			System.err.println("Network error: " + e.getMessage());
			System.exit(1);
		}

		System.out.println("Joining " + CHAN);
		this.joinChannel(CHAN);
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
