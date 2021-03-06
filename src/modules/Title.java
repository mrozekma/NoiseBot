package modules;

import main.CommandContext;
import main.JSONObject;
import main.NoiseModule;
import main.ViewContext;
import org.json.JSONException;

import static main.Utilities.getRandom;

/**
 * Title
 *
 * @author Michael Mrozek
 *         Created May 8, 2011.
 */
public class Title extends NoiseModule {
	// http://gaming.stackexchange.com/q/21292/3391
	private static final String[] prefixes = {"Lead", "Senior", "Direct", "Dynamic", "Future", "National", "Regional", "Central", "Global", "Dynamic", "International", "Legacy", "Forward", "Internal", "Chief", "Principal", "Postdoctoral", "Regulatory"},
	                              middles = {"Human", "Environmental", "Aerospace", "Space", "Deep Sea", "Atmospheric", "Cardiovascular", "Electrical", "Computer", "Emergency", "Mining", "Nuclear", "Safety", "Histology", "Forensic"},
	                              suffixes = {"Surgeon", "Scientist", "Engineer", "Technologist", "Neurosurgeon", "Pilot", "Astronaut", "Archeologist", "Aviator", "Specialist", "Psychologist", "Composer", "Fighter", "Professional", "Geographer", "Architect", "Astronomer", "Cytogeneticist", "Dentist", "Interpreter", "Phlebotomist", "Physician", "Meteorologist", "Philosopher", "Garbologist"};

	@Command("\\.title (.*)")
	public JSONObject title(CommandContext ctx, String target) throws JSONException {
		return new JSONObject().put("target", target).put("prefix", getRandom(prefixes)).put("middle", getRandom(middles)).put("suffix", getRandom(suffixes));
	}
	
	@Command("\\.title") public JSONObject titleSelf(CommandContext ctx) throws JSONException {
		return this.title(ctx, ctx.getMessageSender());
	}

	@View
	public void plainView(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("%s: %s %s %s", data.get("target"), data.get("prefix"), data.get("middle"), data.get("suffix"));
	}
	
	@Override public String getFriendlyName() {return "Title";}
	@Override public String getDescription() {return "Chooses a random job title for the given nick";}
	@Override public String[] getExamples() {
		return new String[] {
				".title -- Display a title for the sending user",
				".title _nick_ -- Display a title for the specified nick",
		};
	}
}
