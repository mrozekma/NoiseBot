package modules;

import java.io.IOException;

import debugging.Log;
import main.JSONObject;
import main.Protocol;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import main.CommandContext;
import main.NoiseModule;
import main.ViewContext;
import static main.Utilities.*;

/**
 * Beer Advocate module
 *
 * @author Michael Auchter
 *         Created Sweetmorn, the 22nd day of Bureaucracy in the YOLD 3178
 */
public class BeerAdvocate extends NoiseModule {
  // Yeah, you wanna fight?
  private static String extract(Document page, String selector)
  {
    Element node = page.select(selector).first();
    if (node == null)
      return "";
    return node.text();
  }

  private Document snarf(String url) throws IOException
  {
    final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/32.0.1667.0 Safari/537.36";
    return Jsoup.connect(url).timeout(10000).userAgent(USER_AGENT).get();
  }

  @Command(".*(http://(?:www\\.)?beeradvocate.com/beer/profile/[0-9]+/[0-9]+).*")
  public JSONObject beer(CommandContext ctx, String beerUrl) throws JSONException
  {
    Document page;
    try {
      page = snarf(beerUrl);
    } catch (IOException e) {
      Log.e(e);
      return new JSONObject().put("error", "Error retrieving BA page...");
    }

    String name = extract(page, ".titleBar");
    String score = extract(page, ".BAscore_big");
    String style = extract(page, "[href^=/beer/style/]");
    return new JSONObject().put("url", beerUrl).put("name", name).put("score", score).put("style", style);
  }

  // Runs a search in BA and returns the first search result. Searches beer only.
  @Command("\\.beer (.*)")
  public JSONObject search(CommandContext ctx, String toSearch) throws JSONException
  {
    toSearch = toSearch.replaceAll(" ", "\\+");
    Document searchResults;
    try {
      searchResults = snarf("http://beeradvocate.com/search?q="+toSearch+"&qt=beer");
    } catch (IOException e) {
      Log.e(e);
      return new JSONObject().put("error", "Error retrieving BA page...");
    }
    String rel = searchResults.select("[href^=/beer/profile/]").first().attr("href");
    String url = "http://beeradvocate.com" + rel;
    return this.beer(null, url);
  }

  @View(method = "beer")
  public void beerView(ViewContext ctx, JSONObject data) throws JSONException {
    ctx.respond("%s - %s - %s", data.getString("name"), data.getString("style"), data.getString("score"));
  }

  @View(method = "search")
  public void searchView(ViewContext ctx, JSONObject data) throws JSONException {
    this.beerView(ctx, data);
    ctx.respond("%s", data.getString("url"));
  }

  // Slack unfolds BeerAdvocate links, so no need to do anything when links are posted
  @View(value = Protocol.Slack, method = "beer")
  public void slackBeerView(ViewContext ctx, JSONObject data) {}

  @Override
  public String getFriendlyName() {
    return "BeerAdvocate";
  }

  @Override
  public String getDescription() {
    return "Returns information about a beer when a BeerAdvocate link appears...";
  }

  @Override
  public String[] getExamples() {
    return new String[] { 
      "http://beeradvocate.com/beer/profile/192/83920/"
    };
  }
}
