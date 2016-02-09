package modules;

import static main.Utilities.getJSON;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import main.*;
import org.json.JSONException;

import debugging.Log;

/**
 * Clip
 *
 * @author Michael Mrozek
 *         Created Feb 19, 2011.
 */
public class Clip extends NoiseModule {
	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("info", Style.MAGENTA);
		}};
	}

	@Command(".*(http://clip.mrozekma.com/[0-9a-fA-F-]{36}).*")
	public JSONObject clip(CommandContext ctx, String url) throws JSONException {
		try {
			return getJSON(url + "?info");
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to connect to mrozekma server");
		}
	}

	@View
	public void view(ViewContext ctx, JSONObject data) throws JSONException {
		ctx.respond("#info %s (from %s, %s-%s)", data.isNull("title") ? "Untitled" : data.get("title"), data.get("source"), data.get("start"), data.get("end"));
	}
	
	@Override public String getFriendlyName() {return "Clip";}
	@Override public String getDescription() {return "Outputs information about any mrozekma.com video clip URLs posted";}
	@Override public String[] getExamples() {
		return new String[] {
				"http://clip.mrozekma.com/c06c0612-349b-4580-b03e-ba8f771c2d0f"
		};
	}
}
