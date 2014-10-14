package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import main.Message;
import main.NoiseModule;
import static panacea.Panacea.*;

/**
 * Untappd module
 *
 * @author Michael Auchter
 */

public class Untappd extends NoiseModule {
	private static final String COLOR_ERROR = RED + REVERSE;
	private static final DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss ZZZZZ");
	private static final Map<String, String> RATING_MAP = new HashMap<String, String>() {{
		put("r05", "½");
		put("r15", "*½");
		put("r25", "**½");
		put("r35", "***½");
		put("r45", "****½");
		put("r10", "*");
		put("r20", "**");
		put("r30", "***");
		put("r40", "****");
		put("r50", "*****");
	}};

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

	@Command("\\.drank (.*)")
	public void drank(Message message, String user) {
		try {
			Document page = Jsoup.connect("http://untappd.com/user/" + user).timeout(10000).get();
			Element checkin = page.select(".checkin").first();
			String output = checkin.select("p").select(".text").select("a[href]").stream()
				.skip(1)
				.map(e -> BOLD + e.text() + NORMAL)
				.collect(Collectors.joining(" - ")); // Just join by " - " for now since Java lacks zipWith on Streams :/

			String rating = "";
			final Element ratingElem = checkin.select("span[class^=rating]").first();
			if (ratingElem != null)
				for (String c : ratingElem.classNames()) {
					if (RATING_MAP.containsKey(c)) {
						rating = " - " + BOLD + RATING_MAP.get(c) + NORMAL;
						break;
					}
				}

			this.bot.sendMessage(output + rating + " " + fuzzyTimeAgo(checkin.select(".time").first().text()));
		} catch (IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to retrieve page");
		}
	}

	@Command("\\.drankstats (.*)")
	public void drankstats(Message m, String user) {
		try {
			Document page = Jsoup.connect("http://untappd.com/user/" + user).timeout(10000).get();
			Elements statDiv = page.select(".stats").first().select("span");
			Elements titles = statDiv.select(".title");
			Elements stats = statDiv.select(".stat");
			String s = "";
			for (int i = 0; i < titles.size(); i++) {
				s += titles.get(i).text() + ": " + stats.get(i).text();
				if ((i + 1) != titles.size())
					s += ", ";
			}

			this.bot.sendMessage(s);
		} catch (IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to retrieve page");
		}
	}

	@Override public String getFriendlyName() { return "Untappd"; }
	@Override public String getDescription() { return "Show what an untappd user last drank"; }
	@Override public String[] getExamples() { return new String[] { ".drank <untappd user>" }; }
}
