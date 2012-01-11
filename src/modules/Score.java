package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import main.Message;
import main.NoiseModule;

import static modules.Slap.slapUser;


/**
 * Score
 *
 * @author Arathald (Greg Jackson)
 *         Created January 10, 2012.
 */

public class Score extends NoiseModule implements Serializable {
	private static final String COLOR_POSITIVE = ""; // No color
	private static final String COLOR_NEGATIVE = RED;
	
	private Map<String, Integer> userScores = new HashMap<String, Integer>();
	
	@Command("(.+)\\+\\+")
	public void incrementScore(Message message, String target) {
		if (target.equals(message.getSender())) {
			this.bot.sendAction(slapUser(message.getSender()));
		} else {
			this.changeScore(target, 1);
		}
	}
	
	@Command("(.+)\\-\\-")
	public void decrementScore(Message message, String target) {
		this.changeScore(target, -1);
	}
	
	@Command("\\.score (.+)")
	public void getScore(Message message, String target) {
		if (this.userScores.containsKey(target)) {
			Integer score = this.userScores.get(target);
			String color = (score >= 0 ? this.COLOR_POSITIVE : this.COLOR_NEGATIVE);
			this.bot.sendMessage(target + "'s score is " + color + score);
		} else {
			this.bot.sendMessage(target + " has no score");
		}
	}
	
	@Override public String getFriendlyName() {return "Score";}
	@Override public String getDescription() {return "Keeps users' scores";}
	@Override public String[] getExamples() {
		return new String[] {
				"_nick_++ -- Increment _nick_'s score",
				"_nick_-- -- Decrement _nick_'s score",
				".score _nick_ -- Display _nick_'s score"
		};
	}
	
	private void changeScore(String user, Integer amount) {
		Integer oldScore = (this.userScores.containsKey(user) ? this.userScores.get(user) : 0);
		this.userScores.put(user, oldScore + amount);
		this.save();
	}
}