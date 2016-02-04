package modules;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import main.Style;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

import main.CommandContext;
import main.NoiseModule;
import main.ViewContext;

import static main.Utilities.urlEncode;

/**
 * Reddit
 *
 * @author Michael Mrozek
 *         Created Jul 7, 2009.
 */
public class Reddit extends NoiseModule {
	private static final String REDDIT_URL_PATTERN = "http://www.reddit.com/r/[a-zA-Z0-9_-]+/comments/[a-zA-Z0-9]+";

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("info", Style.MAGENTA);
		}};
	}

	private static JSONObject getJSON(String url) throws IOException, JSONException {
		final URLConnection c = new URL(url).openConnection();
		final Scanner s = new Scanner(c.getInputStream());
		final String str = s.nextLine();
		return new JSONObject(str.startsWith("[") && str.endsWith("]") ? str.substring(1, str.length() - 1) : str);
	}

	@Command(".*(" + REDDIT_URL_PATTERN + ").*")
	public JSONObject reddit(CommandContext ctx, String url) throws JSONException {
		try {
			final JSONObject json = getJSON(url + "/.json");
			final JSONObject rtn = json.getJSONObject("data").getJSONArray("children").getJSONObject(0).getJSONObject("data");
			return rtn;
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to connect to Reddit");
		}
	}

	@Command(".*((?:ftp|http|https):\\/\\/(?:\\w+:{0,1}\\w*@)?(?:\\S+)(?::[0-9]+)?(?:\\/|\\/(?:[\\w#!:.?+=&%@!\\-\\/]))?).*")
	public JSONObject url(CommandContext ctx, String url) throws JSONException {
		if(url.matches(REDDIT_URL_PATTERN + ".*")) {return new JSONObject();}

		try {
			final JSONObject json = getJSON("http://www.reddit.com/api/info.json?count=1&url=" + urlEncode(url));
			final JSONArray children = json.getJSONObject("data").getJSONArray("children");
			if(children.length() > 0) {
				final JSONObject rtn = children.getJSONObject(0).getJSONObject("data");
				rtn.put("linked_url", url);
				return rtn;
			} else {
				return new JSONObject();
			}
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to connect to Reddit");
		}
	}

	@View
	public void plainView(ViewContext ctx, JSONObject data) throws JSONException {
		if(data.length() == 0) {
			return;
		} else if(data.has("linked_url")) {
			this.bot.sendMessage("#info %s, http://www.reddit.com/r/%s/comments/%s (posted by %s in %s at %s, +%d/-%d, %d %s)",
					data.getString("title"),
					data.getString("subreddit"),
					data.getString("id"),
					data.getString("author"),
					data.getString("subreddit"),
					new Date(data.getLong("created_utc") * 1000),
					data.getInt("ups"),
					data.getInt("downs"),
					data.getInt("num_comments"),
					(data.getInt("num_comments") == 1) ? "comment" : "comments");
		} else {
			this.bot.sendMessage("#info %s, %s (posted by %s in %s at %s, +%d/-%d, %d %s)",
					data.getString("title"),
					data.getString("url"),
					data.getString("author"),
					data.getString("subreddit"),
					new Date(data.getLong("created_utc") * 1000),
					data.getInt("ups"),
					data.getInt("downs"),
					data.getInt("num_comments"),
					(data.getInt("num_comments") == 1) ? "comment" : "comments");
		}
	}

	@Override public String getFriendlyName() {return "Reddit";}
	@Override public String getDescription() {return "Outputs information about any reddit URLs posted, or any URLs that have reddit posts";}
	@Override public String[] getExamples() {
		return new String[] {
			"http://www.reddit.com/r/programming/comments/8ydvg/",
			"http://nflath.com/2009/07/the-dangers-of-stringsubstring/"
		};
	}
}
