package modules;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import main.*;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;

import debugging.Log;

import static main.Utilities.*;

/**
 * Wikia
 *
 * @author Michael Mrozek
 *         Created Jan 16, 2018.
 */
public class Wikia extends WebLookupModule implements Serializable {
	private final Map<String, String> aliases = new HashMap<>();

	@Command(".*(https?:\\/\\/[a-zA-Z0-9_-]+\\.wikia\\.com\\/wiki\\/((?:\\S+)(?::[0-9]+)?(?:\\/|\\/(?:[\\w#!:.?+=&%@!\\-\\/]))?)).*")
	public JSONObject wikiaLink(CommandContext ctx, String url, String term) throws JSONException {
		return this.lookup(urlDecode(term).replace("_", " "), url);
	}

	@Command(".*\\[\\[([^:]+):([^\\]]+)]].*")
	public JSONObject wikiaInline(CommandContext ctx, String site, String term) throws JSONException {
		return this.lookup(term, this.urlFromTerm(site, term));
	}

	@Override protected String getThumbnailURL() {
		return "https://vignette.wikia.nocookie.net/central/images/b/b0/CMP_Wikia_logo_brackets.png";
	}

	@Override protected boolean shouldIncludeLink(Method commandMethod) {
		final String name = commandMethod.getName();
		return name.equals("wikiaInline");
	}

	@Override protected boolean shouldShowErrors(Method commandMethod) {
		final String name = commandMethod.getName();
		return name.equals("wikiaLink");
	}

	private static String fixTitle(String term) {
		String fixedTerm = term.replace(" ", "_");
		if(Character.isLowerCase(fixedTerm.charAt(0))) {
			fixedTerm = Character.toUpperCase(fixedTerm.charAt(0)) + fixedTerm.substring(1);
		}
		return fixedTerm;
	}

	private String urlFromTerm(String site, String term) {
		if(this.aliases.containsKey(site)) {
			site = this.aliases.get(site);
		}
		return "http://" + site + ".wikia.com/wiki/" + urlEncode(fixTitle(term));
	}

	@Override protected String getBody(final String term, final String url, final Document doc) throws BodyNotFound {
		System.out.println(url);
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
		if (el == null && anchor != null) {
			el = doc.select("span.mw-headline#" + anchor).first(); // <span>
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
			return el.text();
		} else {
			throw new BodyNotFound();
		}
	}

	@Command("\\.wikiaalias ([a-zA-Z0-9_-]+)[ =]([^: ]+)")
	public JSONObject alias(CommandContext ctx, String alias, String site) throws JSONException {
		final String orig = this.aliases.get(alias);
		this.aliases.put(alias, site);
		this.save();
		final JSONObject rtn = new JSONObject().put("action", "alias").put("site", site).put("alias", alias);
		if(orig != null) {
			rtn.put("original", orig);
		}
		return rtn;
	}

	@Command("\\.wikiaunalias ([a-zA-Z0-9_-]+)")
	public JSONObject unalias(CommandContext ctx, String alias) throws JSONException {
		final String site = this.aliases.remove(alias);
		final JSONObject rtn = new JSONObject().put("action", "unalias").put("alias", alias);
		if(site == null) {
			rtn.put("existed", false).put("error", "Alias not found: " + alias);
		} else {
			this.save();
			rtn.put("existed", true).put("site", site);
		}
		return rtn;
	}

	@View(method = "alias")
	public void viewAlias(ViewContext ctx, JSONObject data) throws JSONException {
		final MessageBuilder builder = ctx.buildResponse();
		builder.add("Added alias %(#bold)s for %(#bold)s", new Object[] {data.getString("alias"), data.getString("site")});
		if(data.has("original")) {
			builder.add(" (replaced %(#bold)s)", new Object[] {data.getString("original")});
		}
		builder.send();
	}

	@View(method = "unalias")
	public void viewUnalias(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("Removed alias %(#bold)s for %(#bold)s", data.getString("alias"), data.getString("site"));
	}

	@Override public String getFriendlyName() {return "Wikia";}
	@Override public String getDescription() {return "Returns the beginning of the wikia article for the specified term";}
	@Override public String[] getExamples() {
		return new String[] {
				"[[_site_:_term_]] -- Returns the beginning of the _site_ wikia article for _term_",
				".wikiaalias _alias_ _site_ -- Allow _alias_ to be used in place of _site_ in lookups",
				".wikiaunalias _alias_ -- Remove the _alias_ alias for a site",
		};
	}
}
