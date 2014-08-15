package modules;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.Scanner;
import java.util.Optional;

import main.Message;
import main.ModuleLoadException;
import main.NoiseBot;
import main.NoiseModule;
import static main.Utilities.getJSON;
import static main.Utilities.urlEncode;

import static org.jibble.pircbot.Colors.*;
import static panacea.Panacea.*;

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

	class Phrase {
		private String code;
		private String text;

		public Phrase(String code, String text)
		{
			this.code = code;
			this.text = text;
		}

		public String getCode() { return code; }
		@Override public String toString() { return text; }
	};

	class LanguageGuess {
		private String code;
		private double confidence;
		private boolean reliable;

		public LanguageGuess(String code, double confidence, boolean reliable)
		{
			this.code = code;
			this.confidence = confidence;
			this.reliable = reliable;
		}

		public String toString()
		{
			StringBuffer buffer = new StringBuffer();
			if (LANGUAGE_KEYS.containsKey(this.code))
				buffer.append(LANGUAGE_KEYS.get(this.code));
			else
				buffer.append(this.code);
			buffer.append(String.format(" (%s%2.2f%%%s)", this.reliable ? COLOR_RELIABLE : COLOR_UNRELIABLE, this.confidence, NORMAL));
			return buffer.toString();
		}

		public String getCode() { return code; }
	};

	private LanguageGuess guessLanguage(String phrase, String toCode)
	{
		try {
			final JSONObject json = getJSON(String.format("https://www.googleapis.com/language/translate/v2/detect?key=%s&format=text&target=%s&q=%s", urlEncode(this.key), urlEncode(toCode), urlEncode(phrase)), true);
			if(json.has("data")) {
				final JSONObject data = json.getJSONObject("data");
				final JSONArray detections = data.getJSONArray("detections");
				final JSONArray inner = detections.getJSONArray(0);
				final JSONObject detection = inner.getJSONObject(0);

				final String fromCode = detection.getString("language");
				final boolean isReliable = detection.getBoolean("isReliable");
				final double confidence = detection.getDouble("confidence") * 100;

				return new LanguageGuess(fromCode, confidence, isReliable);
			} else if(json.has("error")) {
				final JSONObject error = json.getJSONObject("error");
				this.bot.sendMessage(COLOR_ERROR + "Google Translate error " + error.getInt("code") + ": " + error.get("message"));
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Unknown Google Translate error");
			}
		} catch (Exception e) {
			Log.e(e);
		}

		return null;
	}

	private Optional<Phrase> translate(Phrase p, String toCode)
	{
		String fromCode = p.getCode();
		String phrase = p.toString();

		try {
			fromCode = interpretCode(fromCode);
			toCode = interpretCode(toCode);
		} catch(IllegalArgumentException e) {
			this.bot.sendMessage(COLOR_ERROR + e.getMessage());
			return Optional.empty();
		}

		try {
			final StringBuffer buffer = new StringBuffer();

			final JSONObject json = getJSON(String.format("https://www.googleapis.com/language/translate/v2?key=%s&format=text&source=%s&target=%s&q=%s", urlEncode(this.key), urlEncode(fromCode), urlEncode(toCode), urlEncode(phrase)));
			if(json.has("data")) {
				final JSONObject data = json.getJSONObject("data");
				final JSONArray translations = data.getJSONArray("translations");
				return Optional.of(new Phrase(toCode, translations.getJSONObject(0).get("translatedText").toString()));
			} else if(json.has("error")) {
				final JSONObject error = json.getJSONObject("error");
				this.bot.sendMessage(COLOR_ERROR + "Google Translate error " + error.getInt("code") + ": " + error.get("message"));
			} else {
				this.bot.sendMessage(COLOR_ERROR + "Unknown Google Translate error");
			}
		} catch(IOException e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Unable to connect to Google Translate");
			return Optional.empty();
		} catch(JSONException e) {
			Log.e(e);
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Google Translate response");
			return Optional.empty();
		}

		return Optional.empty();
	}

	private void translationHelper(String fromCode, String toCode, String phrase) {
		try {
			fromCode = fromCode == null ? null : interpretCode(fromCode);
			toCode = interpretCode(toCode);
		} catch(IllegalArgumentException e) {
			this.bot.sendMessage(COLOR_ERROR + e.getMessage());
			return;
		}

		final StringBuffer buffer = new StringBuffer();

		if(fromCode == null) {
			LanguageGuess lg = guessLanguage(phrase, toCode);
			if (lg == null)
				return;
			fromCode = lg.getCode();
			buffer.append(lg);
		} else {
			buffer.append(LANGUAGE_KEYS.get(fromCode));
		}

		buffer.append(" -> ").append(LANGUAGE_KEYS.get(toCode)).append(": ");

		translate(new Phrase(fromCode, phrase), toCode)
			.ifPresent(p -> this.bot.sendMessage(buffer.append(p).toString()));
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

	private void engrish(int times, String phrase)
	{
		Optional<Phrase> p = Optional.of(new Phrase("en", phrase));
		Set<String> langs = LANGUAGE_KEYS.keySet();
		langs.remove("en");

		for (int i = 0; i < times; i++) {
			final String language = getRandom(langs.toArray(new String[0]));
			langs.remove(language);
			p = p.flatMap(x -> translate(x, language));
		}

		p.flatMap(x -> translate(x, "en")).ifPresent(x -> this.bot.sendMessage(x.toString()));
	}

	@Command("\\.engrish \"(.*)\"")
	public void toEngrish(Message message, String phrase) {
		engrish(10, phrase);
	}

	@Command("\\.engrish ([0-9]+) (\"(.*)\"")
	public void toEngrish(Message message, int times, String phrase) {
		int boundedTimes = range(times, 1, 20);
		engrish(times, phrase);
	}

	private static String interpretCode(String code) {
		if(LANGUAGE_KEYS.containsKey(code)) {
			return code;
		} else if(LANGUAGE_NAMES.containsKey(code.toLowerCase())) {
			return LANGUAGE_NAMES.get(code.toLowerCase());
		} else {
			return code.toLowerCase();
		}
	}

	@Override public String getFriendlyName() {return "Translate";}
	@Override public String getDescription() {return "Translates text between languages";}
	@Override public String[] getExamples() {
		return new String[] {
				".translate _from_ _to_ \"_message_\" -- Translate _message_ from language _from_ to _to_ (use language codes)",
				".translate _to_ \"_message_\" -- Guess the from language and translate as above",
				".translate \"_message_\" -- Guess the from language and translate to English",
				".engrish \"_message_\" -- Translate from English into broken English by translating between other languages",
				".engrish 4 \"_message_\" -- Translate to 4 other language before going back to English"
		};
	}
}
