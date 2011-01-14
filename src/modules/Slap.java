package modules;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.getRandom;

/**
 * Slap
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Slap extends NoiseModule {

	public static String slapUser(String victim) {
		final String[] adverbs = new String[] {
			"", "disapprovingly", "with great prejudice", "pimpingly", "righteously", "with a squid",
			"with a telephone pole", "with a hatchet", "with a fist of fury", "with a baguette",
			"with a flaming piece of carpet", "absentmindedly", "judgmentally", "owlishly", "lazily",
			"unethically", "zealously", "majestically", "with an acid-filled flask", "with a chair",
			"surrealistically", "with a lightbulb", "with a bucket full of lobsters", "viciously",
			"quizzically", "with a chainsaw", "quixotically", "with a pound of bacon", "with a platypus", 
		};
		
		return "slaps " + victim + " " + getRandom(adverbs);
	}

	@Command("\\.slap (.*)")
	public void slap(Message message, String target) {
		this.bot.sendAction(slapUser(target));
	}
	
	@Command("\\.slap")
	public void slapSelf(Message message) {
		this.slap(message, message.getSender());
	}

	@Override public String getFriendlyName() {return "Slap";}
	@Override public String getDescription() {return "Slaps the specified user";}
	@Override public String[] getExamples() {
		return new String[] {
				".slap -- Slap the sending user",
				".slap _nick_ -- Slap the specified nick"
		};
	}
}
