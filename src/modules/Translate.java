package modules;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

import main.Message;
import main.NoiseModule;
import static main.Utilities.getJSON;
import static main.Utilities.urlEncode;

import static org.jibble.pircbot.Colors.*;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Translate
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Translate extends NoiseModule {
	private static final String COLOR_ERROR = RED;
	private static final String COLOR_RELIABLE = GREEN;
	private static final String COLOR_UNRELIABLE = YELLOW;
	
	@Command("\\.translate ([a-z]+) ([a-z]+) \"(.*)\"")
	public void translate(Message message, String fromCode, String toCode, String phrase) {
		try {
			final JSONObject json = getJSON("http://ajax.googleapis.com/ajax/services/language/translate?q=" + urlEncode(phrase) + "&v=1.0&langpair=" + urlEncode(fromCode) + "%7C" + urlEncode(toCode));
			final int code = json.getInt("responseStatus");
			if(code == 200) {
				final String translation = json.getJSONObject("responseData").getString("translatedText");
				this.bot.reply(message, "Translation: " + translation);
			} else {
				this.bot.reply(message, COLOR_ERROR + "Unable to translate (HTTP code " + code + ")");
			}
		} catch(IOException e) {
			this.bot.reply(message, COLOR_ERROR + "Unable to connect to Google Translate");
		} catch(JSONException e) {
			this.bot.reply(message, COLOR_ERROR + "Problem parsing Google Translate response");
		}
	}
	
	@Command("\\.translate ([a-z]+) \"(.*)\"")
	public void detect(Message message, String toCode, String phrase) {
		try {
			final JSONObject json = getJSON("http://ajax.googleapis.com/ajax/services/language/detect?q=" + urlEncode(phrase) + "&v=1.0");
			final int code = json.getInt("responseStatus");
			if(code == 200) {
				final JSONObject data = json.getJSONObject("responseData");
				final String fromCode = data.getString("language");
				final boolean isReliable = data.getBoolean("isReliable");
				final double confidence = data.getDouble("confidence");
				
				this.bot.reply(message, (isReliable ? COLOR_RELIABLE + "Reliable" : COLOR_UNRELIABLE + "Unreliable") + " guess at source language (" + ((int)(confidence * 100)) + "%): " + fromCode);
				this.translate(message, fromCode, toCode, phrase);
			} else {
				this.bot.reply(message, COLOR_ERROR + "Unable to translate (HTTP code " + code + ")");
			}
		} catch(IOException e) {
			this.bot.reply(message, COLOR_ERROR + "Unable to connect to Google Translate");
		} catch(JSONException e) {
			this.bot.reply(message, COLOR_ERROR + "Problem parsing Google Translate response");
		}
	}
	
	@Command("\\.translate \"(.*)\"")
	public void toEnglish(Message message, String phrase) {
		detect(message, "en", phrase);
	}
	
	@Override public String getFriendlyName() {return "Translate";}
	@Override public String getDescription() {return "Translates text between languages";}
	@Override public String[] getExamples() {
		return new String[] {
				".translate _from_ _to_ \"_message_\" -- Translate _message_ from language _from_ to _to_ (use language codes)",
				".translate _to_ \"_message_\" -- Guess the from language and translate as above",
				".translate \"_message_\" -- Guess the from language and translate to English"
		};
	}
	@Override public String getOwner() {return "Morasique";}
}
