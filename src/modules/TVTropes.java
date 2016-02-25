package modules;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;

import main.CommandContext;
import main.JSONObject;
import static main.Utilities.*;

/**
 * TVTropes
 *
 * @author Michael Mrozek
 *         Created Jun 28, 2014.
 */
public class TVTropes extends WebLookupModule {
	@Command("\\.trope (.+)")
	public JSONObject tvtrope(CommandContext ctx, String term) throws JSONException {
		return this.lookup(term, "http://tvtropes.org/pmwiki/pmwiki.php/Main/" + urlEncode(fixTitle(term)));
	}

	@Command(".*(http://tvtropes.org/pmwiki/pmwiki.php/Main/(.+)).*")
	public JSONObject tvtropeLink(CommandContext ctx, String url, String term) throws JSONException {
		return this.lookup(urlDecode(term).replace("_", " "), url);
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

	@Override protected String getBody(String term, String url, Document doc) throws EntryNotFound, BodyNotFound {
		final Element el = doc.select(".page-content").first();
		if(el == null || el.text().startsWith("We don't have an article named")) {
			throw new EntryNotFound();
		}
		el.select("div,span").remove();
		String text = el.text();
		if(text == null) {
			throw new BodyNotFound();
		}
		return text;
	}

	@Override protected String getThumbnailURL() {
		return "https://s3-us-west-2.amazonaws.com/s.cdpn.io/291322/tv-logo.png";
	}

	@Override protected boolean shouldIncludeLink(Method commandMethod) {
		final String name = commandMethod.getName();
		return name.equals("tvtrope");
	}

	@Override public String getFriendlyName() {return "TVTropes";}
	@Override public String getDescription() {return "Returns the beginning of the TVTropes entry for the specified term";}
	@Override public String[] getExamples() {
		return new String[] {
				".trope _term_ -- Returns the beginning of the TVTropes article for _term_",
		};
	}
}
