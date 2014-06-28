package modules;

import debugging.Log;
import main.Message;
import main.NoiseModule;
import static main.Utilities.formatSeconds;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

import static org.jibble.pircbot.Colors.PURPLE;
import static org.jibble.pircbot.Colors.RED;
import static panacea.Panacea.pluralize;

/**
 * Created by yellingdog on 3/29/14.
 */
public class Vimeo extends NoiseModule{
    private static final String COLOR_ERROR = RED;
    private static final String COLOR_INFO = PURPLE;

    @Command("https?://(?:.+\\.)?vimeo.com/(?:.+/)?([a-zA-Z0-9]+)")
    public void vimeo(Message message, String imgID) {
        try {
            final JSONObject data = this.getJSON(imgID);
            final StringBuilder buffer = new StringBuilder();

            buffer.append((data.has("title") && !data.isNull("title")) ? data.get("title") : "Untitled");
            buffer.append(" (");
            buffer.append("posted by ").append(data.getString("user_name"));
            if(data.getInt("duration") != 0)
                buffer.append(", ").append(formatSeconds(data.getInt("duration")));
            buffer.append(", ").append(pluralize(data.getInt("stats_number_of_plays"), "view", "views"));
            buffer.append(")");

            this.bot.sendMessage(COLOR_INFO + buffer);
        } catch(FileNotFoundException e) {
            Log.e(e);
            this.bot.sendMessage(COLOR_ERROR + String.format("No video with ID %s exists", imgID));
        } catch(Exception e) {
            Log.e(e);
            this.bot.sendMessage(COLOR_ERROR + "Problem parsing Vimeo data");
        }
    }

    private JSONObject getJSON(String vidID) throws IOException, JSONException {
        final URLConnection c = new URL("http://vimeo.com/api/v2/video/" + vidID + ".json").openConnection();
        final Scanner s = new Scanner(c.getInputStream());
        final StringBuilder buffer = new StringBuilder();
        while(s.hasNextLine()) {
            buffer.append(s.nextLine());
        }

        String jsonText = buffer.substring(1, buffer.length()-1);
        return new JSONObject(jsonText);
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
