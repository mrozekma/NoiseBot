package modules;

import main.Message;
import main.NoiseBot;
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
      float numWoops = Float.valueOf(woopsArg);
	   
	  // whole woops
	  int wholeWoops = (int) Math.floor(numWoops);
	  String woops = "";
      int num = range(wholeWoops, 1, 20);

      for (int i = 0; i < num; i++)
         woops += " WOOP";
      
      // remainder partial woops, to the nearest 1/4 woop
      int partialWoops = Math.round((numWoops - wholeWoops) * 4);
      switch(partialWoops) {
      case 1:
    	  woops += " W";
    	  break;
      case 2:
    	  woops += " WO";
    	  break;
      case 3:
    	  woops += " WOO";
    	  break;
      case 4:
    	  woops += " WOOP";
    	  break;
      default:
    	  break;
      }

      this.bot.sendMessage(RED + woops.substring(1));
   }

   @Command("\\.woop")
   public void woopDefault(Message message) {this.woop(message, "10");}

   @Override public String getFriendlyName() {return "Woop";}
   @Override public String getDescription() {return "Woop it up a bit (to the nearest quarter woop).";}
   @Override public String[] getExamples() {
      return new String[] {
            ".woop",
            ".woop 15",
            ".woop 4.25"
      };
   }
}
