package modules;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.GregorianCalendar;

import main.*;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;

import debugging.Log;

import static main.Utilities.*;

/**
 * Wikipedia
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Wikipedia extends WebLookupModule {
	final String API_URL = "https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&utf8=1&srsearch=intitle%%3A%s";

	@Command("(?:\\.wik|\\.wp|:wikipedia:) (.+)")
	public JSONObject wikipedia(CommandContext ctx, String term) throws JSONException {
		return this.lookup(term, urlFromTerm(term));
	}

	@Command(".*((?:https?:\\/\\/en\\.wikipedia\\.org|https:\\/\\/secure\\.wikimedia\\.org\\/wikipedia(?:\\/commons|\\/en))\\/wiki\\/((?:\\S+)(?::[0-9]+)?(?:\\/|\\/(?:[\\w#!:.?+=&%@!\\-\\/]))?)).*")
	public JSONObject wikipediaLink(CommandContext ctx, String url, String term) throws JSONException {
		return this.lookup(urlDecode(term).replace("_", " "), url);
	}

	@Command(".*\\[\\[([^\\]]+)]].*")
	public JSONObject wikipediaInline(CommandContext ctx, String term) throws JSONException {
		return this.lookup(term, urlFromTerm(term));
	}

	@Override protected String getThumbnailURL() {
		return "https://upload.wikimedia.org/wikipedia/commons/6/63/Wikipedia-logo.png";
	}

	@Override protected boolean shouldIncludeLink(Method commandMethod) {
		final String name = commandMethod.getName();
		return name.equals("wikipedia") || name.equals("wikipediaInline") || name.equals("featured");
	}

	@Override protected boolean shouldShowErrors(Method commandMethod) {
		final String name = commandMethod.getName();
		return !name.equals("wikipediaInline");
	}

	@Override public JSONObject lookup(String term, String url) throws JSONException {
		JSONObject rtn = super.lookup(term, url);
		// If the entry isn't found, try searching for it with the API
		if(rtn.has("fail_type") && rtn.get("fail_type").equals("no_entry")) {
			try {
				final URLConnection apiConnection = new URL(String.format(API_URL, urlEncode(term.toLowerCase()))).openConnection();
				apiConnection.setRequestProperty("User-Agent", "NoiseBot (https://github.com/mrozekma/NoiseBot/; noisebot@mrozekma.com)");
				final JSONObject apiResult = getJSON(apiConnection);
				final JSONArray results = apiResult.getJSONObject("query").getJSONArray("search");
				for(int i = 0; i < results.length(); i++) {
					final String title = results.getJSONObject(i).getString("title");
					if(title.equalsIgnoreCase(term)) {
						rtn = super.lookup(title, urlFromTerm(title));
						break;
					}
				}
			} catch(IOException | JSONException e) {
				// Log and just return the original lookup attempt
				Log.e(e);
			}
		}
		return rtn;
	}

	private static String fixTitle(String term) {
		String fixedTerm = term.replace(" ", "_");
		if(Character.isLowerCase(fixedTerm.charAt(0))) {
			fixedTerm = Character.toUpperCase(fixedTerm.charAt(0)) + fixedTerm.substring(1);
		}
		return fixedTerm;
	}

	private static String urlFromTerm(String term) {
		return "http://en.wikipedia.org/wiki/" + urlEncode(fixTitle(term));
	}

	@Override protected String getBody(final String term, final String url, final Document doc) throws BodyNotFound {
		String anchor = null;
		try {
			anchor = new URL(url).getRef();

			// We don't want references that are empty or start with '/' (hash URLs)
			if(anchor != null && (anchor.isEmpty() || anchor.charAt(0) == '/')) {
				anchor = null;
			}
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
			el = doc.select("span.mw-headline[id=" + anchor + "]").first(); // <span>
			if(el != null) {
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
		}
		if (el == null) { // First paragraph of any other page
			el = doc.select("div#mw-content-text > p").first();
		}
		if (el == null) {
			el = doc.select("div#bodyContent p").first();
		}

		if (el != null) {
			return el.text().replaceAll("\\[[0-9]+\\]", "");
		} else {
			throw new BodyNotFound();
		}
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

	@Override
	public void slackView(ViewContext ctx, JSONObject data) throws JSONException {
		if(ctx.wasCommandTriggered() && ctx.getCommandMethod().getName().equals("wikipediaLink")) {
			// Slack has mandatory Wikipedia unfurling for the moment, so do nothing
			return;
		}

		super.slackView(ctx, data);
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
