package modules;

import static main.Utilities.getJSON;
import static main.Utilities.urlEncode;
import static org.jibble.pircbot.Colors.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.net.HttpURLConnection;
import java.net.URL;

import migbase64.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

import main.Message;
import main.ModuleLoadException;
import main.NoiseBot;
import main.NoiseModule;

import static main.Utilities.urlEncode;

/**
 * Twitter
 *
 * @author Michael Mrozek
 *         Created Jan 25, 2010.
 */
public class Twitter extends NoiseModule {
	private static final String TWITTER_URL_PATTERN = "https?://(?:mobile\\.)?twitter.com/.*/status(?:es)?/([0-9]+)";
	private static final int PERIOD = 30; // seconds

	private String key, secret;
	private String token;
	private long sinceID = 0;
	private final Timer timer = new Timer();

	private static final String COLOR_ERROR = RED;
	private static final String COLOR_INFO = PURPLE;

	@Override public void init(NoiseBot bot, Map<String, String> config) throws ModuleLoadException {
		super.init(bot, config);
		if(!config.containsKey("key")) {
			throw new ModuleLoadException("No Twitter key specified in configuration");
		}
		this.key = config.get("key");
		if(!config.containsKey("secret")) {
			throw new ModuleLoadException("No Twitter secret specified in configuration");
		}
		this.secret = config.get("secret");

		this.token = this.authenticate();
		if(this.token != null) {
			this.timer.scheduleAtFixedRate(new TimerTask() {
				@Override public void run() {
					Twitter.this.poll();
				}
			}, 0, PERIOD * 1000);
		}
	}

	@Override public void unload() {
		super.unload();
		this.timer.cancel();
	}

	private String authenticate() {
		final String auth = Base64.encodeToString(String.format("%s:%s", urlEncode(this.key), urlEncode(this.secret)).getBytes(), false);
		try {
			final URL url = new URL("https://api.twitter.com/oauth2/token");
			final HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
			conn.setRequestProperty("Authorization", "Basic " + auth);
			final DataOutputStream writer = new DataOutputStream(conn.getOutputStream());
			writer.writeBytes("grant_type=client_credentials");
			writer.flush();
			writer.close();

			final JSONObject json = getJSON(conn);
			if(!json.getString("token_type").equals("bearer")) {
				Log.d("%s", json.toString());
				this.bot.sendMessage(COLOR_ERROR + "Unexpected token type: " + json.getString("token_type"));
				return null;
			}

			return json.getString("access_token");
		} catch(IOException e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Unable to authenticate with Twitter: " + e.getMessage());
			return null;
		} catch(JSONException e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Unable to interpret Twitter response: " + e.getMessage());
			return null;
		}
	}

	private JSONObject api(String route) throws IOException, JSONException {
		if(this.token == null) {
			return null;
		}

		final URL url = new URL("https://api.twitter.com/1.1" + route);
		final HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setRequestProperty("Authorization", "Bearer " + this.token);
		return getJSON(conn);
	}

	@Command(".*" + TWITTER_URL_PATTERN + ".*")
	public void tweet(Message message, String id) {
		try {
			final JSONObject json = api("/statuses/show/" + id + ".json");
			if(json.has("user") && json.has("text")) {
				this.emitTweet(json.getJSONObject("user").getString("screen_name"), json.getString("text"));
			} else if(json.has("error")) {
				this.bot.sendMessage(COLOR_ERROR + "Twitter error: " + json.getString("error"));
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Unknown Twitter error");
			}
		} catch(IOException e) {
			Log.e(e);
			if(e.getMessage().contains("Server returned HTTP response code: 403")) {
				this.bot.sendMessage(COLOR_ERROR + "Tweet is protected");
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Unable to connect to Twitter");
			}
		} catch(JSONException e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Twitter response");
		}
	}

	public void poll() {
		try {
			final JSONObject json = api("/search/tweets.json?result_type=recent&q=" + urlEncode("#rhnoise") + (sinceID > 0 ? "&since_id=" + sinceID : ""));
			Log.v("%s", json.toString());
			final JSONArray results = json.getJSONArray("statuses");

			if(this.sinceID == 0) {
				Log.i("Initial poll");
				this.sinceID = results.length() == 0 ? 1 : results.getJSONObject(0).getLong("id");
				return;
			}

			Log.v("Polling for tweets since " + this.sinceID);
			if(results.length() > 0) {
				Log.i("%d new tweet(s)", results.length());
				for(int i = results.length() - 1; i >= 0; i--) {
					final JSONObject tweet = results.getJSONObject(i);
					Log.i("Emitting tweet ID %ld", tweet.getLong("id"));
					this.emitTweet(tweet.getJSONObject("user").getString("name"), tweet.getString("text"));
					this.sinceID = tweet.getLong("id");
				}
			}
		} catch(IOException e) { // Meh
			Log.e(e);
		} catch(JSONException e) {
			Log.e(e);
		}
	}

	private void emitTweet(String username, String text) {
		this.bot.sendMessage(COLOR_INFO + UNDERLINE + "@" + username + NORMAL + COLOR_INFO + ": " + encoded(text.replace("\n", " ")));
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
