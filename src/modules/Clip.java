package modules;

import static main.Utilities.getJSON;
import static org.jibble.pircbot.Colors.*;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

import main.Message;
import main.NoiseModule;

/**
 * Clip
 *
 * @author Michael Mrozek
 *         Created Feb 19, 2011.
 */
public class Clip extends NoiseModule {
	private static final String COLOR_ERROR = RED;
	private static final String COLOR_INFO = PURPLE;
	
	@Command(".*(http://clip.mrozekma.com/[0-9a-fA-F-]{36}).*")
	public void clip(Message message, String url) {
		try {
			final JSONObject json = getJSON(url + "?info");
			if(json.has("id") && json.has("title") && json.has("source") && json.has("start") && json.has("end")) {
				this.bot.sendMessage(COLOR_INFO + (json.isNull("title") ? "Untitled" : json.get("title")) + " (from " + json.get("source") + ", " + json.get("start") + "-" + json.get("end") + ")");
			} else if(json.has("error")) {
				this.bot.sendMessage(COLOR_ERROR + json.get("error"));
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Unknown error");
			}
		} catch(IOException e) {
			Log.w(e);
			this.bot.sendMessage(COLOR_ERROR + "Unable to connect to mrozekma server");
		} catch(JSONException e) {
			Log.w(e);
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing response");
		}
	}
	
	@Override public String getFriendlyName() {return "Clip";}
	@Override public String getDescription() {return "Outputs information about any mrozekma.com video clip URLs posted";}
	@Override public String[] getExamples() {
		return new String[] {
				"http://clip.mrozekma.com/c06c0612-349b-4580-b03e-ba8f771c2d0f"
		};
	}
}
