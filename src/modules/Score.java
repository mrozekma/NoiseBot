package modules;

import static org.jibble.pircbot.Colors.GREEN;
import static org.jibble.pircbot.Colors.NORMAL;
import static org.jibble.pircbot.Colors.RED;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.stream.Collectors;

import main.Message;
import main.ModuleInitException;
import main.NoiseBot;
import main.NoiseModule;
import panacea.Condition;
import panacea.MapFunction;
import panacea.Panacea;
import panacea.ReduceFunction;

import static panacea.Panacea.*;

/**
 * Score
 *
 * @author Arathald (Greg Jackson) Created January 10, 2012.
 * @author Will Fuqua February 09, 2013.
 */
public class Score extends NoiseModule implements Serializable {
	private static final String COLOR_POSITIVE = GREEN;
	private static final String COLOR_NEGATIVE = RED;

	// This is the old deprecated store. init() will convert data in it to the new store and clear it

	@Deprecated
	private Map<String, ScoreEntry> userScores = new HashMap<String, ScoreEntry>();

	@Deprecated
	public static class ScoreEntry implements Comparable<ScoreEntry>, Serializable {
		public String username;
		public int score;

		public ScoreEntry() {}

		public ScoreEntry(String username, int score) {
			this.username = username;
			this.score = score;
		}

		@Override
		public int compareTo(ScoreEntry o) {
			return this.score - o.score;
		}

		public String ircFormat() {
			String scoreColor = (this.score >= 0 ? Score.COLOR_POSITIVE	: Score.COLOR_NEGATIVE);
			return this.username  + ": " + scoreColor + this.score + NORMAL;
		}
	}

	// This is the new store

	private static class ScoreChange implements Serializable {
		public final String user;
		public final String target;
		public final Date when;
		public final int change;

		public ScoreChange(String user, String target, Date when, int change) {
			this.user = user;
			this.target = target;
			this.when = when;
			this.change = change;
		}
	}

	private static enum When {
		day, week, month, year, ever;

		public static When getByLetter(char c) {
			for(When w : When.values()) {
				if(w.toString().charAt(0) == c) {
					return w;
				}
			}
			throw new IllegalArgumentException(String.format("No `When' starts with `%c'", c));
		}
	}

	// Date of score change => Change
	// gson can't serialize this correctly, so 'diskScores' is the one actually written to disk
	private transient NavigableMap<Date, ScoreChange> scores = new TreeMap<>();
	private ScoreChange[] diskScores = null;

	@Override public void init(final NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		if(diskScores == null) {
			// Convert old data
			final Date now = new Date();
			final Calendar when = new Calendar.Builder().setDate(now.getYear() + 1900, now.getMonth(), now.getDate()).setTimeOfDay(0, 0, 0).build();
			for(ScoreEntry e : this.userScores.values()) {
				this.scores.put(when.getTime(), new ScoreChange(null, e.username, when.getTime(), e.score));
				when.add(Calendar.SECOND, 1);
			}
			this.userScores.clear();
			this.save();
		} else {
			// Populate 'scores' from 'diskScores'
			for(ScoreChange score : this.diskScores) {
				this.scores.put(score.when, score);
			}
		}
	}

	@Override public boolean save() {
		// Populate 'diskScores' from 'scores'
		this.diskScores = this.scores.values().toArray(new ScoreChange[0]);
		return super.save();
	}

	@Command(".*\\b([a-zA-Z0-9_]{3,16})\\+\\+.*")
	public void incrementScore(Message message, String target) {
		this.changeScore(message.getSender(), target, 1);
	}

	@Command(".*\\b([a-zA-Z0-9_]{3,16})\\-\\-.*")
	public void decrementScore(Message message, String target) {
		this.changeScore(message.getSender(), target, -1);
	}

	private void changeScore(String sender, String target, int amount) {
		final Calendar when = new GregorianCalendar();
		// Just in case multiple score changes come in at the same second
		while(this.scores.containsKey(when.getTime())) {
			when.add(Calendar.SECOND, 1);
		}
		this.scores.put(when.getTime(), new ScoreChange(sender, target, when.getTime(), amount));
		this.save();
	}

	private Map<String, Integer> getTotals(String forUser, When sinceWhen) {
		Calendar since = new GregorianCalendar();

		switch(sinceWhen) {
		case day:
			since.add(Calendar.DAY_OF_YEAR, -1);
			break;
		case week:
			since.add(Calendar.WEEK_OF_YEAR, -1);
			break;
		case month:
			since.add(Calendar.MONTH, -1);
			break;
		case year:
			since.add(Calendar.YEAR, -1);
			break;
		case ever:
			since = null;
			break;
		}

		final Date sinceDate = (since == null) ? null : since.getTime();
		final Map<String, Integer> totals = new TreeMap<String, Integer>();
		for(Map.Entry<Date, ScoreChange> e : this.scores.entrySet()) {
			final String user = e.getValue().target;
			if((forUser == null || forUser.equals(user)) && (sinceDate == null || sinceDate.before(e.getKey()))) {
				totals.put(user, totals.getOrDefault(user, 0) + e.getValue().change);
			}
		}

		return totals;
	}

	private String formatScore(String user, int score) {
		return String.format("%s: %s%d%s", user, (score >= 0) ? COLOR_POSITIVE : COLOR_NEGATIVE, score, NORMAL);
	}

	@Command("\\.score(?: @(day|week|month|year|ever))? (.+)")
	public void getScore(Message message, String whenStr, String nick) {
		final When when = (whenStr == null || whenStr.isEmpty()) ? When.ever : When.valueOf(whenStr);
		final Map<String, Integer> totals = this.getTotals(nick, when);
		this.bot.respondParts(message, this.formatScore(nick, totals.getOrDefault(nick, 0)));
	}

	@Command("\\.score(?: @(day|week|month|year|ever))?")
	public void getSelfScore(Message message, String whenStr) {
		this.getScore(message, whenStr, message.getSender());
	}

	@Command("\\.([dwmye])score (.+)")
	public void getScoreWhen(Message message, String when, String nick) {
		this.getScore(message, When.getByLetter(when.charAt(0)).toString(), nick);
	}

	@Command("\\.([dwmye])score")
	public void getSelfScoreWhen(Message message, String when) {
		this.getSelfScore(message, When.getByLetter(when.charAt(0)).toString());
	}

	@Command("\\.(?:scores|scoreboard)(?: @(day|week|month|year|ever))?")
	@PM("\\.(?:scores|scoreboard)(?: @(day|week|month|year|ever))?")
	public void scores(Message message, String whenStr) {
		final List<String> nicks = Arrays.asList(this.bot.getNicks());
		final When when = (whenStr == null || whenStr.isEmpty()) ? When.ever : When.valueOf(whenStr);
		//TODO |score| > 2 || nicks.contains(user)
		final String[] parts = this.getTotals(null, when).entrySet().stream()
			.filter(e -> Math.abs(e.getValue()) > 2 || nicks.contains(e.getKey()))
			.sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
			.map(e -> this.formatScore(e.getKey(), e.getValue()))
			.toArray(String[]::new);

		if(parts.length == 0) {
			this.bot.respond(message, "No scores available");
		} else {
			this.bot.respondParts(message, ", ", parts);
		}
	}

	@Command("\\.([dwmye])(?:scores|scoreboard)")
	@PM("\\.([dwmye])(?:scores|scoreboard)")
	public void scoresWhen(Message message, String when) {
		this.scores(message, When.getByLetter(when.charAt(0)).toString());
	}

	@Override
	public String getFriendlyName() {
		return "Score";
	}

	@Override
	public String getDescription() {
		return "Keeps users' scores";
	}

	@Override
	public String[] getExamples() {
		final List<String> rtn = new Vector<String>() {{
			add("_nick_++ -- Increment _nick_'s score");
			add("_nick_-- -- Decrement _nick_'s score");
			add(".scores || .scoreboard -- Display all scores");
			add(".score _nick_ -- Display _nick_'s score");
			for(When w : When.values()) {
				if(w == When.ever) {continue;}
				final String wStr = w.toString();
				add(String.format(".%cscore _nick_ || .score @%s _nick_ -- Display _nick_'s score for the last %s", wStr.charAt(0), wStr, wStr));
			}
		}};
		return rtn.toArray(new String[0]);
	}
}
