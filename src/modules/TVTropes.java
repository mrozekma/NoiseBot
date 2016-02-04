package modules;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;

import main.CommandContext;
import main.JSONObject;
import main.NoiseModule;
import main.ViewContext;
import static main.Utilities.*;

/**
 * TVTropes
 *
 * @author Michael Mrozek
 *         Created Jun 28, 2014.
 */
public class TVTropes extends NoiseModule {
	private static final int MAXIMUM_MESSAGE_LENGTH = 400; // Approximately (512 bytes including IRC data), although we truncate on all protocols

	@Command("\\.trope (.+)")
	public JSONObject tvtrope(CommandContext ctx, String term) throws JSONException {
		return this.getEntry(term, "http://tvtropes.org/pmwiki/pmwiki.php/Main/" + urlEncode(fixTitle(term)));
	}

	@View(method = "tvtrope")
	public void plainTvtropeView(ViewContext ctx, JSONObject data) throws JSONException {
		this.plainView(ctx, data, true);
	}

	@Command(".*(http://tvtropes.org/pmwiki/pmwiki.php/Main/(.+)).*")
	public JSONObject tvtropeLink(CommandContext ctx, String url, String term) throws JSONException {
		return this.getEntry(urlDecode(term).replace("_", " "), url);
	}

	@View(method = "tvtropeLink")
	public void plainTvtropeLinkView(ViewContext ctx, JSONObject data) throws JSONException {
		this.plainView(ctx, data, false);
	}

	private static String fixTitle(String term) {
		// There's absolutely no consistency in TVTropes URLs, but I tried
		term = term.replaceAll("[^a-zA-Z0-9]", "");
		final String[] words = term.split(" ");
		for(int i = 0; i < words.length; i++) {
			if(!words[i].isEmpty() && Character.isLowerCase(words[i].charAt(0))) {
				words[i] = Character.toUpperCase(words[i].charAt(0)) + words[i].substring(1);
			}
		}
		return Arrays.stream(words).collect(Collectors.joining());
	}

	private JSONObject getEntry(final String term, final String url) throws JSONException {
		final Document doc;
		try {
			doc = Jsoup.connect(url).get();
		} catch(IOException e) {
			return new JSONObject().put("error", "Unable to connect to TVTropes: " + e.getMessage());
		}

		final Element el = doc.select("#wikitext").first();
		if(el.text().startsWith("We don't have an article named")) {
			return new JSONObject().put("warning", "No entry for " + term);
		}
		el.select("div,span").remove();
		String text = el.text();
		if(text == null) {
			return new JSONObject().put("warning", "Unable to find post body");
		}

		return new JSONObject().put("term", term).put("url", url).put("text", text);
	}

	private void plainView(ViewContext ctx, JSONObject data, boolean includeLink) throws JSONException {
		if(data.has("warning")) {
			ctx.respond("#warning %s", data.get("warning"));
			return;
		}

		String text = data.getString("text");
		final String url = data.getString("url");
		text = truncateOnWord(text, MAXIMUM_MESSAGE_LENGTH - (includeLink ? (1 + utf8Size(url)) : 0));
		if(includeLink) {
			text += " " + url;
		}
		ctx.respond("%s", text);
	}

	@Override public String getFriendlyName() {return "TVTropes";}
	@Override public String getDescription() {return "Returns the beginning of the TVTropes entry for the specified term";}
	@Override public String[] getExamples() {
		return new String[] {
				".trope _term_ -- Returns the beginning of the TVTropes article for _term_",
		};
	}
}
