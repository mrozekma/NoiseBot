package modules;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import main.Message;
import main.ModuleLoadException;
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
	private static final Map<String, String> LANGUAGE_KEYS = new HashMap<String, String>() {{
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

	private static final Map<String, String> LANGUAGE_NAMES = new HashMap<String, String>() {{
		for(Map.Entry<String, String> entry : LANGUAGE_KEYS.entrySet()) {
			put(entry.getValue().toLowerCase(), entry.getKey());
		}
	}};

	private String key;

	@Override public void init(NoiseBot bot, Map<String, String> config) throws ModuleLoadException {
		super.init(bot, config);
		if(!config.containsKey("key")) {
			throw new ModuleLoadException("No Google Translate key specified in configuration");
		}
		this.key = config.get("key");
	}

	private void translationHelper(String fromCode, String toCode, String phrase) {
		try {
			fromCode = fromCode == null ? null : interpretCode(fromCode);
			toCode = interpretCode(toCode);
		} catch(IllegalArgumentException e) {
			this.bot.sendMessage(COLOR_ERROR + e.getMessage());
			return;
		}

		try {
			final StringBuffer buffer = new StringBuffer();

			if(fromCode == null) {
				final JSONObject json = getJSON(String.format("https://www.googleapis.com/language/translate/v2/detect?key=%s&format=text&target=%s&q=%s", urlEncode(this.key), urlEncode(toCode), urlEncode(phrase)));
				if(json.has("data")) {
					final JSONObject data = json.getJSONObject("data");
					final JSONArray detections = data.getJSONArray("detections");
					final JSONArray inner = detections.getJSONArray(0);
					final JSONObject detection = inner.getJSONObject(0);

					fromCode = detection.getString("language");
					final boolean isReliable = detection.getBoolean("isReliable");
					final double confidence = detection.getDouble("confidence") * 100;

					buffer.append(LANGUAGE_KEYS.get(fromCode)).append(String.format(" (%s%2.2f%%%s)", isReliable ? COLOR_RELIABLE : COLOR_UNRELIABLE, confidence, NORMAL));
				} else if(json.has("error")) {
					final JSONObject error = json.getJSONObject("error");
					this.bot.sendMessage(COLOR_ERROR + "Google Translate error " + error.getInt("code") + ": " + error.get("message"));
					return;
				} else {
					this.bot.sendMessage(COLOR_ERROR + "Unknown Google Translate error");
					return;
				}
			} else {
				buffer.append(LANGUAGE_KEYS.get(fromCode));
			}

			buffer.append(" -> ").append(LANGUAGE_KEYS.get(toCode)).append(": ");

			final JSONObject json = getJSON(String.format("https://www.googleapis.com/language/translate/v2?key=%s&format=text&source=%s&target=%s&q=%s", urlEncode(this.key), urlEncode(fromCode), urlEncode(toCode), urlEncode(phrase)));
			if(json.has("data")) {
				final JSONObject data = json.getJSONObject("data");
				final JSONArray translations = data.getJSONArray("translations");
				buffer.append(translations.getJSONObject(0).get("translatedText"));
				this.bot.sendMessage(buffer.toString());
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

	@Command("\\.translate ([a-z]+) ([a-z]+) \"(.*)\"")
	public void translate(Message message, String fromCode, String toCode, String phrase) {
		this.translationHelper(fromCode, toCode, phrase);
	}

	@Command("\\.translate ([a-z]+) \"(.*)\"")
	public void detect(Message message, String toCode, String phrase) {
		this.translationHelper(null, toCode, phrase);
	}

	@Command("\\.translate \"(.*)\"")
	public void toEnglish(Message message, String phrase) {
		this.translationHelper(null, "en", phrase);
	}

	private static String interpretCode(String code) {
		if(LANGUAGE_KEYS.containsKey(code)) {
			return code;
		} else if(LANGUAGE_NAMES.containsKey(code.toLowerCase())) {
			return LANGUAGE_NAMES.get(code.toLowerCase());
		} else {
			throw new IllegalArgumentException("Unrecognized language code: " + code);
		}
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
}
