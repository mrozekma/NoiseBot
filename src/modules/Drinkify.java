package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URI;

import main.Message;
import main.NoiseModule;
import static main.Utilities.*;

/**
 * Drinkify module
 *
 * @author Michael Auchter
 *         Hacked together on Prickle-Prickle, the 35th day of Bureaucracy
 *         in the YOLD 3178
 */
public class Drinkify extends NoiseModule {
  private static final String COLOR_ERROR = RED;

  @Command("\\.drinkify (.+)")
  public void drinkify(Message message, String band)
  {
    Document page = null;
    try {
      page = Jsoup.connect(new URI("http", "drinkify.org", "/" + band, null).toASCIIString()).timeout(10000).get();
    } catch (IOException e) {
      e.printStackTrace();
      this.bot.sendMessage(COLOR_ERROR + "Error retrieving drinkify page...");
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }

    Element recipe = page.select("#recipeContent").first();

    band = recipe.select("h2").first().text().toUpperCase();
    band = band.substring(0, band.length()-1).substring(1);

    String output = band + ": ";
    Elements ingredients = recipe.select(".recipe").first().select("li");
    for (Element ingredient : ingredients)
        output += ingredient.text() + (ingredient == ingredients.last() ? ". " : ", ");

    output += recipe.select(".instructions").first().text();

    this.bot.sendMessage(output);
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
