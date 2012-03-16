package modules;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import main.Message;
import main.NoiseBot;
import main.NoiseModule;
import static main.Utilities.getJSON;
import static main.Utilities.urlEncode;

import static org.jibble.pircbot.Colors.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

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

	// http://code.google.com/apis/language/translate/v2/using_rest.html#language-params
	private static final Map<String, String> LANGUAGES = new HashMap<String, String>() {{
		put("af", "Afrikaans");
		put("sq", "Albanian");
		put("ar", "Arabic");
		put("be", "Belarusian");
		put("bg", "Bulgarian");
		put("ca", "Catalan");
		put("zh-CN", "Chinese Simplified");
		put("zh-TW", "Chinese Traditional");
		put("hr", "Croatian");
		put("cs", "Czech");
		put("da", "Danish");
		put("nl", "Dutch");
		put("en", "English");
		put("et", "Estonian");
		put("tl", "Filipino");
		put("fi", "Finnish");
		put("fr", "French");
		put("gl", "Galician");
		put("de", "German");
		put("el", "Greek");
		put("iw", "Hebrew");
		put("hi", "Hindi");
		put("hu", "Hungarian");
		put("is", "Icelandic");
		put("id", "Indonesian");
		put("ga", "Irish");
		put("it", "Italian");
		put("ja", "Japanese");
		put("ko", "Korean");
		put("lv", "Latvian");
		put("lt", "Lithuanian");
		put("mk", "Macedonian");
		put("ms", "Malay");
		put("mt", "Maltese");
		put("no", "Norwegian");
		put("fa", "Persian");
		put("pl", "Polish");
		put("pt", "Portuguese");
		put("ro", "Romanian");
		put("ru", "Russian");
		put("sr", "Serbian");
		put("sk", "Slovak");
		put("sl", "Slovenian");
		put("es", "Spanish");
		put("sw", "Swahili");
		put("sv", "Swedish");
		put("th", "Thai");
		put("tr", "Turkish");
		put("uk", "Ukrainian");
		put("vi", "Vietnamese");
		put("cy", "Welsh");
		put("yi", "Yiddish");
	}};
	
	private static final File KEY_FILE = new File("translate-key");
	private String key;
	
	@Override public void init(NoiseBot bot) {
	    super.init(bot);
	    
		try {
			key = new Scanner(KEY_FILE).nextLine();
		} catch(IOException e) {
			Log.e(e);
			this.bot.sendNotice("Problem reading key file: " + e.getMessage());
		}
    }

	@Command("\\.translate ([a-z]+) ([a-z]+) \"(.*)\"")
	public void translate(Message message, String fromCode, String toCode, String phrase) {
		for(String code : new String[] {fromCode, toCode}) {
			if(!LANGUAGES.containsKey(code)) {
				this.bot.sendMessage(COLOR_ERROR + "Unrecognized language code: " + code);
				return;
			}
		}
		
		try {
			final JSONObject json = getJSON(String.format("https://www.googleapis.com/language/translate/v2?key=%s&format=text&source=%s&target=%s&q=%s", urlEncode(this.key), urlEncode(fromCode), urlEncode(toCode), urlEncode(phrase))); 
			if(json.has("data")) {
				final JSONObject data = json.getJSONObject("data");
				final JSONArray translations = data.getJSONArray("translations");
				this.bot.sendMessage(String.format("%s -> %s translation: %s", LANGUAGES.get(fromCode), LANGUAGES.get(toCode), translations.getJSONObject(0).get("translatedText")));
			} else if(json.has("error")) {
				final JSONObject error = json.getJSONObject("error");
				this.bot.sendMessage(COLOR_ERROR + "Google Translate error " + error.getInt("code") + ": " + error.get("message"));
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Unknown Google Translate error");
			}
		} catch(IOException e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Unable to connect to Google Translate");
		} catch(JSONException e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Google Translate response");
		}
	}
	
	@Command("\\.translate ([a-z]+) \"(.*)\"")
	public void detect(Message message, String toCode, String phrase) {
		if(!LANGUAGES.containsKey(toCode)) {
			this.bot.sendMessage(COLOR_ERROR + "Unrecognized language code: " + toCode);
			return;
		}
		
		try {
			final JSONObject json = getJSON(String.format("https://www.googleapis.com/language/translate/v2/detect?key=%s&format=text&target=%s&q=%s", urlEncode(this.key), urlEncode(toCode), urlEncode(phrase)));
			if(json.has("data")) {
				final JSONObject data = json.getJSONObject("data");
				final JSONArray detections = data.getJSONArray("detections");
				final JSONArray inner = detections.getJSONArray(0);
				final JSONObject detection = inner.getJSONObject(0);

				final String fromCode = detection.getString("language");
				final boolean isReliable = detection.getBoolean("isReliable");
				final double confidence = detection.getDouble("confidence") * 100;
				
				this.bot.sendMessage(String.format("%s guess at source language (%2.2f%%): %s", (isReliable ? COLOR_RELIABLE + "Reliable" : COLOR_UNRELIABLE + "Unreliable"), confidence, LANGUAGES.get(fromCode)));
				this.translate(message, fromCode, toCode, phrase);
			} else if(json.has("error")) {
				final JSONObject error = json.getJSONObject("error");
				this.bot.sendMessage(COLOR_ERROR + "Google Translate error " + error.getInt("code") + ": " + error.get("message"));
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Unknown Google Translate error");
			}
		} catch(IOException e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Unable to connect to Google Translate");
		} catch(JSONException e) {
			Log.e(e);
			e.printStackTrace();
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Google Translate response");
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

	@Override public File[] getDependentFiles() {return new File[] {KEY_FILE};}
}
