package modules;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.GregorianCalendar;

import main.JSONObject;
import org.json.JSONException;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;

import debugging.Log;

import main.CommandContext;
import main.NoiseModule;
import main.ViewContext;
import static main.Utilities.*;

/**
 * Wikipedia
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Wikipedia extends NoiseModule {
	private static final int MAXIMUM_MESSAGE_LENGTH = 400; // Approximately (512 bytes including IRC data), although we truncate on all protocols

	@Command("\\.(?:wik|wp) (.+)")
	public JSONObject wikipedia(CommandContext ctx, String term) throws JSONException {
		return this.getEntry(term, "http://en.wikipedia.org/wiki/" + urlEncode(fixTitle(term)));
	}

	@View(method = "wikipedia")
	public void plainWikipediaView(ViewContext ctx, JSONObject data) throws JSONException {
		this.plainView(ctx, data, true, true);
	}
	
	@Command(".*((?:https?:\\/\\/en\\.wikipedia\\.org|https:\\/\\/secure\\.wikimedia\\.org\\/wikipedia(?:\\/commons|\\/en))\\/wiki\\/((?:\\S+)(?::[0-9]+)?(?:\\/|\\/(?:[\\w#!:.?+=&%@!\\-\\/]))?)).*")
	public JSONObject wikipediaLink(CommandContext ctx, String url, String term) throws JSONException {
		return this.getEntry(urlDecode(term).replace("_", " "), url);
	}

	@View(method = "wikipediaLink")
	public void plainWikipediaLinkView(ViewContext ctx, JSONObject data) throws JSONException {
		this.plainView(ctx, data, true, false);
	}

	@Command(".*\\[\\[([^\\]]+)]].*")
	public JSONObject wikipediaInline(CommandContext ctx, String term) throws JSONException {
		return this.getEntry(term, "http://en.wikipedia.org/wiki/" + urlEncode(fixTitle(term)));
	}

	@View(method = "wikipediaInline")
	public void plainWikipediaInlineView(ViewContext ctx, JSONObject data) throws JSONException {
		this.plainView(ctx, data, false, true);
	}

	private static String fixTitle(String term) {
		String fixedTerm = term.replace(" ", "_");
		if(Character.isLowerCase(fixedTerm.charAt(0))) {
			fixedTerm = Character.toUpperCase(fixedTerm.charAt(0)) + fixedTerm.substring(1);
		}
		return fixedTerm;
	}

	private String selectEntryText(final String term, final String url, final Document doc) {
		String anchor = null;
		try {
			anchor = new URL(url).getRef();
		} catch(MalformedURLException e) {
			Log.e(e);
		}

		Element el = null;
		if (term.contains("File:")) {
			if (el == null) { // Image description on a Commons page
				el = doc.select("th#fileinfotpl_desc + td p").first();
			}
			if (el == null) { // Alternative image description
				el = doc.select("div#shared-image-desc > p").first();
			}
		}
		if (el == null && anchor != null) {
			el = doc.select("span.mw-headline#" + anchor).first(); // <span>
			el = el.parent(); // <h2>
			while((el = el.nextElementSibling()) != null) {
				if(el.tagName().equals("p")) {
					break;
				} else if(el.hasClass("mw-headline")) {
					el = null;
					break;
				}
			}
		}
		if (el == null) { // First paragraph of any other page
			el = doc.select("div#mw-content-text > p").first();
		}
		if (el == null) {
			el = doc.select("div#bodyContent p").first();
		}
		return el == null ? null : el.text();
	}
	
	private JSONObject getEntry(final String term, final String url) throws JSONException {
		final Document doc;
		try {
			doc = Jsoup.connect(url).get();
		} catch(HttpStatusException e) {
			if(e.getStatusCode() == 404) {
				return new JSONObject().put("warning", "No entry for " + term);
			} else {
				Log.e(e);
				return new JSONObject().put("warning", "Unable to connect to Wikipedia: " + e.getMessage());
			}
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("warning", "Unable to connect to Wikipedia: " + e.getMessage());
		}
		
		String text = selectEntryText(term, url, doc);
		if(text == null) {
			return new JSONObject().put("warning", "Unable to find post body");
		}

		return new JSONObject().put("term", term).put("url", url).put("text", text);
	}

	private void plainView(ViewContext ctx, JSONObject data, boolean showErrors, boolean includeLink) throws JSONException {
		if(data.has("warning")) {
			if(showErrors) {
				ctx.respond("#error %s", data.get("warning"));
			}
			return;
		}
		final String url = data.getString("url");
		String text = truncateOnWord(data.getString("text"), MAXIMUM_MESSAGE_LENGTH - (includeLink ? (1 + utf8Size(url)) : 0));
		if(includeLink) {
			text += " " + url;
		}
		ctx.respond("%s", text);
	}
	
	@Command("\\.featured")
	public JSONObject featured(CommandContext ctx) throws JSONException {
		// From http://en.wikipedia.org/w/api.php?action=query&prop=revisions&action=featuredfeed&feed=featured; not going to bother parsing unless it changes
		final Calendar now = new GregorianCalendar();
		final String url = String.format("http://en.wikipedia.org/wiki/Special:FeedItem/featured/%04d%02d%02d000000/en", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));

		final Document doc;
		try {
			Log.v("Loading from %s", url);
			doc = Jsoup.connect(url).get();
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to connect to Wikipedia: " + e.getMessage());
		}

		final Element e = doc.select("b:matchesOwn(Full.article)").first();
		if(e != null) {
			final Element a = e.parent();
			if(a != null && a.tagName().equals("a")) {
				final String articleURL = a.attr("href");
				if(articleURL.startsWith("/wiki/")) {
					final String term = articleURL.substring("/wiki/".length());
					return this.wikipedia(ctx, urlDecode(term).replace("_", " "));
				}
			}
		}

		return new JSONObject().put("error", "No title found");
	}

	@View(method = "featured")
	public void plainFeaturedView(ViewContext ctx, JSONObject data) throws JSONException {
		this.plainView(ctx, data, true, true);
	}

	@Override public String getFriendlyName() {return "Wikipedia";}
	@Override public String getDescription() {return "Returns the beginning of the wikipedia article for the specified term";}
	@Override public String[] getExamples() {
		return new String[] {
				".wik _term_ -- Returns the beginning of the wikipedia article for _term_",
				".wp _term_ -- Same as above",
				"[[_term_]] -- Same as above (can appear in the middle of a line)",
				".featured -- Show the wikipedia definition for the current featured article"
		};
	}
}
