package modules;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.zip.GZIPInputStream;

import main.*;
import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONException;

import debugging.Log;

import static main.Utilities.pluralize;

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

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("info", Style.MAGENTA);
		}};
	}

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

	@Command(".*" + ANSWER_URL_PATTERN + ".*")
	public JSONObject answer(CommandContext ctx, String site, String id) throws JSONException {
		return this.se(ctx, site, true, id);
	}

	@Command(".*" + ANSWER_SHORT_URL_PATTERN + ".*")
	public JSONObject answer_short(CommandContext ctx, String site, String id) throws JSONException {
		return this.se(ctx, site, true, id);
	}

	// QUESTION_URL_PATTERN is a prefix of ANSWER_URL_PATTERN, so this has to go later
	@Command(".*" + QUESTION_URL_PATTERN + ".*")
	public JSONObject question(CommandContext ctx, String site, String id) throws JSONException {
		return this.se(ctx, site, false, id);
	}

	private JSONObject se(CommandContext ctx, String site, boolean isAnswer, String id) throws JSONException {
		try {
			final JSONObject json = getJSON(String.format(isAnswer ? API_ANSWER : API_QUESTION, id, site));
			if(json.has("error_id")) {
				return json.put("error", String.format("Code %d: %s: %s", json.getInt("error_id"), json.optString("error_name", "???"), json.optString("error_message", "???")));
			}

			final JSONArray items = json.getJSONArray("items");
			if(items.length() == 0) {
				return new JSONObject().put("error", "No post with ID " + id);
			}

			return items.getJSONObject(0);
		} catch(Exception e) {
			Log.e(e);
			return new JSONObject().put("error", "Problem parsing SE data");
		}
	}

	@View
	public void plainView(ViewContext ctx, JSONObject post) throws JSONException {
		final Calendar c = new GregorianCalendar();
		c.setTime(new Date(post.getInt("creation_date") * 1000L));
		final String created = String.format("%d %s %d", c.get(Calendar.DAY_OF_MONTH), c.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US), c.get(Calendar.YEAR));
		final boolean isAnswer = post.has("answer_id");

		final MessageBuilder builder = ctx.buildResponse();
		builder.add("#info %s (%s by %s on %s, +%d/-%d", new Object[] {
				StringEscapeUtils.unescapeHtml4(post.getString("title")),
				isAnswer ? "answered" : "asked",
				post.getJSONObject("owner").getString("display_name"),
				created,
				post.getInt("up_vote_count"),
				post.getInt("down_vote_count")
		});
		if(!isAnswer) {
			builder.add("#info , %s, %s", new Object[] {
					pluralize(post.getInt("view_count"), "view", "views"),
					pluralize(post.getInt("answer_count"), "answer", "answers")
			});
		}
		builder.add("#info )").send();
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
