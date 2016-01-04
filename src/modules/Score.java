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
import java.util.stream.Stream;

import main.*;

/**
 * Score
 *
 * @author Arathald (Greg Jackson) Created January 10, 2012.
 * @author Will Fuqua February 09, 2013.
 */
public class Score extends NoiseModule implements Serializable {
	private static final Style STYLE_POSITIVE = Style.GREEN;
	private static final Style STYLE_NEGATIVE = Style.RED;

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

	private enum When {
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
		// Populate 'scores' from 'diskScores'
		for(ScoreChange score : this.diskScores) {
			this.scores.put(score.when, score);
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

	@Command("\\.score(?: @(day|week|month|year|ever))? (.+)")
	public void getScore(Message message, String whenStr, String nick) {
		final When when = (whenStr == null || whenStr.isEmpty()) ? When.ever : When.valueOf(whenStr);
		final Map<String, Integer> totals = this.getTotals(nick, when);
		final int total = totals.getOrDefault(nick, 0);

		message.buildResponse().add("%s: ", nick).add(total >= 0 ? STYLE_POSITIVE : STYLE_NEGATIVE, "%s", "" + total).send();
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

	@Command(value = "\\.(?:scores|scoreboard)(?: @(day|week|month|year|ever))?", allowPM = true)
	public void scores(Message message, String whenStr) {
		final List<String> nicks = Arrays.asList(this.bot.getNicks());
		final When when = (whenStr == null || whenStr.isEmpty()) ? When.ever : When.valueOf(whenStr);
		final MessageBuilder messageBuilder = message.buildResponse();
		final MessageBuilder.PartBuilder partBuilder = messageBuilder.buildParts(", ", "%s: %s");

		this.getTotals(null, when).entrySet().stream()
			.filter(e -> Math.abs(e.getValue()) > 2 || nicks.contains(e.getKey()))
			.sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
			.forEach(e -> partBuilder.addPart(new String[] {e.getKey(), "" + e.getValue()}, new Style[] {null, e.getValue() >= 0 ? STYLE_POSITIVE : STYLE_NEGATIVE}));

		if(partBuilder.isEmpty()) {
			message.respond("%s", "No scores available");
		} else {
			partBuilder.done();
			messageBuilder.send();
		}
	}

	@Command(value = "\\.([dwmye])(?:scores|scoreboard)", allowPM = true)
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
