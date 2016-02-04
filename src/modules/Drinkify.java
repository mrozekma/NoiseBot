package modules;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import main.JSONObject;
import org.json.JSONException;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import debugging.Log;
import main.CommandContext;
import main.NoiseModule;
import main.ViewContext;

/**
 * Drinkify module
 *
 * @author Michael Auchter
 *         Hacked together on Prickle-Prickle, the 35th day of Bureaucracy
 *         in the YOLD 3178
 */
public class Drinkify extends NoiseModule {
  @Command("\\.drinkify (.+)")
  public JSONObject drinkify(CommandContext ctx, String band) throws JSONException
  {
    final JSONObject rtn = new JSONObject();
    Document page;
    try {
      page = Jsoup.connect(new URI("http", "drinkify.org", "/" + band, null).toASCIIString()).timeout(10000).get();
    } catch (HttpStatusException e) {
      Log.e(e);
      return rtn.put("code", e.getStatusCode()).put("error", (e.getStatusCode() == 500) ? "Drinkify page does not exist" : "Error retrieving drinkify page...");
    } catch (IOException | URISyntaxException e) {
      Log.e(e);
      return rtn.put("error", "Error retrieving drinkify page...");
    }

    Element recipe = page.select("#recipeContent").first();

    band = recipe.select("h2").first().text().toUpperCase();
    band = band.substring(0, band.length()-1).substring(1);
    rtn.put("band", band);

    Elements ingredients = recipe.select(".recipe").first().select("li");
    for(Element ingredient : ingredients) {
      rtn.append("ingredients", ingredient.text());
    }

    rtn.put("instructions", recipe.select(".instructions").first().text());
    return rtn;
  }

  @View
  public void view(ViewContext ctx, JSONObject data) throws JSONException {
    ctx.respond("%(#bold)s: #([, ] %s). %s", data.get("band"), data.getStringArray("ingredients"), data.get("instructions"));
  }

  @Override
  public String getFriendlyName() {
    return "Drinkify";
  }

  @Override
  public String getDescription() {
    return "Returns a drink appropriate for a given band";
  }

  @Override
  public String[] getExamples() {
    return new String[] {
      ".drinkify Kyuss"
    };
  }
}
