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

   @Command("\\.woop ([0-9]+\\.?[0-9]*)")
   public void woop(Message message, String woopsArg) {
	  float requestedWoops = Float.valueOf(woopsArg);
     String woops = generateWoops(REGULAR_WOOP, requestedWoops);
	  this.bot.sendMessage(RED + woops.trim());
   }

   @Command("\\.woop")
   public void woopDefault(Message message) {this.woop(message, "10");}
   
   @Command("\\.woo[o]+p ([0-9]+\\.?[0-9]*)")
   public void woopLong(Message message, String woopsArg) {this.woop(message, woopsArg);}
   
   @Command("\\.woo([o]+)p")
   public void woopLongDefault(Message message, String numOs) {this.woop(message, "" + (numOs.length() + 2));}

   @Command("\\.woop coffee ([0-9]+\\.?[0-9]*)")
   public void woopCoffee(Message message, String woopsArg) {
     float requestedWoops = Float.valueOf(woopsArg);
     String woops = generateWoops(COFFEE_WOOP, requestedWoops);
     this.bot.sendMessage(RED + woops.trim());
   }

   @Command(".woop coffee")
   public void woopCoffeeDefault(Message message) {this.woopCoffee(message, "10");}

   @Command("\\.woo([o]+)p coffee")
   public void woopCoffeeLongDefault(Message message, String numOs) {this.woopCoffee(message, "" + (numOs.length() + 2));}

   @Override public String getFriendlyName() {return "Woop";}
   @Override public String getDescription() {return "Woop it up a bit (to the nearest quarter woop).";}
   @Override public String[] getExamples() {
      return new String[] {
            ".woop",
            ".woop 15",
            ".woop 4.25",
            ".woooooop",
            ".woop coffee"
            ".woop coffee 2.5"
      };
   }

   private String generateWoops(String woop, float requestedWoops) {
     int wholeWoops = (int) Math.floor(requestedWoops);
     int boundedWoops = range(wholeWoops, 1, 20);
     int woopPart = Math.round((requestedWoops - wholeWoops) * woop.length);
     
     String woops = new String(new char[boundedWoops]).replace("\0", woop + " ") + woop.substring(0, woopPart);
     return woops;
   }
}
