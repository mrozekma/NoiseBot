package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

import main.Message;
import main.ModuleLoadException;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.*;

/**
 * Imgur
 *
 * @author Michael Mrozek
 *         Created Apr 26, 2013.
 */
public class Imgur extends NoiseModule {
	private static final String URL_PATTERN = "https?://(?:.+\\.)?imgur.com/(?:.+/)?([a-zA-Z0-9]+)(?:\\.[a-z]{3})?";

	private static final String COLOR_INFO = PURPLE;
	private static final String COLOR_ERROR = RED + REVERSE;

	private String clientID;

	@Override public void init(NoiseBot bot, Map<String, String> config) throws ModuleLoadException {
		super.init(bot, config);
		if(!config.containsKey("client-id")) {
			throw new ModuleLoadException("No Imgur client ID specified in configuration");
		}
		this.clientID = config.get("client-id");
	}

	@Command(".*" + URL_PATTERN + ".*")
	public void imgur(Message message, String imgID) {
		try {
			final JSONObject json = this.getJSON(imgID);
			if(!json.getBoolean("success")) {
				if(json.has("data")) {
					final JSONObject data = json.getJSONObject("data");
					if(data.has("error")) {
						this.bot.sendMessage(COLOR_ERROR + data.get("error"));
						return;
					}
				}
				this.bot.sendMessage(COLOR_ERROR + "Unknown error communicating with Imgur");
				return;
			}

			final JSONObject data = json.getJSONObject("data");
			final StringBuffer buffer = new StringBuffer();
			buffer.append((data.has("title") && !data.isNull("title")) ? data.get("title") : "Untitled");
			if(data.has("description") && !data.isNull("description")) {
				buffer.append(" -- ").append(data.get("description"));
			}
			buffer.append(" (");
			buffer.append(String.format("%dx%d, ", data.getInt("width"), data.getInt("height")));
			buffer.append(bytesToFriendly(data.getInt("size"), 2)).append(", ");
			buffer.append(pluralize(data.getInt("views"), "view", "views"));
			buffer.append(")");

			this.bot.sendMessage(COLOR_INFO + buffer);
		} catch(FileNotFoundException e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + String.format("No image with ID %s exists", imgID));
		} catch(Exception e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Imgur data");
		}
	}

	@Override public String getFriendlyName() {return "Imgur";}
	@Override public String getDescription() {return "Outputs information about any Imgur URLs posted";}
	@Override public String[] getExamples() {
		return new String[] {
			"http://imgur.com/E75a0ua"
		};
	}

	private JSONObject getJSON(String imgID) throws IOException, JSONException {
		final URLConnection c = new URL("https://api.imgur.com/3/image/" + imgID).openConnection();
		c.addRequestProperty("Authorization", "Client-ID " + this.clientID);
		final Scanner s = new Scanner(c.getInputStream());
		final StringBuffer buffer = new StringBuffer();
		while(s.hasNextLine()) {
			buffer.append(s.nextLine());
		}
		return new JSONObject(buffer.toString());
	}
}
