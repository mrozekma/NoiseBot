package debugging;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import main.NoiseBot;

import org.jibble.pircbot.Colors;

import static panacea.Panacea.*;

/**
 * Parser
 *
 * @author Michael Mrozek
 *         Created Dec 19, 2010.
 */
public class Parser {
	private static class ParserException extends RuntimeException {
		public ParserException(String msg) {super(msg);}
	}
	
	public static void parse(Client client, String line) {
		if(line.isEmpty()) {return;}
		
		final String[] parts = line.split(" ");
		final String command = parts[0];
		final String[] args = new String[parts.length - 1];
		System.arraycopy(parts, 1, args, 0, args.length);
		
		Method method;
		try {
			method = Parser.class.getDeclaredMethod("parse" + Character.toUpperCase(command.charAt(0)) + command.substring(1), Client.class, String[].class);
			method.invoke(null, client, args);
		} catch(NoSuchMethodException e) {
			Debugger.me.out(client, "Unknown command `" + command + "'");
		} catch(InvocationTargetException e) {
			Debugger.me.out(client, e.getCause().getMessage());
		} catch(Exception e) {}
	}
		
	private static void parseEcho(Client client, String[] args) {
		final Map<String, Set<Level>> levels = new HashMap<String, Set<Level>>();
		
		if(args.length == 1 && args[0].equals("-")) {
			client.setLevels(levels);
			return;
		}
		
		for(String arg : args) {
			if(!arg.contains(":")) {throw new ParserException("Expected colon in argument: " + arg);}
			
			int colon = arg.lastIndexOf(':');
			final String name = arg.substring(0, colon);
			final String levelStr = arg.substring(colon+1);
			
			if(levels.containsKey(name)) {throw new ParserException("`" + name + "' specified twice");}
			
			final Set<Level> levelSet = new HashSet<Level>();
			for(int i = 0; i < levelStr.length(); i++) {
				final char c = Character.toUpperCase(levelStr.charAt(i));
				if(c == '*') {
					levelSet.addAll(Arrays.asList(Level.values()));
				} else {
					final Level level = Level.codeToLevel(c);
					if(level == null) {throw new ParserException("Unknown level character " + c);}
					levelSet.add(level);
				}
			}
			
			levels.put(name, levelSet);
		}
		
		client.setLevels(levels);
	}
	
	private static void parseHistory(Client client, String[] args) {
		Debugger.me.out(client, Debugger.me.log);
	}
	
	private static void parseSay(Client client, String[] args) {
		NoiseBot.me.sendMessage(implode(args, " "));
	}
	
	private static void parseQuit(Client client, String[] args) {
		String msg = "Bot disconnected by debugger";
		if(args.length > 0) {
			msg += ": " + Colors.BLUE + implode(args, " ");
		}
		NoiseBot.me.sendNotice(msg);
		sleep(2);
		NoiseBot.me.quit();
	}
	
	private static void parseAuth(Client client, String[] args) {Debugger.me.out(client, "Already authorized by " + client.getNick());}
	private static void parseCode(Client client, String[] args) {Debugger.me.out(client, "Already authorized by " + client.getNick());}
}
