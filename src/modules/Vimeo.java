package modules;

import debugging.Log;
import main.*;
import static main.Utilities.formatSeconds;

import org.json.JSONException;

import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static main.Utilities.pluralize;

/**
 * Created by yellingdog on 3/29/14.
 */
public class Vimeo extends NoiseModule{
    @Override protected Map<String, Style> styles() {
        return new HashMap<String, Style>() {{
            put("info", Style.MAGENTA);
        }};
    }

    @Command(".*https?://(?:.+\\.)?vimeo.com/(?:.+/)?([a-zA-Z0-9]+).*")
    public JSONObject vimeo(CommandContext ctx, String vidID) throws JSONException {
        try {
            final URLConnection c = new URL("http://vimeo.com/api/v2/video/" + vidID + ".json").openConnection();
            final Scanner s = new Scanner(c.getInputStream());
            final StringBuilder buffer = new StringBuilder();
            while(s.hasNextLine()) {
                buffer.append(s.nextLine());
            }

            String jsonText = buffer.substring(1, buffer.length()-1);
            return new JSONObject(jsonText);
        } catch(FileNotFoundException e) {
            Log.e(e);
            return new JSONObject().put("error", String.format("No video with ID %s exists", vidID));
        } catch(Exception e) {
            Log.e(e);
            return new JSONObject().put("error", "Problem parsing Vimeo data");
        }
    }

    @View
    public void plainView(ViewContext ctx, JSONObject data) throws JSONException {
        final MessageBuilder builder = ctx.buildResponse();
        builder.add("#info %s (posted by %s", new Object[] {data.optString("title", "Untitled"), data.get("user_name")});
        if(data.getInt("duration") > 0) {
            builder.add("#info , %s", new Object[] {formatSeconds(data.getInt("duration"))});
        }
        builder.add("#info , %s)", new Object[] {pluralize(data.getInt("stats_number_of_plays"), "view", "views")});
        builder.send();
    }

    @Override
    public String getFriendlyName() {
        return "Vimeo";
    }

    @Override
    public String getDescription() {
        return "Provides basic information about posted Vimeo videos";
    }

    @Override
    public String[] getExamples() {
        return new String[] { "http://vimeo.com/1"};
    }
}
