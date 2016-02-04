package modules;

import static main.Utilities.getJSON;
import static main.Utilities.urlEncode;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.net.HttpURLConnection;
import java.net.URL;

import main.*;
import migbase64.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.jsoup.Jsoup;

import debugging.Log;

/**
 * Twitter
 *
 * @author Michael Mrozek
 *         Created Jan 25, 2010.
 */
public class Twitter extends NoiseModule {
	private static final String TWITTER_URL_PATTERN = "https?://(?:mobile\\.)?twitter.com/.*/status(?:es)?/([0-9]+)";
	private static final int PERIOD = 30; // seconds

	@Configurable("key")
	private String key = null;

	@Configurable("secret")
	private String secret = null;

	private String token = null;
	private long sinceID = 0;
	private Timer timer = null;

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("info", Style.MAGENTA);
			put("username", get("info").update("underline"));
		}};
	}

	@Override public void setConfig(final Map<String, Object> config) throws ModuleInitException {
		super.setConfig(config);

		if(this.timer != null) {
			this.timer.cancel();
			this.timer = null;
		}

		if(this.key == null || this.secret == null) {
			return;
		}

		this.timer = new Timer();
		this.token = this.authenticate();
		this.timer.scheduleAtFixedRate(new TimerTask() {
			@Override public void run() {
				Twitter.this.poll();
			}
		}, 0, PERIOD * 1000);
	}

	@Override public void unload() {
		super.unload();
		if(this.timer != null) {
			this.timer.cancel();
		}
	}

	private String authenticate() throws ModuleInitException {
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
				throw new ModuleInitException("Unexpected token type: " + json.getString("token_type"));
			}

			return json.getString("access_token");
		} catch(IOException e) {
			Log.e(e);
			throw new ModuleInitException("Unable to authenticate with Twitter: " + e.getMessage());
		} catch(JSONException e) {
			Log.e(e);
			throw new ModuleInitException("Unable to interpret Twitter response: " + e.getMessage());
		}
	}

	private JSONObject api(String route) throws IOException, JSONException {
		final URL url = new URL("https://api.twitter.com/1.1" + route);
		final HttpURLConnection conn = (HttpURLConnection)url.openConnection();
		conn.setRequestProperty("Authorization", "Bearer " + this.token);
		return getJSON(conn);
	}

	@Command(".*" + TWITTER_URL_PATTERN + ".*")
	public JSONObject tweet(CommandContext ctx, String id) throws JSONException {
		try {
			final JSONObject json = api("/statuses/show/" + id + ".json");
			if(json.has("user") && json.has("text")) {
				return json;
			} else if(json.has("error")) {
				return new JSONObject().put("error", "Twitter error: " + json.getString("error"));
			} else {
				return new JSONObject().put("error", "Unknown Twitter error");
			}
		} catch(IOException e) {
			Log.e(e);
			if(e.getMessage().contains("IRCServer returned HTTP response code: 403")) {
				return new JSONObject().put("error", "Tweet is protected");
			} else {
				return new JSONObject().put("error", "Unable to connect to Twitter");
			}
		} catch(JSONException e) {
			Log.e(e);
			return new JSONObject().put("error", "Problem parsing Twitter response");
		}
	}

	@View
	public void plainView(ViewContext ctx, JSONObject json) throws JSONException {
		final String username = json.getJSONObject("user").getString("screen_name");
		final String text = Jsoup.parse(json.getString("text")).text();
		ctx.respond("#username @%s#info : %s", username, text);
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
					// Not sure what the right thing to do is here; I really want to displayMessage(), but the interface is pretty awkward
					this.plainView(this.bot.makeViewContext(), tweet);
					this.sinceID = tweet.getLong("id");
				}
			}
		} catch(JSONException | IOException e) { // Meh
			Log.e(e);
		}
	}

	@Override public String getFriendlyName() {return "Twitter";}
	@Override public String getDescription() {return "Outputs information about any Twitter URLs posted, and any tweets containing the #rhnoise hashtag";}
	@Override public String[] getExamples() {return new String[0];}
}
