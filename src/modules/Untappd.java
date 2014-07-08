package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;

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

	@Command("\\.drank (.*)")
	public void drank(Message message, String user) {
		try {
			Document page = Jsoup.connect("http://untappd.com/user/" + user).timeout(10000).get();
			Elements checkinLinks = page.select(".checkin").first().select(".text").select("a[href]");
			checkinLinks.remove(0);
			String[] info = map(checkinLinks.toArray(new Element[0]), new MapFunction<Element, String>() {
				@Override public String map(Element e) {
					return BOLD + e.text() + NORMAL;
				}
			});

			String output = info[0] + " by " + info[1];
			if (info.length > 2)
				output += " at " + info[2];

			this.bot.sendMessage(output);
		} catch (IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to retrieve page");
		}
	}

	@Override public String getFriendlyName() { return "Untappd"; }
	@Override public String getDescription() { return "Show what an untappd user last drank"; }
	@Override public String[] getExamples() { return new String[] { ".drank <untappd user>" }; }
}
