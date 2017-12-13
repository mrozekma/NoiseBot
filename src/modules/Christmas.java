package modules;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.stream.Collectors;

import main.*;
import org.json.JSONException;

import static main.Utilities.getRandom;
import static main.Utilities.getRandomInt;

public class Christmas extends NoiseModule {
	private static final String[] lyrics = {
		"Deck the halls with boughs of holly,\nFa la la la la la, la la la la.\nTis the season to be jolly,\nFa la la la la la, la la la la.",
		"Frosty the snowman was a jolly happy soul\nWith a corncob pipe and a button nose\nand two eyes made out of coal\nFrosty the snowman is a fairy tale they say\nHe was made of snow but the children\nknow how he came to life one day",
		"Have yourself a merry little Christmas.\nLet your heart be light,\nFrom now on our troubles\nWill be out of sight.",
		"Jingle bells\njingle bells\njingle all the way!\nO what fun it is to ride\nIn a one-horse open sleigh",
		"Rudolph the Red-Nosed Reindeer\nHad a very shiny nose,\nAnd if you ever saw it,\nYou would even say it glows.",
		"Ma-ma-ma-ma-cita, donde esta Santa Cleese...\nThe vato wit da bony knees...\nHe comin' down da street wit no choos on his feet...\nAnd he's going to...",
		"On the fourth day of Xmas I stole from that lady...\nFour family photos\nThree jars of pennies\nTwo former husbands\nAnd a slipper on a shoe tree!",
		"He knows when you are sleeping\nHe knows when you're on the can\nHe'll hunt you down and blast your ass from here to Pakistan\nOh, you'd better not breathe, you'd better not move\nYou're better off dead, I'm telling you, dude\nSanta Claus is gunning you down",
	};

	private static final String[] reactions = {"santa", "christmas_tree", "gift"};

	private int count = 0;

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		final Calendar now = new GregorianCalendar();
		// 4th Thursday in November
		final Calendar thanksgiving = new GregorianCalendar(now.get(Calendar.YEAR), Calendar.NOVEMBER, 1, 0, 0, 0);
		thanksgiving.add(Calendar.DAY_OF_MONTH, (Calendar.THURSDAY - thanksgiving.get(Calendar.DAY_OF_WEEK) + 7) % 7);
		thanksgiving.add(Calendar.WEEK_OF_YEAR, 3);

		// Allowed after Thanksgiving, through the end of the year
		thanksgiving.add(Calendar.DAY_OF_MONTH, 1);
		if(now.before(thanksgiving)) {
			throw new ModuleInitException("It's too early for this; wait until " + thanksgiving.getTime());
		}
	}

	@Command("[^\\.].*")
	public JSONObject talked(CommandContext ctx) throws JSONException {
		// We return non-null if we actually want the view to display something; there's no actual data
		return (getRandomInt(0, 50) == 0) ? new JSONObject() : null;
	}

	@View(method = "talked")
	public void plainTalkedView(ViewContext ctx, JSONObject data) {
		ctx.respond("Ho Ho Ho!");
	}

	@View(value = Protocol.IRC, method = "talked")
	public void ircTalkedView(ViewContext ctx, JSONObject data) {
		final Style first = (this.count % 2 == 0) ? Style.RED : Style.GREEN,
		            second = (this.count % 2 == 0) ? Style.GREEN : Style.RED;
		ctx.respond("%#s %#s %#s", first, "Ho", second, "Ho", first, "Ho!");
		this.count++;
	}

	@View(value = Protocol.Slack, method = "talked")
	public void slackTalkedView(ViewContext ctx, JSONObject data) {
		ctx.respondReaction(reactions[this.count++ % reactions.length]);
	}

	@Command("\\.jingle")
	public JSONObject jingle(CommandContext ctx) throws JSONException {
		return new JSONObject().put("song", getRandom(lyrics).split("\n"));
	}

	@View(method = "jingle")
	public void plainJingleView(ViewContext ctx, JSONObject data) throws JSONException {
		for(String line : data.getStringArray("song")) {
			ctx.respond("#blue %s", line);
		}
	}

	@View(value = Protocol.Slack, method = "jingle")
	public void slackJingleView(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond(Arrays.stream(data.getStringArray("song")).collect(Collectors.joining("\n")));
	}

	@Override public String getFriendlyName() {return "Christmas";}
	@Override public String getDescription() {return "Says `Ho Ho Ho' a lot, and maybe other stuff";}
	@Override public String[] getExamples() {return new String[0];}
}
