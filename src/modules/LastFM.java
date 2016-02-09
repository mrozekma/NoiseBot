package modules;

import static main.Utilities.fuzzyTimeAgo;

import java.io.IOException;

import debugging.Log;
import main.JSONArray;
import main.JSONObject;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import main.CommandContext;
import main.NoiseModule;
import main.ViewContext;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * LastFM module
 * @author Oh who remembers
 */

public class LastFM extends NoiseModule {
	@Command("\\.playing (.*)")
	public JSONObject playing(CommandContext ctx, String user) throws JSONException {
		try {
			Document page = Jsoup.connect("http://ws.audioscrobbler.com/1.0/user/" + user + "/recenttracks.rss").timeout(10000).get();
			Element song = page.select("item").first();

			// Surely no one is using this particular sequence in their track names...
			String[] parts = song.select("title").first().text().split(" \u2013 ");
			return new JSONObject().put("track", parts[1]).put("artist", parts[0]).put("date", song.select("pubDate").first().text());
		} catch (IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to retrieve page");
		}
	}

	@View(method = "playing")
	public void playingView(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("%s by %s %s", data.get("track"), data.get("artist"), fuzzyTimeAgo(data.getString("date")));
	}

	private JSONObject top(String user, String which) throws JSONException {
		try {
			Document page = Jsoup.connect("http://ws.audioscrobbler.com/2.0/user/" + user + "/" + which + ".xml").timeout(10000).get();
			final Elements elements = page.select(which).first().select("artist");
			final JSONObject[] rtn = new JSONObject[Math.min(10, elements.size())];
			for(int i = 0; i < rtn.length; i++) {
				final Element a = elements.get(i);
				rtn[i] = new JSONObject().put("name", a.select("name").first().text()).put("playcount", a.select("playcount").first().text());
			}
			return new JSONObject().put("top", rtn);
		} catch (IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to retrieve page");
		}
	}

	@View(method = {"top10", "top10week"})
	public void topView(ViewContext ctx, JSONObject data) throws JSONException {
		final JSONArray top = data.getJSONArray("top");
		final Object[] args = new Object[top.length() * 2];
		for(int i = 0; i < top.length(); i++) {
			final JSONObject o = top.getJSONObject(i);
			args[i * 2] = o.get("name");
			args[i * 2 + 1] = o.get("playcount");
		}
		ctx.respond("#([ - ] %s (%s))", (Object)args);
	}

	@Command("\\.top10 (.*)")
	public JSONObject top10(CommandContext ctx, String user) throws JSONException { return this.top(user, "topartists"); }

	@Command("\\.top10week (.*)")
	public JSONObject top10week(CommandContext ctx, String user) throws JSONException { return this.top(user, "weeklyartistchart"); }

	@Override public String getFriendlyName() { return "LastFM"; }
	@Override public String getDescription() { return "Show what a last.fm user last played"; }
	@Override public String[] getExamples() { return new String[] {
		".playing _user_",
		".top10 _user_ -- show _user_ 's top10 most played artists",
		".top10week _user_ -- show _user_ 's top10 most played artists for the week"
	}; }
}
