package modules;

import main.CommandContext;
import main.NoiseModule;
import main.Protocol;
import main.Style;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kool-aid!
 *
 * @author auchter
 *         Created Dec 23ish, 2011.
 */
public class Koolaid extends NoiseModule {
	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("yell", Style.RED.update("bold"));
		}};
	}

	@Command(value = ".*oh(?:,|\\.)* no.*", caseSensitive = false)
	public void ohyeah(CommandContext ctx) {
		if(this.bot.getProtocol() == Protocol.Slack) {
			// getRandomSlackEmoji() returns more than 2 elements, but the formatter is ok with it
			ctx.respond("#yell %s OH YEAH! %s", (Object[])getRandomSlackEmoji());
		} else {
			ctx.respond("#yell OH YEAH!");
		}
	}

	private static String[] getRandomSlackEmoji() {
		final String[] emoji = {":laughing:", ":stuck_out_tongue_winking_eye:", ":stuck_out_tongue_closed_eyes:", ":money_mouth_face:", ":astonished:", ":scream:"};
		for(int i = emoji.length - 1; i > 0; i--) {
			final int index = ThreadLocalRandom.current().nextInt(i + 1);
			String tmp = emoji[index];
			emoji[index] = emoji[i];
			emoji[i] = tmp;
		}
		return emoji;
	}

	@Override public String getFriendlyName() {return "Kool-aid";}
	@Override public String getDescription() {return "Contributes meaningfully to the conversation when needed.";}
	@Override public String[] getExamples() {
		return new String[] {
         "oh, no"
		};
	}
}
