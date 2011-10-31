package modules;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import com.wolfram.alpha.WAEngine;
import com.wolfram.alpha.WAException;
import com.wolfram.alpha.WAPlainText;
import com.wolfram.alpha.WAPod;
import com.wolfram.alpha.WAQuery;
import com.wolfram.alpha.WAQueryResult;
import com.wolfram.alpha.WASubpod;

import static panacea.Panacea.*;
import static org.jibble.pircbot.Colors.*;

/**
 * Wolfram Alpha integration
 *
 * @author Will Fuqua
 *         Created Oct 31, 2011. Halloween, bitches
 */
public class Wolfram extends NoiseModule {
	private static final String COLOR_ERROR = RED + REVERSE;
	
   @Command("\\.wolfram (.+)")
   public void wolfram(Message message, String term) {
	   if(term.isEmpty()) { // Should be impossible
			this.bot.sendMessage(COLOR_ERROR + "Missing term");
			return;
	   }
	   this.bot.sendMessage("looking up " + term);
   }

   @Override public String getFriendlyName() {return "Wolfram";}
   @Override public String getDescription() {return "Query Wolfram Alpha";}
   @Override public String[] getExamples() {
      return new String[] {
            ".wolfram",
            ".woop 15"
      };
   }
}
