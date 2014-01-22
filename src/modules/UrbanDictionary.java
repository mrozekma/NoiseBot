package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import main.Message;
import main.NoiseModule;
import static main.Utilities.*;

/**
 * Urban Dictionary module
 *
 * @author Will Fuqua
 *         Created Nov 5, 2011
 */
public class UrbanDictionary extends NoiseModule {
  private static final int MAXIMUM_MESSAGE_LENGTH = 400; // Approximately (512 bytes including IRC data)
  private static final String COLOR_WARNING = RED;
  private static final String COLOR_ERROR = RED + REVERSE;
  private static final String URBAN_URL = "http://www.urbandictionary.com/define.php?term=";

  @Command("\\.(?:ud|urban) (.+)")
  public void urban(Message message, String term) {
    if (term.isEmpty()) { // Should be impossible
      this.bot.sendMessage(COLOR_ERROR + "Missing term");
      return;
    }

    sendDefinition(term);
  }

  private void sendDefinition(String term) {

    // fetch webpage
    Document page = null;
    try {
      page = Jsoup.connect(URBAN_URL + urlEncode(term))
          .timeout(10000) // 10 seems like a nice number
          .get(); 
    } catch (IOException e) {
      e.printStackTrace();
      this.bot.sendMessage(COLOR_ERROR + "Error retrieving urban dictionary page");
    }

    // search page for definition
    Element node = page.select(".definition").first();
    if (node == null) {
      this.bot.sendMessage(COLOR_WARNING + "Not found");
      return;
    }
    String definition = node.text();

    // truncate the definition if it's too long
    if (definition.length() > MAXIMUM_MESSAGE_LENGTH)
      definition = definition.substring(0, MAXIMUM_MESSAGE_LENGTH);

    this.bot.sendMessage(definition);
  }

  @Override
  public String getFriendlyName() {
    return "UrbanDictionary";
  }

  @Override
  public String getDescription() {
    return "Looks up a term in urbandictionary.com";
  }

  @Override
  public String[] getExamples() {
    return new String[] { 
        ".urban _term_ -- Look up a term",
        ".ud _term_ -- Look up a term" 
    };
  }
}
