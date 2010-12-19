package debugging;

import java.util.HashMap;
import java.util.Map;

/**
 * Level
 *
 * @author Michael Mrozek
 *         Created Dec 19, 2010.
 */
public enum Level {
	DEBUG, VERBOSE, INFO, WARN, ERROR;

	private static Map<Character, Level> codes = new HashMap<Character, Level>();
	static {
		for(Level level : values()) {
			codes.put(Character.toUpperCase(level.toString().charAt(0)), level);
		}
		assert(codes.size() == Level.values().length) : "Every logging level must start with a unique character";
	}
	
	public static Level codeToLevel(char c) {
		return codes.containsKey(c) ? codes.get(c) : null;
	}
}
