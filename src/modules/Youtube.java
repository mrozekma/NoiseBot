package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Scanner;
import java.time.Duration;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import debugging.Log;

import main.Message;
import main.NoiseModule;
import static main.Utilities.formatSeconds;

/**
 * Youtube
 *
 * @author Michael Mrozek
 *         Created Jun 17, 2009.
 */
public class Youtube extends NoiseModule {
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");

	private static final String COLOR_ERROR = RED;
	private static final String COLOR_INFO = PURPLE;
	public static final String API_URL = "https://www.googleapis.com/youtube/v3/videos?id=%s&key=%s&part=snippet,contentDetails,statistics,liveStreamingDetails";

	@Configurable("appid")
	private transient String appid = null;

	@Command(".*https?://(?:www.youtube.com/(?:watch\\?v=|v/|user/.*\\#p/u/[0-9]+/)|youtu.be/)([A-Za-z0-9_-]{11}).*")
	public void youtube(Message message, String videoID) {
		try {
			JSONObject data = getJSON(videoID);
			JSONArray videos = data.getJSONArray("items");

			// There should be exactly one entry for every video ID
			final int numResults = data.getJSONObject("pageInfo").getInt("totalResults");
			if(numResults == 0) {
				throw new FileNotFoundException(); // Youtube's HTTP code should cause this automatically, this case should never happen
			} else if(numResults != 1) {
				this.bot.sendMessage(COLOR_ERROR + "Found " + numResults + " videos with ID " + videoID);
				return;
			}

			final JSONObject video = videos.getJSONObject(0);
			final JSONObject snippet = video.getJSONObject("snippet");

			String author, title;
			Duration duration;
			int viewCount;
			Calendar published;

			author = snippet.getString("channelTitle");
			title = snippet.getString("title");

			duration = Duration.parse(video.getJSONObject("contentDetails").getString("duration"));
			viewCount = (int) video.getJSONObject("statistics").getLong("viewCount");

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

					this.bot.sendMessage(COLOR_INFO + encoded(title) +" (Live, posted by " + encoded(author) +", started on " + pubdate + " at " + pubtime + ", " + viewers +" viewers)");
					return;
				} else if(live.equals("upcoming")) {
					Calendar startTime = new GregorianCalendar();
					startTime.setTime(dateFormat.parse(liveDeets.getString("scheduledStartTime")));

					final String pubdate = String.format("%d %s %d", startTime.get(Calendar.DAY_OF_MONTH), startTime.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()), startTime.get(Calendar.YEAR));
					final String pubtime = String.format("%d:%02d %s", startTime.get(Calendar.HOUR_OF_DAY), startTime.get(Calendar.MINUTE), startTime.getTimeZone().getDisplayName(false, TimeZone.SHORT));

					this.bot.sendMessage(COLOR_INFO + encoded(title) + " (Upcoming, posted by " + encoded(author) + ", starting on " + pubdate + " at " + pubtime + ")");
					return;
				} else {
					Calendar endTime = new GregorianCalendar();
					endTime.setTime(dateFormat.parse(liveDeets.getString("actualEndTime")));

					final String pubdate = String.format("%d %s %d", endTime.get(Calendar.DAY_OF_MONTH), endTime.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()), endTime.get(Calendar.YEAR));

					this.bot.sendMessage(COLOR_INFO + encoded(title) + " (Recorded, posted by " + encoded(author) + ", ended on " + pubdate + ", " + viewCount + " views)");
					return;
				}
			}

			final String pubdate = String.format("%d %s %d", published.get(Calendar.DAY_OF_MONTH), published.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()), published.get(Calendar.YEAR));
			this.bot.sendMessage(COLOR_INFO +  encoded(title) + " (posted by " + encoded(author) + " on " + pubdate + ", " + formatSeconds(duration.getSeconds()) + ", " + viewCount + " views)");
		} catch(FileNotFoundException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to find Youtube video with ID " + videoID);
			Log.e(e);
		} catch(IOException e) {
			this.bot.sendMessage(COLOR_ERROR + "Unable to contact Youtube");
			Log.e(e);
		} catch (ParseException | JSONException e) {
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Youtube data");
			Log.e(e);
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
