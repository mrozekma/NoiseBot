package main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.TreeSet;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
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
		String value(); // Regex pattern (must be named 'value' for Java reasons)
		boolean caseSensitive() default true; // Pattern is case-sensitive
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	protected static @interface PM {
		String value();
		boolean caseSensitive() default true;
	}

	// Field is loaded from the config file
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	protected static @interface Configurable {
		String value(); // Config key
		boolean required() default true; // If the field is null, this module won't fire
	}

	protected transient NoiseBot bot;
	protected transient Map<Pattern, Method> patterns = new LinkedHashMap<Pattern, Method>();
	protected transient Map<Pattern, Method> pmPatterns = new LinkedHashMap<Pattern, Method>();

	public void init(final NoiseBot bot) throws ModuleInitException {
		this.bot = bot;
		Log.v(this + " - Init");

		final Map<String, String> variables = new HashMap<String, String>() {{
			put("bot.nick", bot.getBotNick());
		}};
		final StrSubstitutor sub = new StrSubstitutor(variables);

		final TreeSet<CtMethod> methods = new TreeSet<CtMethod>(new Comparator<CtMethod>() {
			@Override public int compare(CtMethod first, CtMethod second) {
				return Integer.compare(first.getMethodInfo().getLineNumber(0), second.getMethodInfo().getLineNumber(0));
			}
		});
		try {
			for(CtMethod ctmethod : ClassPool.getDefault().getCtClass(this.getClass().getName()).getDeclaredMethods()) {
				if(ctmethod.hasAnnotation(Command.class) || ctmethod.hasAnnotation(PM.class)) {
					methods.add(ctmethod);
				}
			}
		} catch(NotFoundException e) {
			throw new ModuleInitException(e);
		}
		for(CtMethod ctmethod : methods) {
			// This is way more difficult than it should be
			final Method method;
			try {
				final CtClass[] ctparams = ctmethod.getParameterTypes();
				final Class[] params = new Class[ctparams.length];
				for(int i = 0; i < ctparams.length; i++) {
					params[i] = ClassUtils.getClass(ctparams[i].getName());
				}
				method = this.getClass().getMethod(ctmethod.getName(), params);
			} catch (NotFoundException | ClassNotFoundException | NoSuchMethodException e) {
				throw new ModuleInitException(e);
			}

			final Command command = method.getAnnotation(Command.class);
			if(command != null) {
				final Pattern pattern = Pattern.compile(sub.replace(command.value()), command.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE);
				Log.i(this + " - Added pattern %s for method %s", command.value(), method);
				this.patterns.put(pattern, method);
			}

			final PM pm = method.getAnnotation(PM.class);
			if(pm != null) {
				final Pattern pattern = Pattern.compile(sub.replace(pm.value()), pm.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE);
				Log.i(this + " - Added PM pattern %s for method %s", pm.value(), method);
				this.pmPatterns.put(pattern, method);
			}
		}
	}

	public void setConfig(final Map<String, Object> config) throws ModuleInitException {
		final List<String> errors = new LinkedList<String>();
		for(Field field : this.getClass().getDeclaredFields()) {
			final Configurable configurable = field.getAnnotation(Configurable.class);
			if(configurable != null) {
				// Set to null (if possible), in case of error
				// Primitive types are left unchanged
				field.setAccessible(true);
				try {
					field.set(this, null);
				} catch(IllegalArgumentException|IllegalAccessException e) {}

				if(config.containsKey(configurable.value())) {
					final Object val = config.get(configurable.value());
					Log.i("Setting field from configuration entry `%s'", configurable.value());
					try {
						if(field.getType() == boolean.class && val.getClass() == Boolean.class) {
							field.setBoolean(this, (boolean)val);
						} else if(field.getType() == char.class && val.getClass() == Character.class) {
							field.setChar(this, (char)val);
						} else if(field.getType() == int.class && val.getClass() == Integer.class) {
							field.setInt(this, (int)val);
						} else if(field.getType() == int.class && val.getClass() == Double.class) {
							field.setInt(this, (int)(double)val);
						} else if(field.getType() == double.class && (val.getClass() == Integer.class || val.getClass() == Double.class)) {
							field.setDouble(this, (double)val);
						} else if(field.getType() == String.class && val.getClass() == String.class) {
							field.set(this, val);
						} else if(field.getType() == File.class && val.getClass() == String.class) {
							final File file = new File((String)val);
							if(file.exists()) {
								field.set(this, file);
							} else {
								errors.add(String.format("Config entry `%s' points to non-existent file", configurable.value()));
							}
						} else {
							errors.add(String.format("Config entry `%s' should be of type %s", configurable.value(), field.getType()));
						}
					} catch(IllegalAccessException e) {
						Log.e("Unable to set module field");
						Log.e(e);
					}
				} else if(configurable.required()) {
					errors.add(String.format("Missing config entry `%s'", configurable.value()));
				} else {
					Log.v("Skipping missing config entry `%s'", configurable.value());
				}
			}
		}

		if(!errors.isEmpty()) {
			throw new ModuleInitException("Invalid " + this.getClass().getSimpleName() + " configuration: " + errors.stream().collect(Collectors.joining(", ")));
		}
	}

	public void unload() {
		Log.v(this + " - Unload");
	}

	private boolean isEnabled() {
		for(Field field : this.getClass().getDeclaredFields()) {
			final Configurable configurable = field.getAnnotation(Configurable.class);
			if(configurable != null) {
				field.setAccessible(true);
				try {
					if(field.get(this) == null) {
						return false;
					}
				} catch(IllegalAccessException e) {
					Log.e(e);
				}
			}
		}
		return true;
	}

	public void onJoin(String sender, String login, String hostname) {if(this.isEnabled()) {this.joined(sender);}}
	public void onPart(String sender, String login, String hostname) {if(this.isEnabled()) {this.left(sender);}}
	public void onUserList(User[] users) {
		if(!this.isEnabled()) {return;}
		for(User user : users) {
			this.joined(user.getNick());
		}
	}
	public void onKick(String kickerNick,String kickerLogin, String kickerHostname, String recipientNick, String reason) {if(this.isEnabled()) {this.left(recipientNick);}}
	public void onNickChange(String oldNick, String login, String hostname, String newNick) {
		if(!this.isEnabled()) {return;}
		this.left(oldNick);
		this.joined(newNick);
	}

	public void onTopic(String topic, String setBy, long date, boolean changed) {}

	protected void joined(String nick) {}
	protected void left(String nick) {}

	public abstract String getFriendlyName();
	public abstract String getDescription();
	public abstract String[] getExamples();

	public boolean showInHelp() {return true;}

	public Pattern[] getPatterns() {return this.patterns.keySet().toArray(new Pattern[0]);}

	public void processMessage(Message message) {
		if(!this.isEnabled()) {
			Log.i(this + " - Skipping, module disabled");
			return;
		}
		Log.v(this + " - Processing message: %s", message);
		for(Pattern pattern : (message.isPM() ? this.pmPatterns : this.patterns).keySet()) {
			Log.v("Trying pattern: %s", pattern);
			final Matcher matcher = pattern.matcher(message.getMessage());
			if(matcher.matches()) {
				final Method method = (message.isPM() ? this.pmPatterns : this.patterns).get(pattern);
				Log.i(this + " - Handling message: %s -- %s.%s", message, method.getDeclaringClass().getName(), method.getName());
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
						Log.v("Invoking with %d args", args.length);
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

	public static <T extends NoiseModule> T load(NoiseBot bot, Class<T> moduleType) {
		Log.v("%s - Loading", moduleType.getSimpleName());
		if(!Arrays.asList(moduleType.getInterfaces()).contains(Serializable.class)) {return null;}

		try {
			return Serializer.deserialize(new File(bot.getStoreDirectory(), moduleType.getSimpleName()), moduleType);
		} catch(FileNotFoundException e) {
			Log.v("No store file for %s", moduleType.getSimpleName());
		}

		return null;
	}

	public boolean save() {
		Log.v(this + " - Saving");
		if(!(this instanceof Serializable)) {return true;}

		try {
            Serializer.serialize(new File(this.bot.getStoreDirectory(), this.getClass().getSimpleName()), this);
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

	protected void triggerIfOwner(final Message message, final Runnable fn, final boolean errorOnFail) {
		this.bot.whois(message.getSender(), new WhoisHandler() {
			@Override public void onResponse() {
				if(NoiseModule.this.bot.isOwner(this.nick, this.hostname, this.account)) {
					fn.run();
				} else if(errorOnFail) {
					NoiseModule.this.bot.sendMessage(COLOR_ERROR + "Operation not permitted");
				}
			}

			@Override public void onTimeout() {
				if(errorOnFail) {
					NoiseModule.this.bot.sendMessage(COLOR_ERROR + "Unable to whois " + message.getSender());
				}
			}
		});
	}
}
