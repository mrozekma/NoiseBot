package modules;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import main.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Untappd module
 *
 * @author Michael Auchter
 */

public class Untappd extends NoiseModule {
	private static final DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss ZZZZZ");
	private static final Pattern ratingPattern = Pattern.compile("r([0-9]{3})");

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("element", Style.BOLD);
			put("rating", Style.BOLD);
		}};
	}

	// Parse an RFC822-esque date/time and return a string indicating, fuzzily, how long ago that was
	public static String fuzzyTimeAgo(String rfc822date)
	{
		final DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss ZZZZZ");
		long ms = 0;
		try {
			ms = new Date().getTime() - dateFormat.parse(rfc822date).getTime();
		} catch (ParseException pe) {
			return "";
		}

		if (ms < 0)
			return "(sometime in the future...)";
		if (ms < 1000)
			return "(now)";

		StringBuilder s = new StringBuilder("");

		class FuzzyTime {
			FuzzyTime(StringBuilder in, long milliseconds)
			{
				this.in = in;
				this.milliseconds = milliseconds;
			}

			StringBuilder in;
			final long milliseconds;

			public void xlate(TimeUnit unit, String unitStr) {
				if (!in.toString().equals(""))
					return;

				final long duration = unit.convert(milliseconds, TimeUnit.MILLISECONDS);
				if (duration > 1)
					unitStr += "s";

				if (duration > 0)
					in.append("(").append(duration).append(" ").append(unitStr).append(" ago)");
			}
		};

		FuzzyTime f = new FuzzyTime(s, ms);
		f.xlate(TimeUnit.DAYS,    "day");
		f.xlate(TimeUnit.HOURS,   "hour");
		f.xlate(TimeUnit.MINUTES, "minute");
		f.xlate(TimeUnit.SECONDS, "second");

		return s.toString();
	}

	public static String getRatingString(int rating) {
		final StringBuilder rtn = new StringBuilder();
		for(; rating >= 100; rating -= 100) {
			rtn.append("*");
		}
		switch(rating) {
		case 25:
			rtn.append("\u00bc");
			break;
		case 50:
			rtn.append("\u00bd");
			break;
		case 75:
			rtn.append("\u00be");
			break;
		}
		return rtn.toString();
	}

	@Command("\\.drank (.*)")
	public JSONObject drank(Message message, String user) throws JSONException {
		try {
			Document page = Jsoup.connect("http://untappd.com/user/" + user).timeout(10000).get();
			Element checkin = page.select(".checkin").first();
			if(checkin == null) {
				return new JSONObject();
			}
			final String[] elements = checkin.select("p").select(".text").select("a[href]").stream()
				.skip(1)
				.map(Element::text)
				.toArray(String[]::new);

			final JSONObject rtn = new JSONObject().put("user", user).put("texts", elements);

			final Element ratingElem = checkin.select("span[class^=rating]").first();
			if(ratingElem != null) {
				for(String c : ratingElem.classNames()) {
					final Matcher m = ratingPattern.matcher(c);
					if(m.matches()) {
						rtn.put("rating", Integer.parseInt(m.group(1)));
						break;
					}
				}
			}

			rtn.put("when", checkin.select(".time").first().text());
			return rtn;
		} catch(IOException e) {
			return new JSONObject().put("error", "Unable to retrieve page");
		}
	}

	@View(method = "drank")
	public void plainDrankView(Message message, JSONObject data) throws JSONException {
		if(!data.has("texts")) {
			message.respond("No results");
			return;
		}

		final MessageBuilder builder = message.buildResponse();
		builder.add("#([ - ] #element %s)", new Object[] {data.getStringArray("texts")});
		if(data.has("rating")) {
			builder.add(" - %(#rating)s", new Object[] {getRatingString(data.getInt("rating"))});
		}
		builder.add(" - %s", new Object[] {fuzzyTimeAgo(data.getString("when"))});
		builder.send();
	}

	@Command("\\.drankstats (.*)")
	public JSONObject drankstats(Message m, String user) throws JSONException {
		try {
			Document page = Jsoup.connect("http://untappd.com/user/" + user).timeout(10000).get();
			Elements stats = page.select(".stats").first().select("a");
			final JSONObject rtn = new JSONObject().put("user", user);

			for(int i = 0; i < stats.size(); i++) {
				final String title = stats.get(i).select(".title").text();
				final String value = stats.get(i).select(".stat").text();
				rtn.append("stats", new JSONObject().put("title", title).put("value", value));
			}
//			for (int i = 0; i < titles.size(); i++) {
//				s += titles.get(i).text() + ": " + stats.get(i).text();
//				if ((i + 1) != titles.size())
//					s += ", ";
//			}
			return rtn;
		} catch(IOException e) {
			return new JSONObject().put("error", "Unable to retrieve page");
		}
	}

	@View(method = "drankstats")
	public void plainDrankStatsView(Message message, JSONObject data) throws JSONException {
		final JSONArray stats = data.getJSONArray("stats");
		final List<String> args = new LinkedList<>();
		for(int i = 0; i < stats.length(); i++) {
			args.add(stats.getJSONObject(i).getString("title"));
			args.add(stats.getJSONObject(i).getString("value"));
		}
		message.respond("#([, ] %s: %s)", (Object)args.toArray());
	}

	@Override public String getFriendlyName() { return "Untappd"; }
	@Override public String getDescription() { return "Show what an untappd user last drank"; }
	@Override public String[] getExamples() { return new String[] { ".drank _user_", ".drankstats _user" }; }
}
