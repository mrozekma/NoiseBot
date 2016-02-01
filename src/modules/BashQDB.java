package modules;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import main.JSONObject;
import main.Style;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import debugging.Log;

import main.Message;
import main.NoiseModule;

/**
 * BashQDB
 *
 * @author Michael Mrozek
 *         Created Jul 5, 2014.
 */
public class BashQDB extends NoiseModule {
	private static class Quote implements Iterable<String> {
		public final int id;
		public final String[] lines;
		public final int score;

		public Quote(int id, String[] lines, int score) {
			this.id = id;
			this.lines = lines;
			this.score = score;
		}

		@Override public Iterator<String> iterator() {
			return Arrays.asList(this.lines).iterator();
		}
	}

	private static final String URI = "http://bash.org";
	private static final int TIMEOUT = 5; // seconds

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("quote", Style.CYAN);
		}};
	}

	@Command(".*" + URI + "/?\\?([0-9]+).*")
	public JSONObject link(Message message, int id) throws JSONException {
		return this.show(message, id);
	}

	@Command("\\.bash ([0-9]+)")
	public JSONObject show(Message message, int id) throws JSONException {
		Log.i("Getting quote %d", id);
		final Document doc;
		try {
			doc = Jsoup.connect(String.format("%s/?%d", URI, id)).timeout(TIMEOUT * 1000).get();
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to connect to bash.org: " + e.getMessage());
		}

		final Element bodytext = doc.select(".bodytext").first();
		if(bodytext != null && bodytext.text().matches("Quote #([0-9]+) does not exist.")) {
			return new JSONObject().put("error", "Quote not found");
		}

		final Element qt = doc.select("p.qt").first();
		if(qt == null) {
			return new JSONObject().put("error", "Unable to find quote block");
		}

		int score = 0;
		final Element quote = doc.select("p.quote").first();
		if(quote != null) {
			for(TextNode t : quote.textNodes()) {
				if(t.text().matches("\\([0-9]+\\)")) {
					score = Integer.parseInt(t.text().substring(1, t.text().length() - 1));
					break;
				}
			}
		}

		final String[] lines = qt.html().replace("&nbsp;", " ").split("<br />");
		for(int i = 0; i < lines.length; i++) {
			lines[i] = Jsoup.parse(lines[i]).text();
		}
		return new JSONObject().put("id", id).put("score", score).put("lines", lines);
	}

	@View
	public void plainView(Message message, JSONObject data) throws JSONException {
		for(String line : data.getStringArray("lines")) {
			message.respond("#quote %s", line);
		}
	}

	@Override public String getFriendlyName() {return "BashQDB";}
	@Override public String getDescription() {return "Displays quotes from bash.org";}
	@Override public String[] getExamples() {
		return new String[] {
				".bash _id_ -- Shows quote _id_",
				"http://bash.org/?id -- Same as .bash"
		};
	}
}
