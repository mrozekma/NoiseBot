package modules;

import java.lang.reflect.Method;

import main.JSONObject;
import org.json.JSONException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import main.CommandContext;
import static main.Utilities.*;

/**
 * Urban Dictionary module
 *
 * @author Will Fuqua
 *         Created Nov 5, 2011
 */
public class UrbanDictionary extends WebLookupModule {
  private static final String URBAN_URL = "http://www.urbandictionary.com/define.php?term=";
  private static final String DEFINITION_SELECTOR = ".meaning";

  @Command("\\.(?:ud|urban) (.+)")
  public JSONObject urban(CommandContext ctx, String term) throws JSONException {
    return this.lookup(term, URBAN_URL + urlEncode(term));
  }

  @Override protected String getThumbnailURL() {
    return "https://upload.wikimedia.org/wikipedia/vi/7/70/Urban_Dictionary_logo.png";
  }

  @Override protected String getBody(String term, String url, Document page) throws EntryNotFound, BodyNotFound {
    // search page for definition
    Element node = page.select(DEFINITION_SELECTOR).first();
    if (node == null) {
      throw new BodyNotFound();
    }

    String definition = node.text();
    if(definition.startsWith("There aren't any definitions for")) {
      throw new EntryNotFound();
    }

    return definition;
  }

  @Override protected boolean shouldIncludeLink(Method commandMethod) {
    return false;
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
