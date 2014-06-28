package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import debugging.Log;

import main.Message;
import main.NoiseModule;
import static main.Utilities.formatSeconds;

/**
 * Youtube
 *
 * @author Michael Mrozek
 *         Created Jun 17, 2009.
 */
public class Youtube extends NoiseModule {
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");

	private static final String COLOR_ERROR = RED;
	private static final String COLOR_INFO = PURPLE;

	@Command(".*https?://(?:www.youtube.com/(?:watch\\?v=|v/|user/.*\\#p/u/[0-9]+/)|youtu.be/)([A-Za-z0-9_-]{11}).*")
	public void youtube(Message message, String videoID) {
		try {
			final DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = db.parse(new InputSource(new URL("http://gdata.youtube.com/feeds/api/videos/" + videoID).openStream()));
			final NodeList entryList = doc.getElementsByTagName("entry");

			// There should be exactly one entry for every video ID
			if(entryList.getLength() == 0) {
				throw new FileNotFoundException(); // Youtube's HTTP code should cause this automatically, this case should never happen
			} else if(entryList.getLength() != 1) {
				this.bot.sendMessage(COLOR_ERROR + "Found " + entryList.getLength() + " videos with ID " + videoID);
				return;
			}

			final Node entry = entryList.item(0);

			String author = null, title = null;
			int duration = 0, viewCount = 0;
			Calendar published = null;

			final NodeList entryRootNodes = entry.getChildNodes();
			for(int i = 0; i < entryRootNodes.getLength(); i++) {
				final Node entryRootNode = entryRootNodes.item(i);
				if(entryRootNode.getNodeName().equals("author")) {
					final NodeList authorNodes = entryRootNode.getChildNodes();
					for(int j = 0; j < authorNodes.getLength(); j++) {
						final Node authorNode = authorNodes.item(j);
						if(authorNode.getNodeName().equals("name")) {
							author = authorNode.getTextContent();
						}
					}
				} else if(entryRootNode.getNodeName().equals("media:group")) {
					final NodeList mediaNodes = entryRootNode.getChildNodes();
					for(int j = 0; j < mediaNodes.getLength(); j++) {
						final Node mediaNode = mediaNodes.item(j);
						if(mediaNode.getNodeName().equals("media:title")) {
							title = mediaNode.getTextContent();
						} else if(mediaNode.getNodeName().equals("yt:duration")) {
							duration = Integer.parseInt(mediaNode.getAttributes().getNamedItem("seconds").getNodeValue());
						}
					}
				} else if(entryRootNode.getNodeName().equals("yt:statistics")) {
					viewCount = Integer.parseInt(entryRootNode.getAttributes().getNamedItem("viewCount").getNodeValue());
				} else if(entryRootNode.getNodeName().equals("published")) {
					published = new GregorianCalendar();
					try {
						published.setTime(dateFormat.parse(entryRootNode.getTextContent()));
					} catch(ParseException e) {
						Log.e(e);
						published = null;
					}
				}
			}

			if(author != null && title != null) {
				final String pubdate = (published == null) ? null : String.format("%d %s %d", published.get(Calendar.DAY_OF_MONTH), published.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()), published.get(Calendar.YEAR));
				this.bot.sendMessage(COLOR_INFO +  encoded(title) + " (posted by " + encoded(author) + (pubdate == null ? "" : " on " + pubdate) + ", " + formatSeconds(duration) + ", " + viewCount + " views)");
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Problem parsing Youtube data");
			}
		} catch(ParserConfigurationException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to parse Youtube data");
			Log.e(e);
		} catch(FileNotFoundException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to find Youtube video with ID " + videoID);
			Log.e(e);
		} catch(MalformedURLException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to contact Youtube");
			Log.e(e);
		} catch(SAXException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to contact Youtube");
			Log.e(e);
		} catch(IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to contact Youtube");
			Log.e(e);
		}
	}

	@Override public String getFriendlyName() {return "Youtube";}
	@Override public String getDescription() {return "Outputs information about any youtube URLs posted";}
	@Override public String[] getExamples() {
		return new String[] {
				"http://www.youtube.com/watch?v=8AOfbnGkuGc"
		};
	}
}
