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

   @Command("\\.woop (coffee)? ?([0-9]+\\.?[0-9]*)")
   public void woop(Message message, String coffeeArg, String woopsArg) {
	  float requestedWoops = Float.valueOf(woopsArg);
     String woopType = REGULAR_WOOP;

     if (coffeeArg != null && coffeeArg.equals("coffee")) {
       woopType = COFFEE_WOOP;
     }

     String woops = generateWoops(woopType, requestedWoops);
	  this.bot.sendMessage(RED + woops.trim());
   }

   @Command("\\.woop (coffee)?")
   public void woopDefault(Message message, String coffeeArg) {this.woop(message, coffeeArg, "10");}
   
   @Command("\\.woo[o]+p (coffee)? ?([0-9]+\\.?[0-9]*)")
   public void woopLong(Message message, String, coffeeArg, String woopsArg) {this.woop(message, coffeeArg, woopsArg);}
   
   @Command("\\.woo([o]+)p (coffee)?")
   public void woopLongDefault(Message message, String numOs, String coffeeArg) {this.woop(message, coffeeArg, "" + (numOs.length() + 2));}

   private String generateWoops(String woop, float requestedWoops) {
     int wholeWoops = (int) Math.floor(requestedWoops);
     int boundedWoops = range(wholeWoops, 1, 20);
     int woopPart = Math.round((requestedWoops - wholeWoops) * woop.length);
     
     String woops = new String(new char[boundedWoops]).replace("\0", woop + " ") + woop.substring(0, woopPart);
     return woops;
   }
}
