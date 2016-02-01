package main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.jibble.pircbot.User;

import static org.jibble.pircbot.Colors.*;

import debugging.Log;
import org.json.JSONException;


/**
 * NoiseModule
 *
 * @author Michael Mrozek
 *         Created Jun 13, 2009.
 */
public abstract class NoiseModule implements Comparable<NoiseModule> {
	private static final String COLOR_ERROR = RED;

	public static class MessageResult {
		public final Message message;
		public final Optional<Method> handler;
		public final Optional<JSONObject> data;

		public MessageResult(Message message, Method handler, JSONObject data) {
			this.message = message;
			this.handler = Optional.of(handler);
			this.data = Optional.ofNullable(data);
		}
		public MessageResult(Message message, Method handler) {
			this.message = message;
			this.handler = Optional.of(handler);
			this.data = Optional.empty();
		}
		public MessageResult(Message message) {
			this.message = message;
			this.handler = Optional.empty();
			this.data = Optional.empty();
		}
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	protected @interface Command {
		String value(); // Regex pattern (must be named 'value' for Java reasons)
		boolean allowPM() default true; // Command can be triggered from private message
		boolean caseSensitive() default true; // Pattern is case-sensitive
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	protected @interface View {
		Protocol[] value() default {}; // protocol(s) this view is used for. Empty list if this is the default if no more-specific view exists
		String[] method() default {}; // Apparently null isn't a constant, so I can't use it for the default value
	}

	// Field is loaded from the config file
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	protected @interface Configurable {
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

		try {
			for(Method method : getAnnotatedMethods(this.getClass(), Command.class)) {
				final Command command = method.getAnnotation(Command.class);
				final Pattern pattern = Pattern.compile(sub.replace(command.value()), command.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE);
				Log.i(this + " - Added pattern %s for method %s", command.value(), method);
				this.patterns.put(pattern, method);
				if(command.allowPM()) {
					Log.i(this + " - Added PM pattern %s for method %s", command.value(), method);
					this.pmPatterns.put(pattern, method);
				}
			}
		} catch(NotFoundException | ClassNotFoundException | NoSuchMethodException e) {
			throw new ModuleInitException(e);
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

	protected Map<String, Style> styles() {
		return new HashMap<>();
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

	public boolean matches(Message message) {
		return (message.isPM() ? this.pmPatterns : this.patterns).keySet().stream().anyMatch(pattern -> pattern.matcher(message.getMessage()).matches());
	}

	public void processMessageAndDisplayResult(Message message) {
		final MessageResult result;
		try {
			result = this.processMessage(message);
		} catch(InvocationTargetException e) {
			message.respond("#coreerror %s while handling command: %s", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
			return;
		}
		if(result.data.isPresent()) {
			this.displayResult(result);
		}
	}

	public MessageResult processMessage(Message message) throws InvocationTargetException {
		if(!this.isEnabled()) {
			Log.i(this + " - Skipping, module disabled");
			return new MessageResult(message);
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
					// We let modules return an org.json.JSONObject just because it's easier, but we wrap it in a main.JSONObject if it happens
					if(!method.getReturnType().equals(Void.TYPE) && !org.json.JSONObject.class.isAssignableFrom(method.getReturnType())) {
						throw new ArgumentMismatchException(String.format("Method returns %s (should either be void or return a JSONObject)", method.getReturnType().getName()));
					}

					Style.pushOverrideMap(this.styles());
					try {
						Log.v("Invoking with %d args", args.length);
						Object o = method.invoke(this, args);
						if(o != null && o.getClass() == org.json.JSONObject.class) {
							o = new JSONObject((org.json.JSONObject)o);
						}
						return new MessageResult(message, method, (JSONObject)o);
					} catch(InvocationTargetException e) {
						// Unwrap RuntimeJSONException
						if(e.getTargetException() instanceof RuntimeJSONException) {
							e = new InvocationTargetException(((RuntimeJSONException)e.getTargetException()).getException());
						}

						Log.e(e);
						throw e;
					} catch(Exception e) {
						Log.e(e);
						return new MessageResult(message, method);
					} finally {
						Style.popOverrideMap();
					}
				} else {
					throw new ArgumentMismatchException(params.length == 0 ? "Method doesn't take the mandatory Message instance" : "Expected " + (params.length - 1) + " argument" + (params.length - 1 == 1 ? "" : "s") + "; found " + matcher.groupCount());
				}
			}
		}
		return new MessageResult(message);
	}

	private void displayResult(MessageResult result) {
		try {
			final Method method = result.handler.get();
			final JSONObject data = result.data.get();
			Optional<Method> view = Optional.empty();

			// If there's an error, we force use of a default handler
			if(!data.has("error")) {
				view = this.findBestView(method, getAnnotatedMethods(method.getDeclaringClass(), View.class));
			}

			// If we couldn't find a valid view (or didn't try because of an error condition), find the best default handler
			if(!view.isPresent()) {
				view = this.findBestView(method, getAnnotatedMethods(NoiseModule.class, View.class));
			}

			Style.pushOverrideMap(this.styles());
			try {
				view.get().invoke(this, result.message, data);
			} finally {
				Style.popOverrideMap();
				// Make sure any buffered responses are flushed
				try {
					result.message.flushResponses();
				} catch(IllegalStateException e) {} // Message wasn't buffering responses
			}
		} catch(Throwable e) {
			Log.e(e);
			if(e instanceof InvocationTargetException) {
				e = e.getCause();
			}
			result.message.respond("#error %s: %s", e.getClass().getSimpleName(), e.getMessage());
		}
	}

	private Optional<Method> findBestView(Method dataSource, Method[] views) {
		// We do this CSS style and look for the most specific view (ties are broken by file-order, which is the order 'views' is in)
		Method methodAndProtocolMatch = null, methodMatches = null, protocolMatches = null, wildMatch = null;
		for(Method m : views) {
			final View view = m.getAnnotation(View.class);
			final boolean flagMethodMatches = (view.method().length == 0) || Arrays.stream(view.method()).anyMatch(dataSource.getName()::equals);
			final boolean flagProtocolMatches = Arrays.stream(view.value()).anyMatch(this.bot.getProtocol()::equals);
			final boolean flagMethodWild = view.method().length == 0;
			final boolean flagProtocolWild = view.value().length == 0;

			if(flagMethodMatches && flagProtocolMatches) {
				if(methodAndProtocolMatch == null) {
					methodAndProtocolMatch = m;
				}
			} else if(flagMethodMatches && flagProtocolWild) {
				if(methodMatches == null) {
					methodMatches = m;
				}
			} else if(flagProtocolMatches && flagMethodWild) {
				if(protocolMatches == null) {
					protocolMatches = m;
				}
			} else if(flagMethodWild && flagProtocolWild) {
				if(wildMatch == null) {
					wildMatch = m;
				}
			}
		}

		// Choose the best match
		return Arrays.asList(new Method[] {methodAndProtocolMatch, methodMatches, protocolMatches, wildMatch}).stream().filter(m -> m != null).findFirst();
	}

	@View
	public void defaultView(Message message, JSONObject data) throws JSONException {
		if(data.has("error")) {
			message.respond("#error %s", data.getString("error"));
			return;
		}
		message.respond("#mono %s", data);
	}

	// No custom Slack changes needed yet for the default view (the error block styling is handled by SlackNoiseBot)
//	@View(Protocol.Slack)
//	public void defaultSlackView(Message message, JSONObject data) throws JSONException {}

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

	protected void triggerIfOwner(final Message message, final Runnable fn, final boolean errorOnFail) {
		this.bot.whois(message.getSender(), new WhoisHandler() {
			@Override public void onResponse() {
				if(NoiseModule.this.bot.isOwner(this.nick, this.hostname, this.account)) {
					fn.run();
				} else if(errorOnFail) {
					message.respond("#error Operation not permitted");
				}
			}

			@Override public void onTimeout() {
				if(errorOnFail) {
					message.respond("#error Unable to whois %s", message.getSender());
				}
			}
		});
	}

	private static Method[] getAnnotatedMethods(Class cls, Class... annotations) throws NotFoundException, ClassNotFoundException, NoSuchMethodException {
		// Get methods annotated with at least one of 'annotations', in file order
		final TreeSet<CtMethod> methods = new TreeSet<CtMethod>((first, second) -> Integer.compare(first.getMethodInfo().getLineNumber(0), second.getMethodInfo().getLineNumber(0)));
		final ClassPool cp = new ClassPool(); // We do this instead of ClassPool.getDefault() to avoid caching problems with reloaded modules
		cp.appendSystemPath();
		for(CtMethod ctmethod : cp.getCtClass(cls.getName()).getDeclaredMethods()) {
			for(Class annot : annotations) {
				if(ctmethod.hasAnnotation(annot)) {
					methods.add(ctmethod);
					break;
				}
			}
		}

		// Convert javassist types to standard reflection types. This is way more difficult than it should be
		final List<Method> rtn = new LinkedList<>();
		for(CtMethod ctmethod : methods) {
			final CtClass[] ctparams = ctmethod.getParameterTypes();
			final Class[] params = new Class[ctparams.length];
			for(int i = 0; i < ctparams.length; i++) {
				params[i] = ClassUtils.getClass(ctparams[i].getName());
			}
			rtn.add(cls.getMethod(ctmethod.getName(), params));
		}

		return rtn.toArray(new Method[0]);
	}
}
