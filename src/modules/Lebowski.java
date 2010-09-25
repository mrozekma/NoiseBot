package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Vector;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.*;

/**
 * Lebowski
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
	private List<Match> undisplayedMatches = null;
	private String lastMatchedUserMessage = null;
	
	@Override public void init(NoiseBot bot) {
		super.init(bot);
		try {
			final Vector<String> linesVec = new Vector<String>();
			final Scanner s = new Scanner(new File("lebowski"));
			while(s.hasNextLine()) {
				final String line = s.nextLine();
				if(!line.isEmpty()) {
					linesVec.add(line);
				}
			}
			this.lines = linesVec.toArray(new String[0]);
		} catch(FileNotFoundException e) {
			this.bot.sendNotice("No lebowski quotes file found");
		}
	}

	@Command(".*")
	public void lineReceived(Message message) {
		this.linesSinceLastQuote++;
	}

	// Single char at the beginning doesn't allow . to avoid matching commands
	// [a-zA-Z0-9,\\'\\\" !-][a-zA-Z0-9,\\'\\\"\\. !-]
	@Command("([^\\.].{" + (MIN_MESSAGE - 1) + "," + (PATTERN_MAX - 1) + "})")
	public void lebowski(Message message, String userMessage) {
		final Vector<Match> matches = new Vector<Match>();
		for(int lineNum = 0; lineNum < lines.length; lineNum++) {
			final String line = lines[lineNum];
			final int match = search(line.toLowerCase(), userMessage.toLowerCase(), TOLERANCE);
			if(match >= 0) {
				matches.add(new Match(line, lineNum, match));
			}
		}
		
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
		if(this.lastLineMatched >= 0) {
			if(this.undisplayedMatches != null) // Should always be true
				this.undisplayedMatches.clear();
			this.bot.sendMessage(COLOR_QUOTE + this.lines[++this.lastLineMatched]);
		} else {
			this.bot.sendMessage(COLOR_ERROR + "No matches yet");
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
		
//		return result;
		return -1;
	}
	
	@Override public String getFriendlyName() {return "Lebowski";}
	@Override public String getDescription() {return "Outputs Big Lebowski quotes similar to user text";}
	@Override public String[] getExamples() {
		return new String[] {
				"Where's the money, Lebowski?",
				"You're entering a world of pain",
				".next -- Outputs the next quote after the one most recently said"
		};
	}
	@Override public String getOwner() {return "Morasique";}
}
