package modules;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.*;

/**
 * Woop Woop Woop 
 *
 * @author Michael Mrozek | sed 's/Mrozek/Auchter/'
 *         Created Jun 16, 2009. + 1 year, 9 months
 */
public class Woop extends NoiseModule {
	@Command("\\.woop ([0-9]+)")
	public void woop(Message message, int num) {
      String woops = RED + "WOOP ";
		num = range(num, 1, 20);

      for (int i = 0; i < num; i++)
         woops += "WOOP ";

      this.bot.sendMessage(woops);
	}
	
	@Command("\\.woop")
	public void woopDefault(Message message) {this.woop(message, 10);}
	
	@Override public String getFriendlyName() {return "Woop";}
	@Override public String getDescription() {return "Woop it up a bit.";}
	@Override public String[] getExamples() {
		return new String[] {
				".woop",
				".woop 15"
		};
	}
}
