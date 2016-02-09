package modules;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import main.*;
import org.json.JSONException;

import debugging.Log;

import static main.Utilities.bytesToFriendly;
import static main.Utilities.pluralize;

/**
 * Imgur
 *
 * @author Michael Mrozek
 *         Created Apr 26, 2013.
 */
public class Imgur extends NoiseModule {
	private static final String URL_PATTERN = "https?://(?:.+\\.)?imgur.com/(?:.+/)?([a-zA-Z0-9]+)(?:\\.[a-z]{3})?";

	@Configurable("client-id")
	private String clientID = null;

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("info", Style.MAGENTA);
		}};
	}

	@Command(".*" + URL_PATTERN + ".*")
	public JSONObject imgur(CommandContext ctx, String imgID) throws JSONException {
		try {
			final JSONObject json = this.getJSON(imgID);
			if(!json.getBoolean("success")) {
				if(json.has("data")) {
					final JSONObject data = json.getJSONObject("data");
					if(data.has("error")) {
						return data;
					}
				}
				return new JSONObject().put("error", "Unknown error communicating with Imgur");
			}

			return json.getJSONObject("data");
		} catch(FileNotFoundException e) {
			Log.e(e);
			return new JSONObject().put("error", String.format("No image with ID %s exists", imgID));
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", String.format("Unable to communicate with imgur: %s", e.getMessage()));
		}
	}

	@View
	public void view(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("#info %s%s (%dx%d, %s, %s)",
				(data.has("title") && !data.isNull("title")) ? data.get("title") : "Untitled",
				(data.has("description") && !data.isNull("description")) ? " -- " + data.get("description") : "",
				data.getInt("width"),
				data.getInt("height"),
				bytesToFriendly(data.getInt("size"), 2),
				pluralize(data.getInt("views"), "view", "views"));
	}

	@View
	public void slackView(ViewContext ctx, JSONObject data) {
		// Slack already unfolds Imgur URLs
	}

	@Override public String getFriendlyName() {return "Imgur";}
	@Override public String getDescription() {return "Outputs information about any Imgur URLs posted";}
	@Override public String[] getExamples() {
		return new String[] {
			"http://imgur.com/E75a0ua"
		};
	}

	private JSONObject getJSON(String imgID) throws IOException, JSONException {
		final URLConnection c = new URL("https://api.imgur.com/3/image/" + imgID).openConnection();
		c.addRequestProperty("Authorization", "Client-ID " + this.clientID);
		final Scanner s = new Scanner(c.getInputStream());
		final StringBuffer buffer = new StringBuffer();
		while(s.hasNextLine()) {
			buffer.append(s.nextLine());
		}
		return new JSONObject(buffer.toString());
	}
}
