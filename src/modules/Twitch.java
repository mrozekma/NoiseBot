package modules;

import static main.Utilities.getJSON;
import static org.jibble.pircbot.Colors.*;
import static panacea.Panacea.*;

import debugging.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import main.Message;
import main.NoiseModule;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Twitch
 *
 * @author Michael Mrozek
 *         Created Nov 24, 2013.
 */
public class Twitch extends NoiseModule {
	private static final String COLOR_ERROR = RED;
	private static final String COLOR_INFO = PURPLE;

	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

	@Command(".*https?://(?:www\\.)?twitch.tv/([^/]+)/([bc])/([0-9]+).*")
	public void twitch(Message message, String username, String type, String videoID) {
		try {
			// I can't figure out where the video ID prefix is documented, but it seems to be this
			final JSONObject json = apiCall(String.format("/videos/%c%s", type.equals("b") ? 'a' : 'c', videoID), true);
			if(json == null) {return;}

			final StringBuffer info = new StringBuffer();
			info.append(json.getString("title"));
			info.append(" (");
			info.append(formatTimestamp(json.getInt("length")));
			info.append(", recorded ");
			final Calendar date = new GregorianCalendar();
			date.setTime(dateFormat.parse(json.getString("recorded_at")));
			info.append(String.format("%d %s %d", date.get(Calendar.DAY_OF_MONTH), date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()), date.get(Calendar.YEAR)));

			// It'd be nice to confirm the username actually matches the video, but the API doesn't easily expose it
			final JSONObject user = apiCall(String.format("/users/%s", username), false);
			if(user != null && user.has("display_name")) {
				info.append(" by ");
				info.append(user.getString("display_name"));
			}

			if(json.has("game") && !json.isNull("game")) {
				info.append(" playing ");
				info.append(json.getString("game"));
			}

			info.append(", ");
			info.append(pluralize(json.getInt("views"), "view", "views"));
			info.append(')');

			this.bot.sendMessage(COLOR_INFO + info.toString());
		} catch(ParseException e) {
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Twitch response");
			Log.e(e);
		} catch(JSONException e) {
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Twitch response");
			Log.e(e);
		} catch(FileNotFoundException e) {
			this.bot.sendMessage(COLOR_ERROR + "Video not found");
			Log.e(e);
		} catch(IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to contact Twitch");
			Log.e(e);
		}
	}

	private JSONObject apiCall(String url, boolean showError) throws IOException, JSONException {
		final HttpURLConnection conn = (HttpURLConnection)(new URL("https://api.twitch.tv/kraken" + url).openConnection());
		conn.setRequestProperty("Accept", "application/vnd.twichtv.v2+json");
		final int code = conn.getResponseCode();
		final JSONObject json = getJSON(conn);

		if(code != 200) {
			if(showError) {
				final StringBuffer error = new StringBuffer();
				error.append(json.has("error") ? json.getString("error") : "Error");
				if(json.has("message")) {
					error.append(": ");
					error.append(json.getString("message"));
				}
				this.bot.sendMessage(COLOR_ERROR + error.toString());
			}
			return null;
		}

		return json;
	}

	private String formatTimestamp(int seconds) {
		final StringBuffer rtn = new StringBuffer();
		final int hours = seconds / 3600;
		seconds -= hours * 3600;
		final int minutes = seconds / 60;
		seconds -= minutes * 60;
		return (hours > 0)
			? String.format("%d:%02d:%02d", hours, minutes, seconds)
			: String.format("%d:%02d", minutes, seconds);
	}

	@Override public String getFriendlyName() {return "Twitch";}
	@Override public String getDescription() {return "Outputs information about any Twitch.tv URLs posted";}
	@Override public String[] getExamples() {
		return new String[] {
			"http://www.twitch.tv/morasique/c/2588375"
		};
	}
}
