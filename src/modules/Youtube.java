package modules;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.time.Duration;

import main.Style;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

import main.Message;
import main.NoiseModule;
import static main.Utilities.formatSeconds;
import static main.Utilities.pluralize;

/**
 * Youtube
 *
 * @author Michael Mrozek
 *         Created Jun 17, 2009.
 */
public class Youtube extends NoiseModule {
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
	private static final String API_URL = "https://www.googleapis.com/youtube/v3/videos?id=%s&key=%s&part=snippet,contentDetails,statistics,liveStreamingDetails";

	@Configurable("appid")
	private transient String appid = null;

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("info", Style.MAGENTA);
		}};
	}

	@Command(".*https?://(?:www.youtube.com/(?:watch\\?v=|v/|user/.*\\#p/u/[0-9]+/)|youtu.be/)([A-Za-z0-9_-]{11}).*")
	public JSONObject youtube(Message message, String videoID) throws JSONException {
		try {
			JSONObject data = getJSON(videoID);
			JSONArray videos = data.getJSONArray("items");

			// There should be exactly one entry for every video ID
			final int numResults = data.getJSONObject("pageInfo").getInt("totalResults");
			if(numResults == 0) {
				throw new FileNotFoundException(); // Youtube's HTTP code should cause this automatically, this case should never happen
			} else if(numResults != 1) {
				return new JSONObject().put("error", String.format("Found %d videos with ID %s", numResults, videoID));
			}

			return videos.getJSONObject(0);
		} catch(FileNotFoundException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to find Youtube video with ID " + videoID);
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to contact Youtube");
		} catch(JSONException e) {
			Log.e(e);
			return new JSONObject().put("error", "Problem parsing Youtube data");
		}
	}

	@View
	public void plainView(Message message, JSONObject video) throws JSONException {
		final JSONObject snippet = video.getJSONObject("snippet");

		String author, title;
		Duration duration;
		int viewCount;
		Calendar published;

		try {
			author = snippet.getString("channelTitle");
			title = snippet.getString("title");

			duration = Duration.parse(video.getJSONObject("contentDetails").getString("duration"));
			viewCount = (int)video.getJSONObject("statistics").getLong("viewCount");

			published = new GregorianCalendar();
			published.setTime(dateFormat.parse(snippet.getString("publishedAt")));

			if(video.has("liveStreamingDetails")) {
				String live = snippet.getString("liveBroadcastContent");
				JSONObject liveDeets = video.getJSONObject("liveStreamingDetails");
				if(live.equals("live")) {
					Calendar startTime = new GregorianCalendar();
					startTime.setTime(dateFormat.parse(liveDeets.getString("actualStartTime")));

					long viewers = liveDeets.getLong("concurrentViewers");

					final String pubdate = String.format("%d %s %d", startTime.get(Calendar.DAY_OF_MONTH), startTime.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()), startTime.get(Calendar.YEAR));
					final String pubtime = String.format("%d:%02d %s", startTime.get(Calendar.HOUR_OF_DAY), startTime.get(Calendar.MINUTE), startTime.getTimeZone().getDisplayName(false, TimeZone.SHORT));

					message.respond("#info %s (Live, posted by %s, started on %s at %s, %s)", title, author, pubdate, pubtime, pluralize(viewers, "viewer", "viewers"));
					return;
				} else if(live.equals("upcoming")) {
					Calendar startTime = new GregorianCalendar();
					startTime.setTime(dateFormat.parse(liveDeets.getString("scheduledStartTime")));

					final String pubdate = String.format("%d %s %d", startTime.get(Calendar.DAY_OF_MONTH), startTime.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()), startTime.get(Calendar.YEAR));
					final String pubtime = String.format("%d:%02d %s", startTime.get(Calendar.HOUR_OF_DAY), startTime.get(Calendar.MINUTE), startTime.getTimeZone().getDisplayName(false, TimeZone.SHORT));

					message.respond("#info %s (Upcoming, posted by %s, starting on %s at %s)", title, author, pubdate, pubtime);
					return;
				} else {
					Calendar endTime = new GregorianCalendar();
					endTime.setTime(dateFormat.parse(liveDeets.getString("actualEndTime")));

					final String pubdate = String.format("%d %s %d", endTime.get(Calendar.DAY_OF_MONTH), endTime.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()), endTime.get(Calendar.YEAR));

					message.respond("#info %s (Recorded, posted by %s, ended on %s, %s)", title, author, pubdate, pluralize(viewCount, "view", "views"));
					return;
				}
			}

			final String pubdate = String.format("%d %s %d", published.get(Calendar.DAY_OF_MONTH), published.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()), published.get(Calendar.YEAR));
			message.respond("#info %s (posted by %s on %s, %s, %s)", title, author, pubdate, formatSeconds(duration.getSeconds()), pluralize(viewCount, "view", "views"));
		} catch(ParseException e) {
			Log.e(e);
			message.respond("#error Parse exception: %s", e.getMessage());
		}
	}

	private JSONObject getJSON(String vidID) throws IOException, JSONException {
		final URLConnection c = new URL(String.format(API_URL, vidID, appid)).openConnection();
		final Scanner s = new Scanner(c.getInputStream());
		final StringBuilder buffer = new StringBuilder();
		while(s.hasNextLine()) {
			buffer.append(s.nextLine());
		}

		String jsonText = buffer.toString();
		return new JSONObject(jsonText);
	}

	@Override public String getFriendlyName() {return "Youtube";}
	@Override public String getDescription() {return "Outputs information about any youtube URLs posted";}
	@Override public String[] getExamples() {
		return new String[] {
				"http://www.youtube.com/watch?v=8AOfbnGkuGc"
		};
	}
}
