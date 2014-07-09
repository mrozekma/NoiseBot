package modules;

import main.Message;
import main.NoiseModule;

import static panacea.Panacea.*;
import static org.jibble.pircbot.Colors.*;

/**
 * Woop Woop Woop
 *
 * @author Michael Mrozek | sed 's/Mrozek/Auchter/'
 *         Created Jun 16, 2009. + 1 year, 9 months
 */
public class Woop extends NoiseModule {
   private static String REGULAR_WOOP = "WOOP";
   private static String COFFEE_WOOP = "COFFEE";

   @Command(value = "\\.woop (coffee)? ?([0-9]+\\.?[0-9]*)", caseSensitive = false)
   public void woop(Message message, String coffeeArg, String woopsArg) {
     float requestedWoops = Float.valueOf(woopsArg);
     String woopType = REGULAR_WOOP;

     if (coffeeArg != null && coffeeArg.equals("coffee")) {
       woopType = COFFEE_WOOP;
     }

     String woops = generateWoops(woopType, requestedWoops);
     this.bot.sendMessage(RED + woops.trim());
   }

   @Command(value = "\\.woop ?(coffee)?", caseSensitive = false)
   public void woopDefault(Message message, String coffeeArg) {this.woop(message, coffeeArg, "10");}

   @Command(value = "\\.woo[o]+p (coffee)? ?([0-9]+\\.?[0-9]*)", caseSensitive = false)
   public void woopLong(Message message, String coffeeArg, String woopsArg) {this.woop(message, coffeeArg, woopsArg);}

   @Command(value = "\\.woo([o]+)p ?(coffee)?", caseSensitive = false)
   public void woopLongDefault(Message message, String numOs, String coffeeArg) {this.woop(message, coffeeArg, "" + (numOs.length() + 2));}

   private String generateWoops(String woop, float requestedWoops) {
     int wholeWoops = (int) Math.floor(requestedWoops);
     int boundedWoops = range(wholeWoops, 1, 20);
     int woopPart = Math.round((requestedWoops - wholeWoops) * woop.length());

     String woops = new String(new char[boundedWoops]).replace("\0", woop + " ") + woop.substring(0, woopPart);
     return woops;
   }

   @Override public String getFriendlyName() {return "Woop";}
   @Override public String getDescription() {return "Woop it up a bit (to the nearest quarter woop).";}
   @Override public String[] getExamples() {
      return new String[] {
            ".woop",
            ".woop 15",
            ".woop 4.25",
            ".woooooop",
            ".woop coffee",
            ".woop coffee 2.5",
            ".woooop coffee"
      };
   }
}
