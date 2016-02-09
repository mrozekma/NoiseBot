package modules;

import java.io.Serializable;
import java.util.*;

import debugging.Log;
import main.*;
import org.json.JSONException;

/**
 * Score
 *
 * @author Arathald (Greg Jackson) Created January 10, 2012.
 * @author Will Fuqua February 09, 2013.
 */
public class Score extends NoiseModule implements Serializable {
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
		if(diskScores != null) {
			for(ScoreChange score : this.diskScores) {
				this.scores.put(score.when, score);
			}
		}
	}

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("positive", Style.GREEN);
			put("negative", Style.RED);
		}};
	}

	@Override public boolean save() {
		// Populate 'diskScores' from 'scores'
		this.diskScores = this.scores.values().toArray(new ScoreChange[0]);
		return super.save();
	}

	@Command(".*\\b([a-zA-Z0-9_]{3,16})\\+\\+.*")
	public void incrementScore(CommandContext ctx, String target) {
		this.changeScore(ctx.getMessageSender(), target, 1);
	}

	@Command(".*\\b([a-zA-Z0-9_]{3,16})\\-\\-.*")
	public void decrementScore(CommandContext ctx, String target) {
		this.changeScore(ctx.getMessageSender(), target, -1);
	}

	@Override protected void processReaction(Message message, String sender, String reaction) {
		if(reaction.equals("thumbsup") || reaction.equals("+1")) {
			this.changeScore(sender, message.getSender(), 1);
		} else if(reaction.equals("thumbsdown") || reaction.equals("-1")) {
			this.changeScore(sender, message.getSender(), -1);
		}
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
		final Map<String, Integer> totals = new TreeMap<>();
		for(Map.Entry<Date, ScoreChange> e : this.scores.entrySet()) {
			final String user = e.getValue().target;
			if((forUser == null || forUser.equals(user)) && (sinceDate == null || sinceDate.before(e.getKey()))) {
				totals.put(user, totals.getOrDefault(user, 0) + e.getValue().change);
			}
		}

		return totals;
	}

	@Command("\\.score(?: @(day|week|month|year|ever))? (.+)")
	public JSONObject getScore(CommandContext ctx, String whenStr, String nick) throws JSONException {
		final When when = (whenStr == null || whenStr.isEmpty()) ? When.ever : When.valueOf(whenStr);
		final Map<String, Integer> totals = this.getTotals(nick, when);
		final int total = totals.getOrDefault(nick, 0);
		return new JSONObject().put("who", nick).put("when", when.toString()).put("score", total);
	}

	@Command("\\.score(?: @(day|week|month|year|ever))?")
	public JSONObject getSelfScore(CommandContext ctx, String whenStr) throws JSONException {
		return this.getScore(ctx, whenStr, ctx.getMessageSender());
	}

	@Command("\\.([dwmye])score (.+)")
	public JSONObject getScoreWhen(CommandContext ctx, String when, String nick) throws JSONException {
		return this.getScore(ctx, When.getByLetter(when.charAt(0)).toString(), nick);
	}

	@Command("\\.([dwmye])score")
	public JSONObject getSelfScoreWhen(CommandContext ctx, String when) throws JSONException {
		return this.getSelfScore(ctx, When.getByLetter(when.charAt(0)).toString());
	}

	@View(method = {"getScore", "getSelfScore", "getScoreWhen", "getSelfScoreWhen"})
	public void plainViewScore(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("%s: %#d", data.get("who"), (data.getInt("score") >= 0 ? "positive" : "negative"), data.getInt("score"));
	}

	@Command("\\.(?:scores|scoreboard)(?: @(day|week|month|year|ever))?")
	public JSONObject scores(CommandContext ctx, String whenStr) throws JSONException {
		final When when = (whenStr == null || whenStr.isEmpty()) ? When.ever : When.valueOf(whenStr);
		final JSONObject totals = new JSONObject();
		for(Map.Entry<String, Integer> e : this.getTotals(null, when).entrySet()) {
			totals.put(e.getKey(), e.getValue().intValue());
		}

		return new JSONObject().put("when", when.toString()).put("scores", totals);
	}

	@Command("\\.([dwmye])(?:scores|scoreboard)")
	public JSONObject scoresWhen(CommandContext ctx, String when) throws JSONException {
		return this.scores(ctx, When.getByLetter(when.charAt(0)).toString());
	}

	@View(method = {"scores", "scoresWhen"})
	public void plainViewScores(ViewContext ctx, JSONObject data) throws JSONException {
		final JSONObject scores = data.getJSONObject("scores");
		if(scores.length() == 0) {
			ctx.respond("No scores available");
			return;
		}

		// Convert scores to a map
		final Map<String, Integer> scoresMap = new HashMap<>();
		scores.keys().forEachRemaining(key -> {
			final String nick = (String)key;
			try {
				scoresMap.put(nick, scores.getInt(nick));
			} catch(JSONException e) {
				Log.e(e);
				ctx.respond("#error JSONException: %s", e.getMessage());
			}
		});

		// Filter out non-member scores near zero, sort, and fill the args list for MessageBuilder
		final List<String> nicks = Arrays.asList(this.bot.getNicks());
		final List<Object> args = new LinkedList<>();
		scoresMap.entrySet().stream()
				.filter(e -> Math.abs(e.getValue()) > 2 || nicks.contains(e.getKey()))
				.sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
				.forEach(e -> {
					args.add(e.getKey());
					args.add(e.getValue() >= 0 ? "positive" : "negative");
					args.add(e.getValue());
				}
		);

		ctx.respond("#([, ] %s: %#d)", (Object)args.toArray());
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
