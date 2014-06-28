package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

import main.Message;
import main.NoiseModule;

import static panacea.Panacea.*;

/**
 * StackExchange
 *
 * @author Michael Mrozek
 *         Created Aug 29, 2010.
 */
public class StackExchange extends NoiseModule {
	private static final String BASE = "http://((?:meta\\.)?(?:stackoverflow|serverfault|superuser|(?:.*)\\.stackexchange))\\.com";
	private static final String QUESTION_URL_PATTERN = BASE + "/q(?:uestions)?/([0-9]+)";
	private static final String ANSWER_URL_PATTERN = BASE + "/q(?:uestions)?/[0-9]+/.*/([0-9]+)";
	private static final String ANSWER_SHORT_URL_PATTERN = BASE + "/a/([0-9]+)";

	private static final String API_QUESTION = "http://api.stackexchange.com/2.2/questions/%s?site=%s&filter=!bGqd94P8N)GqQF";
	private static final String API_ANSWER = "http://api.stackexchange.com/2.2/answers/%s?site=%s&filter=!-*f(6t9MJHcA";

	private static final String COLOR_INFO = PURPLE;
	private static final String COLOR_ERROR = RED + REVERSE;

	private static JSONObject getJSON(String url) throws IOException, JSONException {
		Log.i("Loading from API: %s", url);
		final URLConnection c = new URL(url).openConnection();
		final GZIPInputStream s = new GZIPInputStream(c.getInputStream());
		final StringBuffer b = new StringBuffer();

		final byte[] buffer = new byte[1024];
		int size;
		while((size = s.read(buffer)) >= 0) {
			b.append(new String(buffer, 0, size));
		}

		return new JSONObject(b.toString());
	}

	@Command(".*" + QUESTION_URL_PATTERN + ".*")
	public void question(Message message, String site, String id) {this.se(message, site, false, id);}

	@Command(".*" + ANSWER_URL_PATTERN + ".*")
	public void answer(Message message, String site, String id) {this.se(message, site, true, id);}

	@Command(".*" + ANSWER_SHORT_URL_PATTERN + ".*")
	public void answer_short(Message message, String site, String id) {this.se(message, site, true, id);}

	private void se(Message message, String site, boolean isAnswer, String id) {
		try {
			final JSONObject json = getJSON(String.format(isAnswer ? API_ANSWER : API_QUESTION, id, site));
			if(json.has("error_id")) {
				this.bot.sendMessage(String.format(COLOR_ERROR + "Error %d: %s: %s", json.getInt("error_id"), json.has("error_name") ? json.getString("error_name") : "???", json.has("error_message") ? json.getString("error_message") : "???"));
				return;
			}

			final JSONArray items = json.getJSONArray("items");
			if(items.length() == 0) {
				this.bot.sendMessage(COLOR_ERROR + "No post with ID " + id);
				return;
			}

			final JSONObject post = items.getJSONObject(0);
			final String created;
			{
				final Calendar c = new GregorianCalendar();
				c.setTime(new Date(post.getInt("creation_date") * 1000L));
				created = c.get(Calendar.DAY_OF_MONTH) + " " + c.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US) + " " + c.get(Calendar.YEAR);
			}

			final StringBuilder out = new StringBuilder();
			out.append(StringEscapeUtils.unescapeHtml4(post.getString("title")))
			   .append(" (")
			   .append(isAnswer ? "answered" : "asked").append(" by ").append(post.getJSONObject("owner").getString("display_name"))
			   .append(" on ").append(created).append(", ")
			   .append("+").append(post.getInt("up_vote_count")).append("/-").append(post.getInt("down_vote_count"));
			if(!isAnswer) {
				out.append(", ").append(pluralize(post.getInt("view_count"), "view", "views"))
				   .append(", ").append(pluralize(post.getInt("answer_count"), "answer", "answers"));
			}
			out.append(")");

			this.bot.sendMessage(COLOR_INFO + out.toString());
		} catch(Exception e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing SE data");
		}
	}

	@Override public String getFriendlyName() {return "StackExchange";}
	@Override public String getDescription() {return "Outputs information about any Stack Exchange URLs posted";}
	@Override public String[] getExamples() {
		return new String[] {
			"http://stackoverflow.com/questions/3041249/when-are-temporaries-created-as-part-of-a-function-call-destroyed",
			"http://unix.stackexchange.com/q/1262/73",
			"http://unix.stackexchange.com/questions/1262/where-did-the-wheel-group-get-its-name/1271#1271",
		};
	}
}
