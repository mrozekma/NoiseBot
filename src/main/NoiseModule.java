package main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jibble.pircbot.User;


/**
 * NoiseModule
 *
 * @author Michael Mrozek
 *         Created Jun 13, 2009.
 */
public abstract class NoiseModule implements Comparable<NoiseModule> {
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
	
	protected transient NoiseBot bot;
	protected transient Map<Pattern, Method> patterns = new HashMap<Pattern, Method>();
	protected transient Map<Pattern, Method> pmPatterns = new HashMap<Pattern, Method>();
	
	public void init(NoiseBot bot) {
		this.bot = bot;
		
		for(Method method : this.getClass().getDeclaredMethods()) {
			final Command command = method.getAnnotation(Command.class);
			if(command != null) {
				final Pattern pattern = Pattern.compile(command.value());
				System.out.println(this + " - Added pattern " + command.value() + " for method " + method);
				this.patterns.put(pattern, method);
			}

			final PM pm = method.getAnnotation(PM.class);
			if(pm != null) {
				final Pattern pattern = Pattern.compile(pm.value());
				System.out.println(this + " - Added PM pattern " + pm.value() + " for method " + method);
				this.pmPatterns.put(pattern, method);
			}
		}
	}
	
	public void unload() {}
	
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
	
	public String getOwner() {return ".*";}
	
	// Doesn't show in the help listing, and only I can trigger
	public boolean isPrivate() {return false;}
	
	public Pattern[] getPatterns() {return this.patterns.keySet().toArray(new Pattern[0]);}
	
	public void processMessage(Message message) {
		System.out.println(this + " - Processing message: " + message);
		for(Pattern pattern : (message.isPM() ? this.pmPatterns : this.patterns).keySet()) {
			System.out.println("Trying pattern: " + pattern);
			final Matcher matcher = pattern.matcher(message.getMessage());
			if(matcher.matches()) {
				System.out.println("Matched");
				final Method method = (message.isPM() ? this.pmPatterns : this.patterns).get(pattern);
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
						System.out.println("Invoking with " + args.length + " args");
						method.invoke(this, args);
					} catch(Exception e) {
						e.printStackTrace();
					}
				} else {
					throw new ArgumentMismatchException(params.length == 0 ? "Method doesn't take the mandatory Message instance" : "Expected " + (params.length - 1) + " argument" + (params.length - 1 == 1 ? "" : "s") + "; found " + matcher.groupCount());
				}
			}
		}
	}
	
	public boolean save() {
		for(Class iface : this.getClass().getInterfaces()) {
			if(iface == Serializable.class) {
				try {
					new ObjectOutputStream(new FileOutputStream(new File("store", this.getClass().getSimpleName()))).writeObject(this);
				} catch(IOException e) {
					e.printStackTrace();
					return false;
				}
				return true;
			}
		}
		
		return true;
	}

	@Override public int compareTo(NoiseModule other) {
		return this.getFriendlyName().compareTo(other.getFriendlyName());
	}
}
