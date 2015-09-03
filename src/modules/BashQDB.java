package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

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

	private static class ParseException extends Exception {
		public ParseException(String message) {super(message);}
	}

	private static final String URI = "http://bash.org";
	private static final String COLOR_ERROR = RED;
	private static final String COLOR_QUOTE = CYAN;
	private static final int TIMEOUT = 5; // seconds

	@Command(".*" + URI + "/?\\?([0-9]+).*")
	public void link(Message message, int id) {
		this.show(message, id);
	}

	@Command("\\.bash ([0-9]+)")
	public void show(Message message, int id) {
		try {
			for(String line : getQuote(id)) {
				this.bot.sendMessage(COLOR_QUOTE + line);
			}
		} catch(ParseException e) {
			this.bot.sendMessage(COLOR_ERROR + e.getMessage());
		} catch(IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to connect to bash.org");
			Log.e(e);
		}
	}

	private static Quote getQuote(int id) throws IOException, ParseException {
		Log.i("Getting quote %d", id);
		final Document doc = Jsoup.connect(String.format("%s/?%d", URI, id)).timeout(TIMEOUT * 1000).get();

		final Element bodytext = doc.select(".bodytext").first();
		if(bodytext != null && bodytext.text().matches("Quote #([0-9]+) does not exist.")) {
			throw new ParseException("Quote not found");
		}

		final Element qt = doc.select("p.qt").first();
		if(qt == null) {
			throw new ParseException("Unable to find quote block");
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
		final String[] rtn = new String[lines.length];
		for(int i = 0; i < lines.length; i++) {
			rtn[i] = Jsoup.parse(lines[i]).text();
		}
		return new Quote(id, rtn, score);
	}

	@Override public String getFriendlyName() {return "BashQDB";}
	@Override public String getDescription() {return "Displays quotes from bash.org";}
	@Override public String[] getExamples() {
		return new String[] {
				".bash _id_ -- Shows quote _id_",
				"http://bash.org/?_id_ -- Same as .bash"
		};
	}
}
