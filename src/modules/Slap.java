package modules;

import main.JSONObject;
import main.Message;
import main.NoiseBot;
import main.NoiseModule;
import org.json.JSONException;

import static main.Utilities.getRandom;

/**
 * Slap
 *
 * @author Michael Mrozek
 *         Created Jun 14, 2009.
 */
public class Slap extends NoiseModule {
	private final String[] adverbs = new String[] {
		"", "disapprovingly", "with great prejudice", "pimpingly", "righteously", "with a squid",
		"with a telephone pole", "with a hatchet", "with a fist of fury", "with a baguette",
		"with a flaming piece of carpet", "absentmindedly", "judgmentally", "owlishly", "lazily",
		"unethically", "zealously", "majestically", "with an acid-filled flask", "with a chair",
		"surrealistically", "with a lightbulb", "with a bucket full of lobsters", "viciously",
		"quizzically", "with a chainsaw", "quixotically", "with a pound of bacon", "with a platypus",
//			"with a \u00e2\u0098\u0083", "with a magical \u00e2\u0098\u0082", "with a ringing \u00e2\u0098\u008e",
		"with a satchel of poison ivy", "with a sock full of quarters", "with a python",
		"with a flourescent lightbulb", "with a coffee mug", "with a moose", "with Sarah Palin",
		"heroically", "with a barrel of bees", "like Bootsy Collins slaps his bass", "with a cactus",
		"with a crucifix. Praise Jesus!", "with a bible", "with a machete", "with SCIENCE!",
		"like you lost a slap bet", "like really cheap vodka", "with his left hand", "with his right hand",
		"with a lava lamp", "with a trout", "with a porcupine", "with a fine", "with a lawsuit",
		"with a nail gun", "with a feather", "theoretically", "with a rock", "with paper", "with scissors",
		"with porn", "like Kim Jong-il", "with Kim Jong-il", "with a standard ten foot pole", "with a chicken",
		"with Osama bin Laden's bloated corpse",
	};
		
	@Command("\\.slap ([^,]*).*")
	public JSONObject slap(Message message, String target) throws JSONException {
		final String adverb = getRandom(adverbs);
		return new JSONObject().put("victim", target).put("adverb", adverb).put("action", String.format("slaps %s %s", target, adverb));
	}
	
	@Command("\\.slap")
	public JSONObject slapSelf(Message message) throws JSONException {
		return this.slap(message, message.getSender());
	}

	@View
	public void view(Message message, JSONObject data) throws JSONException {
		this.bot.sendAction("%s", data.get("action"));
	}

	// A lot of modules are very interested in slapping people
	static void slap(NoiseBot bot, Message message) {
		slap(bot, message, message.getSender());
	}
	static void slap(NoiseBot bot, Message message, String target) {
		final NoiseModule slapModule = bot.getModules().get("Slap");
		if(slapModule != null) {
			slapModule.processMessageAndDisplayResult(message.deriveNew(".slap " + target));
		} else {
			// Wholly inferior fallback
			message.respondAction("slaps %s", target);
		}
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
