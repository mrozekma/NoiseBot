package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import debugging.Log;

import main.Message;
import main.NoiseModule;
import static main.Utilities.*;
import static panacea.Panacea.*;

/**
 * TVTropes
 *
 * @author Michael Mrozek
 *         Created Jun 28, 2014.
 */
public class TVTropes extends NoiseModule {
	private static final int MAXIMUM_MESSAGE_LENGTH = 400; // Approximately (512 bytes including IRC data)
	private static final String COLOR_WARNING = YELLOW;
	private static final String COLOR_ERROR = RED + REVERSE;

	@Command("\\.(?:trope) (.+)")
	public void tvtrope(Message message, String term) {
		this.sendEntry(term, "http://tvtropes.org/pmwiki/pmwiki.php/Main/" + urlEncode(fixTitle(term)), true);
	}

	@Command(".*(http://tvtropes.org/pmwiki/pmwiki.php/Main/(.+)).*")
	public void tvtropeLink(Message message, String url, String term) {
		this.sendEntry(urlDecode(term).replace("_", " "), url, false);
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
		return implode(words, "");
	}

	private void sendEntry(final String term, final String url, boolean includeLink) {
		final Document doc;
		try {
			doc = Jsoup.connect(url).get();
		} catch(IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to connect to TVTropes: " + e.getMessage());
			return;
		}

		final Element el = doc.select("#wikitext").first();
		if(el.text().startsWith("We don't have an article named")) {
			this.bot.sendMessage(COLOR_WARNING + "No entry for " + term);
			return;
		}
		el.select("div,span").remove();
		String text = el.text();
		if(text == null) {
			this.bot.sendMessage(COLOR_WARNING + "Unable to find post body");
			return;
		}

		text = truncateOnWord(text, MAXIMUM_MESSAGE_LENGTH - (includeLink ? (1 + utf8Size(url)) : 0));
		if(includeLink) {
			text += " " + url;
		}
		this.bot.sendMessage(text);
	}

	@Override public String getFriendlyName() {return "TVTropes";}
	@Override public String getDescription() {return "Returns the beginning of the TVTropes entry for the specified term";}
	@Override public String[] getExamples() {
		return new String[] {
				".trope _term_ -- Returns the beginning of the TVTropes article for _term_",
		};
	}
}
