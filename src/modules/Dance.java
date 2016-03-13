package modules;

import main.CommandContext;
import main.NoiseModule;
import main.Protocol;
import main.Style;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Dance!
 *
 * @author Andy Spencer (and bash.org)
 *         Created Jun 23, 2011.
 */
public class Dance extends NoiseModule {
	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("first_guy", Style.YELLOW);
			put("second_guy", Style.BLUE);
			put("first_line", Style.RED);
			put("second_line", Style.GREEN);
		}};
	}

	@Command(value = ".*\\bdanc(?:e|ing).*", caseSensitive = false)
	public void dance(CommandContext ctx) {
		// Not sure if this should be a view; I don't really want this to return data
		if(this.bot.getProtocol() == Protocol.Slack) {
			ctx.respond(":dancer: :dancers: :dancer: :dancers: :dancer:");
		} else {
			ctx.respondAction("dances :D-<");
			ctx.respondAction("dances :D|-<");
			ctx.respondAction("dances :D/-<");
		}
	}

	@Command(value = ".*\\bdisco.*", caseSensitive = false)
	public void disco(CommandContext ctx) {
		// Not sure if this should be a view; I don't really want this to return data
		final String[] lines = {
			"#first_guy \\o   #first_line LET'S   #second_guy o/",
			" #first_guy |>  #second_line DISCO! #second_guy <|",
			"#first_guy < \\         #second_guy / >",
		};
		if(this.bot.getProtocol() == Protocol.Slack) {
			// This looks so boring I think I'm just going to not disco on Slack. Disco is dead
//			ctx.respond(String.format("```%s```", Arrays.stream(lines).collect(Collectors.joining("\n"))));
		} else {
			Arrays.stream(lines).forEach(ctx::respond);
		}
	}

	@Command(value = ".*\\bflarhgunnstow.*", caseSensitive = false)
	public void flarhgunnstow(CommandContext ctx) {
		ctx.respondAction("flarhgunnstows :D]-<");
		ctx.respondAction("flarhgunnstows :D|-<");
		ctx.respondAction("flarhgunnstows :D[-<");
	}

	@Override public String getFriendlyName() {return "Dance";}
	@Override public String getDescription() {return "Dances when the mood is right";}
	@Override public String[] getExamples() {
		return new String[] {
			"<Zybl0re> get up",
			"<Zybl0re> get on up",
			"<Zybl0re> get up",
			"<Zybl0re> get on up",
			"<phxl|paper> and DANCE"
		};
	}
}
