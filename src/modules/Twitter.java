package modules;

import static main.Utilities.getJSON;
import static main.Utilities.urlEncode;
import static org.jibble.pircbot.Colors.*;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

/**
 * Twitter
 *
 * @author Michael Mrozek
 *         Created Jan 25, 2010.
 */
public class Twitter extends NoiseModule {
	private static final String TWITTER_URL_PATTERN = "http://twitter.com/.*/status(es)?/([0-9]+)";

	private static final String COLOR_ERROR = RED;
	private static final String COLOR_INFO = PURPLE;
	
	@Command(".*" + TWITTER_URL_PATTERN + ".*")
	public void tweet(Message message, String id) {
		try {
			final JSONObject json = getJSON("http://api.twitter.com/1/statuses/show/" + id + ".json");
			if(json.has("user") && json.has("text")) {
				this.bot.sendMessage(COLOR_INFO + UNDERLINE + "@" + json.getJSONObject("user").getString("screen_name") + NORMAL + COLOR_INFO + ": " + json.getString("text"));
			} else if(json.has("error")) {
				this.bot.sendMessage(COLOR_ERROR + "Twitter error: " + json.getString("error"));
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Unknown Twitter error");
			}
		} catch(IOException e) {
			if(e.getMessage().contains("Server returned HTTP response code: 403")) {
				this.bot.sendMessage(COLOR_ERROR + "Tweet is protected");
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Unable to connect to Twitter");
			}
		} catch(JSONException e) {
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Twitter response");
			
		}
	}
	
	@Override public String getFriendlyName() {return "Twitter";}
	@Override public String getDescription() {return "Outputs information about any Twitter URLs posted";}
	@Override public String[] getExamples() {
		return new String[] {};
	}
	@Override public String getOwner() {return "Morasique";}
	
	/*
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
	*/
}
