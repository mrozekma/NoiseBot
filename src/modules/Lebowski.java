package modules;

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

import main.*;
import org.json.JSONArray;
import org.json.JSONException;

import static main.Utilities.getRandomInt;
import static main.Utilities.substring;

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
	private static class RateLimiter {
		private Map<String,List<Long>> users;

		public RateLimiter() {
			this.users = new HashMap<>();
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

	static {
		assert MIN_MESSAGE > TOLERANCE : "Messages must be longer than the number of errors allowed";
	}

	private String[] lines;
	private String lastNick = "";
	private int lastNickMatches = 0;
	private int lastLineMatched = -1;
	private int linesSinceLastQuote = SPACER_LINES;
	private RateLimiter limiter = null;
	private JSONArray undisplayedMatches = null;
	private String lastMatchedUserMessage = null;

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		this.limiter = new RateLimiter();
		this.lines = new String[0];
		try {
			final Vector<String> linesVec = new Vector<>();
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

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("quote", Style.CYAN);
			put("quoteexact", Style.CYAN.update("underline"));
			put("count", Style.CYAN.update("italic"));
		}};
	}

	@Command(value = ".*fascist.*", allowPM = false)
	public JSONObject fascist(CommandContext ctx) throws JSONException {
		return this.lebowski(ctx, "fucking fascist");
	}

	@Command(value = ".*shut the fuck up.*", allowPM = false)
	public JSONObject shutTheFuckUp(CommandContext ctx) throws JSONException {
		return this.lebowski(ctx, "shut the fuck up");
	}

	// Single char at the beginning doesn't allow . to avoid matching commands
	// [a-zA-Z0-9,\\'\\\" !-][a-zA-Z0-9,\\'\\\"\\. !-]
	@Command(value = "([^\\.].{" + (MIN_MESSAGE - 1) + "," + (PATTERN_MAX - 1) + "})", allowPM = false)
	public JSONObject lebowski(CommandContext ctx, String userMessage) throws JSONException {
		Log.i("Lebowski: Searching for matches for \"%s\"", userMessage);

		final JSONObject rtn = new JSONObject().put("message", userMessage);
		for(int lineNum = 0; lineNum < lines.length; lineNum++) {
			final String line = lines[lineNum];
			final int match = search(line.toLowerCase(), userMessage.toLowerCase(), TOLERANCE);
			if(match >= 0) {
				rtn.append("matches", new JSONObject().put("line", line).put("line_num", lineNum).put("offset", match));
			}
		}

		Log.v("Matches: %d", rtn.has("matches") ? rtn.getJSONArray("matches").length() : 0);
		return rtn;
	}

	@View
	public void view(ViewContext ctx, JSONObject data) throws JSONException {
		this.linesSinceLastQuote++;

		if(!data.has("matches")) {return;}
		final JSONArray matches = data.getJSONArray("matches");
		if(matches.length() == 0) {return;}

		if(this.linesSinceLastQuote < SPACER_LINES) {
			return;
		} else if(this.lastNick.equalsIgnoreCase(ctx.getMessageSender())) {
			if(++this.lastNickMatches > MAX_CONSECUTIVE_MATCHES) {
				return;
			}
		} else {
			this.lastNick = ctx.getMessageSender();
			this.lastNickMatches = 0;
			this.linesSinceLastQuote = 0;
		}

		final int idx = getRandomInt(0, matches.length() - 1);
		final org.json.JSONObject match = matches.getJSONObject(idx);
		this.lastLineMatched = match.getInt("line_num");
		this.undisplayedMatches = matches;
		this.undisplayedMatches.remove(idx);
		this.lastMatchedUserMessage = data.getString("message");

		final MessageBuilder builder = ctx.buildResponse();
		this.renderMatch(builder, match, matches.length(), data.getString("message"));
		builder.send();
	}

	@Command("\\.next") public void nextLine(CommandContext ctx) {
		if(this.lastLineMatched < 0) {
			ctx.respond("#error No matches yet");
		} else if(this.lastLineMatched+1 == this.lines.length) {
			ctx.respond("#error Out of lines");
		} else {
			this.undisplayedMatches = null;
			if(this.limiter.isAllowed(ctx.getMessageSender())) {
				ctx.respond("#quote %s", this.lines[++this.lastLineMatched]);
			} else {
				Slap.slap(this.bot, ctx);
			}
		}
	}

	@Command("\\.other") public void other(CommandContext ctx) throws JSONException {
		if(this.lastLineMatched < 0 || this.undisplayedMatches == null) {
			ctx.respond("#error No matches yet");
		} else if(this.undisplayedMatches.length() == 0) {
			ctx.respond("#error No other matches to display");
		} else {
			final int idx = getRandomInt(0, this.undisplayedMatches.length() - 1);
			final org.json.JSONObject match = this.undisplayedMatches.getJSONObject(idx);
			this.lastLineMatched = match.getInt("line_num");
			this.undisplayedMatches.remove(idx);

			final MessageBuilder builder = ctx.buildResponse();
			this.renderMatch(builder, match, 1, this.lastMatchedUserMessage);
			builder.send();
		}
	}

	private void renderMatch(MessageBuilder builder, org.json.JSONObject match, int matches, String userMessage) throws JSONException {
		if(matches > 1) {
			builder.add("#count (%d) ", new Object[] {matches});
		}
		builder.add("%(#quote)s%(#quoteexact)s%(#quote)s", new Object[] {
				match.getString("line").substring(0, match.getInt("offset")),
				substring(match.getString("line"), match.getInt("offset"), userMessage.length()),
				match.getString("line").substring(match.getInt("offset") + userMessage.length())
		});
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
