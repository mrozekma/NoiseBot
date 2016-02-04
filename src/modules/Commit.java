package modules;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import debugging.Log;

import main.*;
import org.json.JSONException;

import static main.Utilities.getRandom;

/**
 * Commit
 *
 * @author Michael Mrozek
 *         Created May 7, 2013.
 */
public class Commit extends NoiseModule {
	// https://raw.github.com/ngerakines/commitment/master/commit_messages.txt
	private static File MESSAGES_FILE = NoiseBot.getDataFile("commit_messages.txt");

	private String[] messages;

	@Override public void init(NoiseBot bot) throws ModuleInitException {
		super.init(bot);
		try {
			this.messages = Files.lines(Paths.get(MESSAGES_FILE.toURI())).toArray(String[]::new);
			Log.i("Loaded messages file: %d", this.messages.length);
		} catch(NoSuchFileException e) {
			this.bot.sendNotice("No commit messages file found");
		} catch(IOException e) {
			this.bot.sendNotice("Unable to load commit messages file: " + e.getMessage());
		}
	}

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("quote", Style.CYAN);
		}};
	}

	@Command("\\.commit")
	public JSONObject commit(CommandContext ctx) throws JSONException {
		if(this.messages.length == 0) {
			return new JSONObject();
		} else {
			final String commitMessage = getRandom(this.messages);
			final String nick = getRandom(this.bot.getNicks());
			return new JSONObject()
					.put("message", commitMessage)
					.put("nick", nick)
					.put("processed", commitMessage.replace("XNAMEX", nick).replace("XUPPERNAMEX", nick.toUpperCase()));
		}
	}

	@View
	public void view(ViewContext ctx, JSONObject data) throws JSONException {
		if(data.has("processed")) {
			ctx.respond("#quote %s", data.getString("processed"));
		}
	}

	@Override public String getFriendlyName() {return "Commit";}
	@Override public String getDescription() {return "Outputs random commit messages from http://github.com/ngerakines/commitment";}
	@Override public String[] getExamples() {
		return new String[] {
				".commit"
		};
	}
}
