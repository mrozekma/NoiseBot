package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;

import debugging.Log;

import main.Message;
import main.NoiseModule;
import static main.Utilities.*;

/**
 * Wikipedia
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Wikipedia extends NoiseModule {
	private static final int MAXIMUM_MESSAGE_LENGTH = 400; // Approximately (512 bytes including IRC data)
	private static final String COLOR_WARNING = YELLOW;
	private static final String COLOR_ERROR = RED + REVERSE;
	
	@Command("\\.(?:wik|wp) (.+)")
	public void wikipedia(Message message, String term) {
		this.sendEntry(term, "http://en.wikipedia.org/wiki/" + urlEncode(fixTitle(term)), true, true);
	}
	
	@Command(".*((?:https?:\\/\\/en\\.wikipedia\\.org|https:\\/\\/secure\\.wikimedia\\.org\\/wikipedia(?:\\/commons|\\/en))\\/wiki\\/((?:\\S+)(?::[0-9]+)?(?:\\/|\\/(?:[\\w#!:.?+=&%@!\\-\\/]))?)).*")
	public void wikipediaLink(Message message, String url, String term) {
		this.sendEntry(urlDecode(term).replace("_", " "), url, true, false);
	}
	
	@Command(".*\\[\\[([^\\]]+)]].*")
	public void wikipediaInline(Message message, String term) {
		this.sendEntry(term, "http://en.wikipedia.org/wiki/" + urlEncode(fixTitle(term)), false, true);
	}
	
	private static String fixTitle(String term) {
		String fixedTerm = term.replace(" ", "_");
		if(Character.isLowerCase(fixedTerm.charAt(0)))
			fixedTerm = Character.toUpperCase(fixedTerm.charAt(0)) + fixedTerm.substring(1);
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
	
	private void sendEntry(final String term, final String url, boolean showErrors, boolean includeLink) {
		final Document doc;
		try {
			doc = Jsoup.connect(url).get();
		} catch(IOException e) {
			if(showErrors) {
				if(e.getMessage().contains("404 error loading URL")) {
					this.bot.sendMessage(COLOR_WARNING + "No entry for " + term);
				} else {
					this.bot.sendMessage(COLOR_ERROR + "Unable to connect to Wikipedia: " + e.getMessage());
				}
			}
			return;
		}
		
		String text = selectEntryText(term, url, doc);
		if(text == null) {
			if(showErrors) {
				this.bot.sendMessage(COLOR_WARNING + "Unable to find post body");
			}
			return;
		}
		text = truncateOnWord(text, MAXIMUM_MESSAGE_LENGTH - (includeLink ? (1 + url.length()) : 0));
		if(includeLink) {
			text += " " + url;
		}
		this.bot.sendMessage(text);
	}
	
	@Command("\\.featured")
	public void featured(Message message) {
		// From http://en.wikipedia.org/w/api.php?action=query&prop=revisions&action=featuredfeed&feed=featured; not going to bother parsing unless it changes
		final Calendar now = new GregorianCalendar();
		final String url = String.format("http://en.wikipedia.org/wiki/Special:FeedItem/featured/%04d%02d%02d000000/en", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));

		final Document doc;
		try {
			Log.v("Loading from %s", url);
			doc = Jsoup.connect(url).get();
		} catch(IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to connect to Wikipedia: " + e.getMessage());
			return;
		}

		final Element e = doc.select("b:matchesOwn(Full.article)").first();
		if(e != null) {
			final Element a = e.parent();
			if(a != null && a.tagName().equals("a")) {
				final String articleURL = a.attr("href");
				if(articleURL.startsWith("/wiki/")) {
					final String term = articleURL.substring("/wiki/".length());
					this.wikipedia(message, urlDecode(term).replace("_", " "));
					return;
				}
			}
		}

		this.bot.sendMessage(COLOR_ERROR + "No title found");
	}

	@Override public String getFriendlyName() {return "Wikipedia";}
	@Override public String getDescription() {return "Returns the beginning of the wikipedia article for the specified term";}
	@Override public String[] getExamples() {
		return new String[] {
				".wik _term_ -- Returns the beginning of the wikipedia article for _term_",
				".wp _term_ -- Same as above",
				"[_term_] -- Same as above (can appear in the middle of a line)",
				".featured -- Show the wikipedia definition for the current featured article"
		};
	}
}
