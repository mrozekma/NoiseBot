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
   @Command("\\.woop ([0-9]+\\.?[0-9]*)")
   public void woop(Message message, String woopsArg) {
	  float requestedWoops = Float.valueOf(woopsArg);
	  
	  int wholeWoops = (int) Math.floor(requestedWoops);
	  int boundedWoops = range(wholeWoops, 1, 20);
	  int woopPart = Math.round((requestedWoops - wholeWoops) * 4);
	  
	  String woops = new String(new char[boundedWoops]).replace("\0", "WOOP ") + "WOOP".substring(0, woopPart);
	
	  this.bot.sendMessage(RED + woops.trim());
   }

   @Command("\\.woop")
   public void woopDefault(Message message) {this.woop(message, "10");}
   
   @Command("\\.woo[o]+p ([0-9]+\\.?[0-9]*)")
   public void woopLong(Message message, String woopsArg) {this.woop(message, woopsArg);}
   
   @Command("\\.woo([o]+)p")
   public void woopLongDefault(Message message, String numOs) {this.woop(message, "" + (numOs.length() + 2));}

   @Override public String getFriendlyName() {return "Woop";}
   @Override public String getDescription() {return "Woop it up a bit (to the nearest quarter woop).";}
   @Override public String[] getExamples() {
      return new String[] {
            ".woop",
            ".woop 15",
            ".woop 4.25",
            ".woooooop"
      };
   }
}
