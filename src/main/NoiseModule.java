package main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ho.yaml.Yaml;
import org.ho.yaml.YamlConfig;
import org.jibble.pircbot.User;

import static org.jibble.pircbot.Colors.*;

import debugging.Log;


/**
 * NoiseModule
 *
 * @author Michael Mrozek
 *         Created Jun 13, 2009.
 */
public abstract class NoiseModule implements Comparable<NoiseModule> {
	private static final String COLOR_ERROR = RED;

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	protected static @interface Command {
		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	protected static @interface PM {
		String value();
	}
	
	// Tell JYaml to serialize all non-transient fields (even if private), and to use any constructors it wants to unserialize
	static {
	    Yaml.config = new YamlConfig() {
			@Override public boolean isFieldAccessibleForDecoding(Field field) {return this.isFieldAccessibleForEncoding(field);}
			@Override public boolean isFieldAccessibleForEncoding(Field field) {return (field.getModifiers() & Modifier.TRANSIENT) == 0;}
			@Override public boolean isConstructorAccessibleForDecoding(Class c) {return true;}
	    };
	}
	
	protected transient NoiseBot bot;
	protected transient Map<Pattern, Method> patterns = new LinkedHashMap<Pattern, Method>();
	protected transient Map<Pattern, Method> pmPatterns = new LinkedHashMap<Pattern, Method>();
	
	public void init(NoiseBot bot) {
		this.bot = bot;
		Log.v(this + " - Init");
		
		for(Method method : this.getClass().getDeclaredMethods()) {
			final Command command = method.getAnnotation(Command.class);
			if(command != null) {
				final Pattern pattern = Pattern.compile(command.value());
				Log.i(this + " - Added pattern " + command.value() + " for method " + method);
				this.patterns.put(pattern, method);
			}

			final PM pm = method.getAnnotation(PM.class);
			if(pm != null) {
				final Pattern pattern = Pattern.compile(pm.value());
				Log.i(this + " - Added PM pattern " + pm.value() + " for method " + method);
				this.pmPatterns.put(pattern, method);
			}
		}
	}
	
	public void unload() {
		Log.v(this + " - Unload");
	}
	
	public void onJoin(String sender, String login, String hostname) {this.joined(sender);}
	public void onPart(String sender, String login, String hostname) {this.left(sender);}
	public void onQuit(String sender, String login, String hostname, String reason) {this.left(sender);}
	public void onUserList(User[] users) {
		for(User user : users) {
			this.joined(user.getNick());
		}
	}
	public void onKick(String kickerNick,String kickerLogin, String kickerHostname, String recipientNick,String reason) {this.left(recipientNick);}
	public void onNickChange(String oldNick, String login, String hostname, String newNick) {
		this.left(oldNick);
		this.joined(newNick);
	}
	
	public void onTopic(String topic, String setBy, long date, boolean changed) {}
	
	protected void joined(String nick) {}
	protected void left(String nick) {}
	
	public abstract String getFriendlyName();
	public abstract String getDescription();
	public abstract String[] getExamples();
	
	// Doesn't show in the help listing, and only I can trigger
	public boolean isPrivate() {return false;}
	
	public File[] getDependentFiles() {return new File[0];}
	
	public Pattern[] getPatterns() {return this.patterns.keySet().toArray(new Pattern[0]);}
	
	public void processMessage(Message message) {
		Log.v(this + " - Processing message: " + message);
		for(Pattern pattern : (message.isPM() ? this.pmPatterns : this.patterns).keySet()) {
			Log.v("Trying pattern: " + pattern);
			final Matcher matcher = pattern.matcher(message.getMessage());
			if(matcher.matches()) {
				final Method method = (message.isPM() ? this.pmPatterns : this.patterns).get(pattern);
				Log.i(this + " - Handling message: " +  message + " -- " + method.getDeclaringClass().getName() + "." + method.getName());
				final Class[] params = method.getParameterTypes();
				if(matcher.groupCount() == params.length - 1) {
					Object[] args = new Object[params.length];
					args[0] = message;
					for(int i = 1; i < args.length; i++) {
						if(params[i] == int.class) {
							try {
								args[i] = Integer.parseInt(matcher.group(i));
							} catch(NumberFormatException e) {
								throw new ArgumentMismatchException("Argument " + (i - 1) + " should be an integer");
							}
						} else {
							args[i] = matcher.group(i);
						}
					}
					
					try {
						Log.v("Invoking with " + args.length + " args");
						method.invoke(this, args);
						break;
					} catch(Exception e) {
						Log.e(e);
					}
				} else {
					throw new ArgumentMismatchException(params.length == 0 ? "Method doesn't take the mandatory Message instance" : "Expected " + (params.length - 1) + " argument" + (params.length - 1 == 1 ? "" : "s") + "; found " + matcher.groupCount());
				}
			}
		}
	}
	
	public static <T extends NoiseModule> T load(Class<T> moduleType) {
		Log.v(moduleType.getSimpleName() + " - Loading");
		if(!Arrays.asList(moduleType.getInterfaces()).contains(Serializable.class)) {return null;}
		
		try {
			return Yaml.loadType(new File("store", moduleType.getSimpleName()), moduleType);
		} catch(FileNotFoundException e) {
			Log.v("No store file for " + moduleType.getSimpleName());
		} catch(Exception e) { // Should just be IOException
			Log.w("Unable to deserialize " + moduleType.getSimpleName());
			Log.w(e);
		}
		
		return null;
	}
	
	public boolean save() {
		Log.v(this + " - Saving");
		if(!(this instanceof Serializable)) {return true;}
		
		try {
			Yaml.dump(this); // Somehow this tricks JYaml into getting the most recent data from memory instead of outputting old data
			Yaml.dump(this, new File("store", this.getClass().getSimpleName()));
			return true;
		} catch(Exception e) { // Should just be IOException
			Log.e(e);
			return false;
		}
	}

	@Override public int compareTo(NoiseModule other) {return this.getFriendlyName().compareTo(other.getFriendlyName());}
	@Override public String toString() {return this.getFriendlyName();}

	protected String encoded(final String s) {
		try {
			final byte bytes[] = s.getBytes("UTF8");
			return new String(bytes, "ISO8859_1");
		} catch (UnsupportedEncodingException e) {
			this.bot.sendMessage(COLOR_ERROR + "He looks like a fuckin' loser.");
			return s;
		}
	}
}
