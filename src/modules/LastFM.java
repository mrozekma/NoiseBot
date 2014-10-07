package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.util.stream.Collectors;
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

	private void top(String user, String which) {
		try {
			Document page = Jsoup.connect("http://ws.audioscrobbler.com/2.0/user/" + user + "/" + which + ".xml").timeout(10000).get();
			String top = page.select(which).first().select("artist").stream()
				.limit(10)
				.map(a -> a.select("name").first().text() + " (" + a.select("playcount").first().text() + ")")
				.collect(Collectors.joining(" - ")); // Just join by " - " for now since Java lacks zipWith on Streams :/

			this.bot.sendMessage(top);
		} catch (IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to retrieve page");
		}
	}

	@Command("\\.top10 (.*)")
	public void top10(Message message, String user) { top(user, "topartists"); }

	@Command("\\.top10week (.*)")
	public void top10week(Message message, String user) { top(user, "weeklyartistchart"); }

	@Override public String getFriendlyName() { return "LastFM"; }
	@Override public String getDescription() { return "Show what a last.fm user last played"; }
	@Override public String[] getExamples() { return new String[] {
		".playing <last.fm user>",
		".top10 <last.fm user> -- show user's top10 most played artists",
		".top10week <last.fm user> -- show user's top10 most played artists for the week"
	}; }
}
