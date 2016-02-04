package modules;

import java.io.IOException;

import debugging.Log;
import main.JSONObject;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import main.CommandContext;
import main.NoiseModule;
import main.ViewContext;
import static main.Utilities.*;

/**
 * Urban Dictionary module
 *
 * @author Will Fuqua
 *         Created Nov 5, 2011
 */
public class UrbanDictionary extends NoiseModule {
  private static final int MAXIMUM_MESSAGE_LENGTH = 400; // Approximately (512 bytes including IRC data), although we truncate on all protocols
  private static final String URBAN_URL = "http://www.urbandictionary.com/define.php?term=";
  private static final String DEFINITION_SELECTOR = ".meaning";

  @Command("\\.(?:ud|urban) (.+)")
  public JSONObject urban(CommandContext ctx, String term) throws JSONException {
    // fetch webpage
    Document page = null;
    try {
      page = Jsoup.connect(URBAN_URL + urlEncode(term))
          .timeout(10000) // 10 seems like a nice number
          .get(); 
    } catch (IOException e) {
      Log.e(e);
      return new JSONObject().put("error", "Error retrieving urban dictionary page");
    }

    // search page for definition
    Element node = page.select(DEFINITION_SELECTOR).first();
    if (node == null) {
      return new JSONObject().put("warning", "Not found");
    }
    String definition = node.text();

    // truncate the definition if it's too long
    if (definition.length() > MAXIMUM_MESSAGE_LENGTH)
      definition = definition.substring(0, MAXIMUM_MESSAGE_LENGTH);

    return new JSONObject().put("definition", definition);
  }

  @View
  public void plainView(ViewContext ctx, JSONObject data) throws JSONException {
    if(data.has("warning")) {
      ctx.respond("#warning %s", data.get("warning"));
      return;
    }
    ctx.respond("%s", data.get("definition"));
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
