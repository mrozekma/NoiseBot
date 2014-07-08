package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import main.Message;
import main.NoiseModule;

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
			Document page = Jsoup.connect("http://untappd.com/user/" + user).timeout(1000).get();
			String checkin = page.select(".checkin").first().select(".text").first().text();
			this.bot.sendMessage(checkin);
		} catch (IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to retrieve page");
		}
	}

	@Override public String getFriendlyName() { return "Untappd"; }
	@Override public String getDescription() { return "Show what an untappd user last drank"; }
	@Override public String[] getExamples() { return new String[] { ".drank <untappd user>" }; }
}
