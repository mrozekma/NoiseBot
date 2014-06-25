package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;

import debugging.Log;

import main.Message;
import main.ModuleLoadException;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.*;
import static modules.Slap.slapUser;

/**
 *                                  _____
 *                          __.---''    ''--
 *                 ___..---'         \       \"---_
 *             .-''      -            .    \  \  \  \
 *          _.'           `,..___.----./    \  \      \
 *        .'  -         .-'            (        \      \
 *      .'     ,_.-----'                |               \
 *     /   ' .'                          \   \      .__   \
 *     |  /  .                            \   \       ""\. \._
 *     | |   /                             \    \         \   \._
 *     | |   |                        ___...\    |         \     `-.
 *    /  /   |                  _.--''       |   \                  `.
 *    |  |   |              _.-//////,       |      \                 '.
 *    / \ \  |   ___...--.==|/////////       /      ||     \           \
 *       |   .-///,       |-|////////       |       ||      \     \     '.
 *       | .'//////       |  \'''''         |   |   ||             '.     '
 *       | |///////       |   \            .|   |    '\              \     '
 *      /  |'////'        /    '--.____.--' |   \      \          \        |
 *     |  | \            /     '  \         |    |      \          \.     |
 *     |  |  \_        .//      ' - -.       \    \          \.      \    |
 *     |  |    ""----"" (       .--../        \     \          \.     \. .^.
 *     /    |    |      \_.--._/. - _- _        \                "-.    \___'
 *     |  |  \    \       . -_-_-_-__ - _ .       \     \           \      /
 *      \|         |    .'_ -  ______''-__ '       \     \     \         __/
 *       \      \   \  .'-.==**""""""**=._'  \      \     '\     \\        /
 *         \ \       \ '="   .- - --.    "" /  \     '\     \      ""   ____/
 *            \  \    \  /  '|.| |'.-      /     \ '    \                 _.'
 *           | |       \ /     /| \ .     /    \  \      |\___         --'/.
 *          / /    \ |  / '/ / /     ''--'       \ \     \  ______.'  ___/ \-.
 *              /  / /  / / /   |   \        \  \  \   . '\          __./   \ '-.
 *              \ \\   / / / /           \       \\.'      -------'''  /    |   '.
 *              /  \ \  / // / |  \ \   \     \ \ \                     /    |     \
 *                  ) )  / / / / \  \  \  \ \   \                      /     /      |__
 *                 // /   / / | \| \ \ \  \  \                         /     /      |  '-.
 *                 ( (_    ./ / \ ` ` `                               /     /       /     '\
 *                       ./  /   |    `                               /     /       /
 *                     ./  /    /|      `                            /     /       /
 *                   ./   /    / |        `                          /     /      /
 *                 ./   /     /  |                                .-/     /       /
 *               ./    /      /  |                              .' /     /       /
 *             ./     /      /   |                           .-'  /     /       /
 *            /      /      /     \                       .-'     /     /       /
 *           /      /       /      \                   .-'       /     /       /
 *          /      /       /        \              ..-'         /     /       /
 *         /       /       /          \       ..-''            /     /       /
 *        /        /       /            \_.-''                /     /       /
 *
 * @author Michael Mrozek
 *         Created Jun 18, 2009.
 */
public class Lebowski extends NoiseModule {
	private static class Match {
		private String line;
		private int lineNum;
		private int pos;

		public Match(String line, int lineNum, int pos) {
			this.line = line;
			this.lineNum = lineNum;
			this.pos = pos;
		}

		public String getLine() {return this.line;}
		public int getLineNum() {return this.lineNum;}
		public int getPos() {return this.pos;}
	}

	private static class RateLimiter {
		private Map<String,List<Long>> users;

		public RateLimiter() {
			this.users = new HashMap<String,List<Long>>();
		}

		public boolean isAllowed(String user) {
			if(!users.containsKey(user))
				users.put(user, new LinkedList());

			// Calculate probability of allowing the request
			// This could be made configurable..
			double prob = 1;
			for(Long date : users.get(user)) {
				long now = System.nanoTime();
				double age = 1E-9*(now - date);
				prob *= 1.0 - 1.0/(age/60.0+5.0);
			}

			// Log the request
			users.get(user).add(System.nanoTime());

			return Math.random() < prob;
		}
	}

	private static File TRANSCRIPT_FILE = NoiseBot.getDataFile("lebowski");

	private static final int CHAR_MAX = 127;
	private static final int PATTERN_MAX = 31;

	private static final int TOLERANCE = 3;
	private static final int MIN_MESSAGE = 8;
	private static final int MAX_CONSECUTIVE_MATCHES = 3;
	private static final int SPACER_LINES = 30;

	private static final String COLOR_QUOTE = CYAN;
	private static final String COLOR_ERROR = RED;

	static {
		assert MIN_MESSAGE > TOLERANCE : "Messages must be longer than the number of errors allowed";
	}

	private String[] lines;
	private String lastNick = "";
	private int lastNickMatches = 0;
	private int lastLineMatched = -1;
	private int linesSinceLastQuote = SPACER_LINES;
	private RateLimiter limiter = null;
	private List<Match> undisplayedMatches = null;
	private String lastMatchedUserMessage = null;

	@Override public void init(NoiseBot bot, Map<String, String> config) throws ModuleLoadException {
		super.init(bot, config);
		this.limiter = new RateLimiter();
		this.lines = new String[0];
		try {
			final Vector<String> linesVec = new Vector<String>();
			// I'd love to know why Scanner can't read this file...
			final BufferedReader r = new BufferedReader(new FileReader(TRANSCRIPT_FILE));
			String line;
			while((line = r.readLine()) != null) {
				if(!line.isEmpty()) {
					linesVec.add(line);
				}
			}
			this.lines = linesVec.toArray(new String[0]);
			Log.i("Loaded lebowski file: %d", this.lines.length);
		} catch(FileNotFoundException e) {
			this.bot.sendNotice("No lebowski quotes file found");
		} catch(IOException e) {
			this.bot.sendNotice("Problem reading quotes file: " + e.getMessage());
		}
	}

	@Command(".*fascist.*")
	public void fascist(Message message) {
		this.lebowski(message, "fucking fascist");
	}

	@Command(".*shut the fuck up.*")
	public void shutTheFuckUp(Message message) {
		this.lebowski(message, "shut the fuck up");
	}

	// Single char at the beginning doesn't allow . to avoid matching commands
	// [a-zA-Z0-9,\\'\\\" !-][a-zA-Z0-9,\\'\\\"\\. !-]
	@Command("([^\\.].{" + (MIN_MESSAGE - 1) + "," + (PATTERN_MAX - 1) + "})")
	public void lebowski(Message message, String userMessage) {
		Log.i("Lebowski: Searching for matches for \"%s\"", userMessage);
		this.linesSinceLastQuote++;

		final Vector<Match> matches = new Vector<Match>();
		for(int lineNum = 0; lineNum < lines.length; lineNum++) {
			final String line = lines[lineNum];
			final int match = search(line.toLowerCase(), userMessage.toLowerCase(), TOLERANCE);
			if(match >= 0) {
				matches.add(new Match(line, lineNum, match));
			}
		}

		Log.v("Matches: %d", matches.size());

		if(!matches.isEmpty()) {
			if(this.linesSinceLastQuote < SPACER_LINES) {
				return;
			} else if(this.lastNick.equalsIgnoreCase(message.getSender())) {
				if(++this.lastNickMatches > MAX_CONSECUTIVE_MATCHES) {return;}
			} else {
				this.lastNick = message.getSender();
				this.lastNickMatches = 0;
				this.linesSinceLastQuote = 0;
			}

			final Match match = matches.get(getRandomInt(0, matches.size() - 1));
			this.lastLineMatched = match.getLineNum();
			this.bot.sendMessage(renderMatch(match, matches.size(), userMessage));

			this.undisplayedMatches = matches;
			this.undisplayedMatches.remove(match);
			this.lastMatchedUserMessage = userMessage;
		}
	}

	@Command("\\.next") public void nextLine(Message message) {
		if(this.lastLineMatched < 0) {
			this.bot.sendMessage(COLOR_ERROR + "No matches yet");
		} else if(this.lastLineMatched+1 == this.lines.length) {
			this.bot.sendMessage(COLOR_ERROR + "Out of lines");
		} else {
			if(this.undisplayedMatches != null) // Should always be true
				this.undisplayedMatches.clear();
			if(this.limiter.isAllowed(message.getSender()))
				this.bot.sendMessage(COLOR_QUOTE + this.lines[++this.lastLineMatched]);
			else
				this.bot.sendAction(slapUser(message.getSender()));
		}
	}

	@Command("\\.other") public void other(Message message) {
		if(this.lastLineMatched < 0 || this.undisplayedMatches == null) {
			this.bot.sendMessage(COLOR_ERROR + "No matches yet");
		} else if(this.undisplayedMatches.isEmpty()) {
			this.bot.sendMessage(COLOR_ERROR + "No other matches to display");
		} else {
			final Match match = this.undisplayedMatches.get(getRandomInt(0, this.undisplayedMatches.size() - 1));
			this.lastLineMatched = match.getLineNum();
			this.undisplayedMatches.remove(match);
			this.bot.sendMessage(renderMatch(match, 1, this.lastMatchedUserMessage));
		}
	}

	private static String renderMatch(Match match, int matches, String userMessage) {
		final StringBuffer b = new StringBuffer();
		if(matches > 1) {b.append("(").append(matches).append(") ");}
		b.append(COLOR_QUOTE);
		if(match.getPos() > 0) {b.append(match.getLine().substring(0, match.getPos()));}
		b.append(UNDERLINE);
		b.append(substring(match.getLine(), match.getPos(), userMessage.length()));
		b.append(NORMAL).append(COLOR_QUOTE);
		if(match.getPos() + userMessage.length() < match.getLine().length()) {b.append(match.getLine().substring(match.getPos() + userMessage.length()));}
		return b.toString();
	}

	// http://en.wikipedia.org/wiki/Bitap_algorithm
	private static int search(String text, String pattern, int k) {
		final int m = pattern.length();
		final long[] R = new long[k + 1];
		final long[] patternMask = new long[CHAR_MAX + 1];

		if(m == 0) {return 0;} // return text
		if(m > PATTERN_MAX) {throw new IllegalArgumentException("Pattern is too long");}

		for(int i =0; i <= k; i++) {R[i] = ~1;}
		for(int i = 0; i <= CHAR_MAX; i++) {patternMask[i] = ~0;}
		for(int i = 0; i < m; i++) {patternMask[pattern.charAt(i)] &= ~(1 << i);}

		try {
			for(int i = 0; i < text.length(); i++) {
				long oldRd1 = R[0];
				R[0] |= patternMask[text.charAt(i)];
				R[0] <<= 1;

				for(int d = 1; d <= k; d++) {
					long tmp = R[d];
					R[d] = (oldRd1 & (R[d] | patternMask[text.charAt(i)])) << 1;
					oldRd1 = tmp;
				}

				if(0 == (R[k] & (1 << m))) {
	//				result = text.substring(i - m + 1);
					return i - m + 1;
				}
			}
		} catch(Exception e) {}

//		return result;
		return -1;
	}

	@Override public String getFriendlyName() {return "Lebowski";}
	@Override public String getDescription() {return "Outputs Big Lebowski quotes similar to user text";}
	@Override public String[] getExamples() {
		return new String[] {
				"Where's the money, Lebowski?",
				"You're entering a world of pain",
				".next -- Outputs the next quote after the one most recently said",
				".other -- Outputs another quote that matches the last pattern"
		};
	}
}
