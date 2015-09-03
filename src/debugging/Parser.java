package debugging;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import main.NoiseBot;
import main.NoiseModule;
import static main.Utilities.sleep;

import org.jibble.pircbot.Colors;

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

	private static void parseEchoIn(Client client, String[] args) {
		Debugger.me.out(client, Debugger.me.in);
	}

	private static void parseEchoOut(Client client, String[] args) {
		Debugger.me.out(client, Debugger.me.out);
	}

	private static void parsePing(Client client, String[] args) {
		Debugger.me.out(client, "pong");
	}

	private static void parseHistory(Client client, String[] args) {
		Debugger.me.out(client, Debugger.me.log);
	}

	private static void parseSay(Client client, String[] args) {
		client.getBot().sendMessage(Arrays.stream(args).collect(Collectors.joining(" ")));
	}

	private static void parsePattern(Client client, String[] args) {
		final Map<String, NoiseModule> modules = client.getBot().getModules();
		final NoiseModule[] modulesToTest;
		final String testPattern;
		if(args[0].startsWith(":")) { // Specific module
			final String moduleName = args[0].substring(1);
			{
				String[] remainingArgs = new String[args.length - 1];
				System.arraycopy(args, 1, remainingArgs, 0, remainingArgs.length);
				testPattern = Arrays.stream(remainingArgs).collect(Collectors.joining(" "));
			}

			if(!modules.containsKey(moduleName)) {
				Debugger.me.out(client, "No loaded module named `" + moduleName + "'");
				return;
			}

			final NoiseModule module = modules.get(moduleName);
			modulesToTest = new NoiseModule[] {module};
		} else {
			modulesToTest = modules.values().toArray(new NoiseModule[0]);
			testPattern = Arrays.stream(args).collect(Collectors.joining(" "));
		}

		Debugger.me.out(client, "Testing " + modulesToTest.length + " modules");
		nextModule:
		for(NoiseModule module : modulesToTest) {
			for(Pattern pattern : module.getPatterns()) {
				if(pattern.matcher(testPattern).matches()) {
					Debugger.me.out(client, "" + module);
					continue nextModule;
				}
			}
		}
	}

	private static void parseQuit(Client client, String[] args) {
		String msg = "Bot disconnected by debugger";
		if(args.length > 0) {
			msg += ": " + Colors.BLUE + Arrays.stream(args).collect(Collectors.joining(" "));
		}
		client.getBot().sendNotice(msg);
		sleep(2);
		client.getBot().quit(0);
	}

	private static void parseAuth(Client client, String[] args) {Debugger.me.out(client, "Already authorized by " + client.getNick());}
	private static void parseCode(Client client, String[] args) {Debugger.me.out(client, "Already authorized by " + client.getNick());}
}
