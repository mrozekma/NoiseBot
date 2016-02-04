package modules;

import java.io.IOException;
import java.util.*;

import main.CommandContext;
import main.MessageBuilder;
import main.NoiseModule;
import main.ViewContext;

import main.Style;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

import static main.Utilities.*;

/**
 * Translate
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Translate extends NoiseModule {
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

	private static final String ENGRISH_ORIGIN = "en";

	@Configurable("key")
	private String key = null;

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("reliable", Style.GREEN);
			put("unreliable", Style.YELLOW);
			put("language", Style.PLAIN);
		}};
	}

	@Command("\\.language (.+)")
	public JSONObject guessLanguage(CommandContext ctx, String phrase) throws JSONException {
		try {
			final JSONObject json = getJSON(String.format("https://www.googleapis.com/language/translate/v2/detect?key=%s&format=text&q=%s", urlEncode(this.key), urlEncode(phrase)), true);
			if(json.has("data")) {
				final JSONObject data = json.getJSONObject("data");
				final JSONArray detections = data.getJSONArray("detections");
				final JSONArray inner = detections.getJSONArray(0);
				final JSONObject detection = inner.getJSONObject(0);

				final String fromCode = detection.getString("language");
				final boolean isReliable = detection.getBoolean("isReliable");
				final double confidence = detection.getDouble("confidence") * 100;

				final JSONObject rtn = new JSONObject().put("language_code", fromCode).put("confidence", confidence).put("reliable", isReliable);
				if(LANGUAGE_KEYS.containsKey(fromCode)) {
					rtn.put("language_name", LANGUAGE_KEYS.get(fromCode));
				}
				return rtn;
			} else if(json.has("error")) {
				final JSONObject error = json.getJSONObject("error");
				return new JSONObject().put("error", String.format("Google Translate error %d: %s", error.getInt("code"), error.get("message"))).put("error_detail", error);
			} else {
				return new JSONObject().put("error", "Unknown Google Translate error");
			}
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to communicate with Google Translate: " + e.getMessage());
		}
	}

	@View(method = "guessLanguage")
	public void plainLanguageView(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("%(#language)s (# %2.2f%%#plain )", data.optString("language_name", data.getString("language_code")), data.getBoolean("reliable") ? "reliable" : "unreliable", data.getDouble("confidence"));
	}

	private static JSONObject getLanguage(String lang) throws JSONException {
		final String code;
		if(LANGUAGE_KEYS.containsKey(lang)) {
			code = lang;
		} else if(LANGUAGE_NAMES.containsKey(lang.toLowerCase())) {
			code = LANGUAGE_NAMES.get(lang.toLowerCase());
		} else {
			code = lang.toLowerCase();
		}

		final JSONObject rtn = new JSONObject().put("language_code", code);
		if(LANGUAGE_KEYS.containsKey(code)) {
			rtn.put("language_name", LANGUAGE_KEYS.get(code));
		}
		return rtn;
	}

	private JSONObject translate(CommandContext ctx, JSONObject fromLanguage, JSONObject toLanguage, String phrase) throws JSONException {
		try {
			final JSONObject json = getJSON(String.format("https://www.googleapis.com/language/translate/v2?key=%s&format=text&source=%s&target=%s&q=%s", urlEncode(this.key), urlEncode(fromLanguage.getString("language_code")), urlEncode(toLanguage.getString("language_code")), urlEncode(phrase)));
			if(json.has("data")) {
				final JSONObject data = json.getJSONObject("data");
				final JSONArray translations = data.getJSONArray("translations");
				final String translated = translations.getJSONObject(0).getString("translatedText");
				return new JSONObject().put("from_language", fromLanguage).put("to_language", toLanguage).put("from", phrase).put("to", translated);
			} else if(json.has("error")) {
				final JSONObject error = json.getJSONObject("error");
				return new JSONObject().put("error", String.format("Google Translate error %d: %s", error.getInt("code"), error.get("message"))).put("error_detail", error);
			} else {
				return new JSONObject().put("error", "Unknown Google Translate error");
			}
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to communicate with Google Translate: " + e.getMessage());
		}
	}

	private JSONObject translate(CommandContext ctx, JSONObject fromLanguage, String toCode, String phrase) throws JSONException {
		return this.translate(ctx, fromLanguage, getLanguage(toCode), phrase);
	}

	@Command("\\.translate ([a-z]+) ([a-z]+) \"(.*)\"")
	public JSONObject translate(CommandContext ctx, String fromCode, String toCode, String phrase) throws JSONException {
		return this.translate(ctx, getLanguage(fromCode), toCode, phrase);
	}

	@Command("\\.translate ([a-z]+) \"(.*)\"")
	public JSONObject translate(CommandContext ctx, String toCode, String phrase) throws JSONException {
		return this.translate(ctx, this.guessLanguage(ctx, phrase), toCode, phrase);
	}

	@Command("\\.translate \"(.*)\"")
	public JSONObject translate(CommandContext ctx, String phrase) throws JSONException {
		return this.translate(ctx, "en", phrase);
	}

	@View(method = "translate")
	public void plainTranslateView(ViewContext ctx, JSONObject data) throws JSONException {
		final MessageBuilder builder = ctx.buildResponse();

		JSONObject language = data.getJSONObject("from_language");
		builder.add("#language %s", new Object[] {language.optString("language_name", language.getString("language_code"))});
		if(language.has("reliable") && language.has("confidence")) {
			builder.add(" (# %2.2f%%#plain )", new Object[] {language.getBoolean("reliable") ? "reliable" : "unreliable", language.getDouble("confidence")});
		}

		builder.add(" -> ");
		language = data.getJSONObject("to_language");
		builder.add("#language %s", new Object[] {language.optString("language_name", language.getString("language_code"))});

		builder.add(": ");
		builder.add(data.getString("to"));
		builder.send();
	}

	@Command("\\.engrish \"(.*)\"")
	public JSONObject engrish(CommandContext ctx, String phrase) throws JSONException {
		return this.engrish(ctx, 4, phrase);
	}

	@Command("\\.engrish ([0-9]+) \"(.*)\"")
	public JSONObject engrish(CommandContext ctx, int times, String phrase) throws JSONException {
		times = range(times, 1, 20);
		String[] langs = LANGUAGE_KEYS.keySet().toArray(new String[0]);
		for(int i = 0; i < times; i++) {
			// Swap langs[i] with a random choice later in the array
			int j;
			do {
				j = getRandomInt(i, langs.length - 1);
			} while(langs[j].equals(ENGRISH_ORIGIN));
			final String swap = langs[i];
			langs[i] = langs[j];
			langs[j] = swap;
		}
		langs[times] = ENGRISH_ORIGIN;

		JSONObject fromLang = getLanguage(ENGRISH_ORIGIN);
		final JSONObject rtn = new JSONObject().put("original", phrase);
		for(int i = 0; i <= times; i++) {
			final JSONObject toLang = getLanguage(langs[i]);
			final JSONObject translated = this.translate(ctx, fromLang, toLang, phrase);
			if(translated.has("error")) {
				return translated;
			}
			phrase = translated.getString("to");
			rtn.append("engrish", new JSONObject().put("language", toLang).put("translated", phrase));
			fromLang = toLang;
		}
		return rtn;
	}

	@View(method = "engrish")
	public void plainEngrishView(ViewContext ctx, JSONObject data) throws JSONException {
		final JSONArray chain = data.getJSONArray("engrish");
		final String[] languageChain = new String[chain.length() + 1];
		languageChain[0] = getLanguage(ENGRISH_ORIGIN).optString("language_name", ENGRISH_ORIGIN);
		String translated = data.getString("original");
		for(int i = 0; i < chain.length(); i++) {
			final JSONObject entry = chain.getJSONObject(i);
			final JSONObject lang = entry.getJSONObject("language");
			languageChain[i + 1] = lang.optString("language_name", lang.getString("language_code"));
			translated = entry.getString("translated");
		}
		ctx.respond("#([ -> ] %(#language)s)", (Object)languageChain);
		ctx.respond("%s", translated);
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
