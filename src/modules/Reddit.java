package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import static main.Utilities.urlEncode;

/**
 * Reddit
 *
 * @author Michael Mrozek
 *         Created Jul 7, 2009.
 */
public class Reddit extends NoiseModule {
	private static final String REDDIT_URL_PATTERN = "http://www.reddit.com/r/[a-zA-Z0-9_-]+/comments/[a-zA-Z0-9]+";
	
	private static final String COLOR_INFO = PURPLE;
	private static final String COLOR_ERROR = RED + REVERSE;
	
	private static JSONObject getJSON(String url) throws IOException, JSONException {
		final URLConnection c = new URL(url).openConnection();
		final Scanner s = new Scanner(c.getInputStream());
		final String str = s.nextLine();
		return new JSONObject(str.startsWith("[") && str.endsWith("]") ? str.substring(1, str.length() - 1) : str);
	}
	
	@Command(".*(" + REDDIT_URL_PATTERN + ").*")
	public void reddit(Message message, String url) {
		try {
			final JSONObject json = getJSON(url + "/.json");
			final JSONObject data = json.getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
			this.bot.sendMessage(NoiseBot.ME, COLOR_INFO + data.getString("title") + ", " + data.getString("url") + " (posted by " + data.getString("author") + " in " + data.getString("subreddit") + " at " + new Date(data.getLong("created_utc") * 1000) + ", +" + data.getInt("ups") + "/-" + data.getInt("downs") + ", " + data.getInt("num_comments") + " comment" + (data.getInt("num_comments") == 1 ? "" : "s") + ")");
		} catch(IOException e) {
			this.bot.sendMessage(NoiseBot.ME, COLOR_ERROR + "Unable to connect to Reddit");
			Log.e(e);
		} catch(JSONException e) {}
	}

	@Command(".*((?:ftp|http|https):\\/\\/(?:\\w+:{0,1}\\w*@)?(?:\\S+)(?::[0-9]+)?(?:\\/|\\/(?:[\\w#!:.?+=&%@!\\-\\/]))?).*")
	public void url(Message message, String url) {
		if(url.matches(REDDIT_URL_PATTERN + ".*")) {return;}
		
		try {
			final JSONObject json = getJSON("http://www.reddit.com/api/info.json?count=1&url=" + urlEncode(url));
			final JSONArray children = json.getJSONObject("data").getJSONArray("children");
			if(children.length() > 0) {
				JSONObject data = children.getJSONObject(0).getJSONObject("data");
				this.bot.sendMessage(NoiseBot.ME, COLOR_INFO + data.getString("title") + ", http://www.reddit.com/r/" + data.getString("subreddit") + "/comments/" + data.getString("id") + " (posted by " + data.getString("author") + " in " + data.getString("subreddit") + " at " + new Date(data.getLong("created_utc") * 1000) + ", +" + data.getInt("ups") + "/-" + data.getInt("downs") + ", " + data.getInt("num_comments") + " comment" + (data.getInt("num_comments") == 1 ? "" : "s") + ")");
			}
		} catch(IOException e) {
			this.bot.sendMessage(NoiseBot.ME, COLOR_ERROR + "Unable to connect to Reddit");
			Log.e(e);
		} catch(JSONException e) {}
	}

	@Override public String getFriendlyName() {return "Reddit";}
	@Override public String getDescription() {return "Outputs information about any reddit URLs posted, or any URLs that have reddit posts";}
	@Override public String[] getExamples() {
		return new String[] {
			"http://www.reddit.com/r/programming/comments/8ydvg/",
			"http://nflath.com/2009/07/the-dangers-of-stringsubstring/"
		};
	}
	@Override public String getOwner() {return "Morasique";}
}
