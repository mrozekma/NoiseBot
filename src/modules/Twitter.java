package modules;

import static main.Utilities.getJSON;
import static main.Utilities.urlEncode;
import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

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
	private static final String TWITTER_URL_PATTERN = "https?://(?:mobile\\.)?twitter.com/.*/status(?:es)?/([0-9]+)";
	private static final int PERIOD = 30; // seconds

	private long sinceID = 0;
	private final Timer timer = new Timer();

	private static final String COLOR_ERROR = RED;
	private static final String COLOR_INFO = PURPLE;
	
	@Override public void init(NoiseBot bot) {
		super.init(bot);
		this.timer.scheduleAtFixedRate(new TimerTask() {
			@Override public void run() {
				Twitter.this.poll();
			}
		}, 0, PERIOD * 1000);
	}

	@Command(".*" + TWITTER_URL_PATTERN + ".*")
	public void tweet(Message message, String id) {
		try {
			final JSONObject json = getJSON("http://api.twitter.com/1/statuses/show/" + id + ".json");
			if(json.has("user") && json.has("text")) {
				this.emitTweet(json.getJSONObject("user").getString("screen_name"), json.getString("text"));
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
	
	public void poll() {
		try {
			final JSONObject json = getJSON("http://search.twitter.com/search.json?result_type=recent&q=" + urlEncode("#rhnoise") + (sinceID > 0 ? "&since_id=" + sinceID : ""));
			final JSONArray results = json.getJSONArray("results");
			
			if(this.sinceID == 0) {
				Log.i("Initial poll");
				this.sinceID = results.length() == 0 ? 1 : results.getJSONObject(0).getLong("id");
				return;
			}

			Log.v("Polling for tweets since " + this.sinceID);
			if(results.length() > 0) {
				Log.i(results.length() + " new tweet(s)");
				for(int i = results.length() - 1; i >= 0; i--) {
					final JSONObject tweet = results.getJSONObject(i);
					Log.i("Emitting tweet ID " + tweet.getLong("id"));
					this.emitTweet(tweet.getString("from_user"), tweet.getString("text"));
					this.sinceID = tweet.getLong("id");
				}
			}
		} catch(IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to connect to Twitter");
		} catch(JSONException e) {
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Twitter response");
		}
	}
	
	private void emitTweet(String username, String text) {
		this.bot.sendMessage(COLOR_INFO + UNDERLINE + "@" + username + NORMAL + COLOR_INFO + ": " + text);
	}
	
	@Override public String getFriendlyName() {return "Twitter";}
	@Override public String getDescription() {return "Outputs information about any Twitter URLs posted, and any tweets containing the #rhnoise hashtag";}
	@Override public String[] getExamples() {
		return new String[] {};
	}
	
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
