package modules;

import static org.jibble.pircbot.Colors.*;

import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;

import main.NoiseBot;
import main.NoiseModule;

/**
 * Twitter
 *
 * @author Michael Mrozek
 *         Created Jan 25, 2010.
 */
public class Twitter extends NoiseModule {
	private static final int LENGTH = 140;
	
	private static final String COLOR_POST = YELLOW;
	private static final String COLOR_ERROR = RED + REVERSE;
	
	@Override public void onTopic(String topic, String setBy, long date, boolean changed) {
		if(!changed) return;
		if(topic.length() > LENGTH)
			topic = topic.substring(0, 137) + "...";
		try {
			final Status s = new TwitterFactory().getInstance(NoiseBot.NICK, NoiseBot.PASSWORD).updateStatus(topic);
			this.bot.sendMessage(COLOR_POST + "http://twitter.com/" + NoiseBot.NICK +"/status/" + s.getId());
		} catch(TwitterException e) {
			e.printStackTrace();
			this.bot.sendMessage(COLOR_ERROR + e.getMessage());
		}
	}
	
	@Override public String getFriendlyName() {return "Twitter";}
	@Override public String getDescription() {return "Posts topics to Twitter";}
	@Override public String[] getExamples() {
		return new String[] {};
	}
	@Override public String getOwner() {return "Morasique";}
}
