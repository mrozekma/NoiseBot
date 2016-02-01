package modules;

import static main.Utilities.getJSON;

import debugging.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import main.Message;
import main.MessageBuilder;
import main.NoiseModule;
import static main.Utilities.formatSeconds;
import static main.Utilities.pluralize;

import main.Style;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Twitch
 *
 * @author Michael Mrozek
 *         Created Nov 24, 2013.
 */
public class Twitch extends NoiseModule {
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("info", Style.MAGENTA);
		}};
	}

	@Command(".*https?://(?:www\\.)?twitch.tv/([^/]+)/([bc])/([0-9]+).*")
	public JSONObject twitch(Message message, String username, String type, String videoID) throws JSONException {
		try {
			// I can't figure out where the video ID prefix is documented, but it seems to be this
			final JSONObject json = apiCall(String.format("/videos/%c%s", type.equals("b") ? 'a' : 'c', videoID));
			if(!json.has("error")) {
				final JSONObject user = apiCall(String.format("/users/%s", username));
				if(!user.has("error") && user.has("display_name")) {
					json.put("user", user);
				}
			}

			return json;
		} catch(JSONException e) {
			Log.e(e);
			return new JSONObject().put("error", "Problem parsing Twitch response");
		} catch(FileNotFoundException e) {
			Log.e(e);
			return new JSONObject().put("error", "Video not found");
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to contact Twitch");
		}
	}

	@View
	public void plainView(Message message, JSONObject json) throws JSONException {
		final Calendar date = new GregorianCalendar();
		try {
			date.setTime(dateFormat.parse(json.getString("recorded_at")));
		} catch(ParseException e) {
			Log.e(e);
			message.respond("#error %s", e.getMessage());
			return;
		}

		final MessageBuilder builder = message.buildResponse();
		builder.add("#info %s (%s, recorded %d %s %d", new Object[] {json.get("title"), formatSeconds(json.getInt("length")), date.get(Calendar.DAY_OF_MONTH), date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()), date.get(Calendar.YEAR)});

		if(json.has("user")) {
			builder.add("#info  by %s", new Object[] {json.getJSONObject("user").get("display_name")});
		}

		if(json.has("game") && !json.isNull("game")) {
			builder.add("#info  playing %s", new Object[] {json.get("game")});
		}

		builder.add("#info , %s)", new Object[] {pluralize(json.getInt("views"), "view", "views")});
		builder.send();
	}

	private JSONObject apiCall(String url) throws IOException, JSONException {
		final HttpURLConnection conn = (HttpURLConnection)(new URL("https://api.twitch.tv/kraken" + url).openConnection());
		conn.setRequestProperty("Accept", "application/vnd.twichtv.v2+json");
		final int code = conn.getResponseCode();
		final JSONObject json = getJSON(conn);

		if(code != 200) {
			final StringBuffer error = new StringBuffer();
			error.append(json.has("error") ? json.getString("error") : "Error");
			if(json.has("message")) {
				error.append(": ");
				error.append(json.getString("message"));
			}
			return new JSONObject().put("error", error.toString());
		}

		return json;
	}

	@Override public String getFriendlyName() {return "Twitch";}
	@Override public String getDescription() {return "Outputs information about any Twitch.tv URLs posted";}
	@Override public String[] getExamples() {
		return new String[] {
			"http://www.twitch.tv/morasique/c/2588375"
		};
	}
}
