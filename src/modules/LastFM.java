package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import main.Message;
import main.NoiseModule;
import static panacea.Panacea.*;
import static modules.Untappd.fuzzyTimeAgo;

/**
 * LastFM module
 * @author Oh who remembers
 */

public class LastFM extends NoiseModule {
	private static final String COLOR_ERROR = RED + REVERSE;

	@Command("\\.playing (.*)")
	public void playing(Message message, String user) {
		try {
			Document page = Jsoup.connect("http://ws.audioscrobbler.com/1.0/user/" + user + "/recenttracks.rss").timeout(10000).get();
			Element song = page.select("item").first();

			// Surely no one is using this particular sequence in their track names...
			String[] parts = song.select("title").first().text().split(" \u2013 ");
			final String track = BOLD + parts[1] + NORMAL;
			final String artist = BOLD + parts[0] + NORMAL;
			final String date = song.select("pubDate").first().text();

			this.bot.sendMessage(track + " by " + artist + " " + fuzzyTimeAgo(date));
		} catch (IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to retrieve page");
		}
	}

	@Override public String getFriendlyName() { return "LastFM"; }
	@Override public String getDescription() { return "Show what a last.fm user last played"; }
	@Override public String[] getExamples() { return new String[] { ".playing <last.fm user>" }; }
}
