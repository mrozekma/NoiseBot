package modules;

import static org.jibble.pircbot.Colors.GREEN;
import static org.jibble.pircbot.Colors.NORMAL;
import static org.jibble.pircbot.Colors.RED;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import main.Message;
import main.NoiseModule;
import panacea.MapFunction;
import panacea.Panacea;
import panacea.ReduceFunction;

/**
 * Score
 * 
 * @author Arathald (Greg Jackson) Created January 10, 2012.
 * @author Will Fuqua February 09, 2013.
 */
public class Score extends NoiseModule implements Serializable {
	private static final String COLOR_POSITIVE = GREEN;
	private static final String COLOR_NEGATIVE = RED;

	// track username => score. username key is always lowercase, ScoreEntry.username is formatted
	private Map<String, ScoreEntry> userScores = new HashMap<String, ScoreEntry>();

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

	@Command(".*\\b([a-zA-Z0-9_]{3,16})\\+\\+.*")
	public void incrementScore(Message message, String target) {
		this.changeScore(target, 1);
	}

	@Command(".*\\b([a-zA-Z0-9_]{3,16})\\-\\-.*")
	public void decrementScore(Message message, String target) {
		this.changeScore(target, -1);
		if (target.equals("arathald"))
			this.changeScore(target, -9);
	}

	private void changeScore(String nick, Integer amount) {
		String userKey = nick.toLowerCase();
		ScoreEntry currentScore = this.userScores.get(userKey);
		if (currentScore == null)
			currentScore = new ScoreEntry(nick, 0);
		currentScore.score += amount;
		
		//update the score data, stop tracking the user if they have 0 points
		if(currentScore.score == 0)
			this.userScores.remove(userKey);
		else
			this.userScores.put(userKey, currentScore);
		
		this.save();
	}

	@Command("\\.score (.+)")
	public void getScore(Message message, String nick) {
		String userKey = nick.toLowerCase();
		ScoreEntry entry = this.userScores.get(userKey);
		if (entry != null) {
			this.bot.sendMessage(entry.ircFormat());
		} else {
			this.bot.sendMessage(nick + " has no score");
		}
	}

	@Command("\\.score")
	public void getSelfScore(Message message) {
		this.getScore(message, message.getSender());
	}

	@Command("\\.winner")
	public void winner(Message m) {
		
		ScoreEntry[] scores = this.userScores.values().toArray(new ScoreEntry[0]);
		
		if(scores.length == 0) {
			this.bot.sendMessage("No scores available");
			return;
		}
		
		ScoreEntry winner = Panacea.reduce(scores, new ReduceFunction<ScoreEntry, ScoreEntry>() {
			@Override public ScoreEntry reduce(ScoreEntry source, ScoreEntry accum) {
				return (accum.score > source.score) ? accum : source;
			}
		}, new ScoreEntry(null, Integer.MIN_VALUE));

		this.bot.sendMessage(winner.ircFormat());
	}

	@Command("\\.(?:scores|scoreboard)")
	public void scores(Message message) {
		ScoreEntry[] scores = this.userScores.values().toArray(new ScoreEntry[0]);
		
		if(scores.length == 0) {
			this.bot.sendMessage("No scores available");
			return;
		}
		
		Arrays.sort(scores, Collections.reverseOrder());
		String scoreboard = Panacea.implode(
			Panacea.map(scores, new MapFunction<ScoreEntry, String>() {
				@Override public String map(ScoreEntry source) {
					return source.ircFormat();
				}
			}),
			", "
		);
		
		this.bot.sendMessage(scoreboard);
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
		return new String[] { "_nick_++ -- Increment _nick_'s score",
				"_nick_-- -- Decrement _nick_'s score",
				".score _nick_ -- Display _nick_'s score" };
	}
}
