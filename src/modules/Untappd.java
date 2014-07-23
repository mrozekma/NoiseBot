package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;
import java.util.Date;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import panacea.MapFunction;

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

	private static String fuzzyTimeAgo(long ms)
	{
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
			Elements checkinLinks = checkin.select(".text").select("a[href]");
			checkinLinks.remove(0);
			String[] info = map(checkinLinks.toArray(new Element[0]), new MapFunction<Element, String>() {
				@Override public String map(Element e) {
					return BOLD + e.text() + NORMAL;
				}
			});

			String output = info[0] + " by " + info[1];
			if (info.length > 2)
				output += " at " + info[2];

			String drankTime = "";
			try {
				final long ago = new Date().getTime() - dateFormat.parse(checkin.select(".time").first().text()).getTime();
				drankTime = fuzzyTimeAgo(ago);
			} catch (ParseException pe) { /* Eh, whatever */ }

			this.bot.sendMessage(output + " " + drankTime);
		} catch (IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to retrieve page");
		}
	}

	@Override public String getFriendlyName() { return "Untappd"; }
	@Override public String getDescription() { return "Show what an untappd user last drank"; }
	@Override public String[] getExamples() { return new String[] { ".drank <untappd user>" }; }
}
