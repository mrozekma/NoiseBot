package modules;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import main.*;
import org.json.JSONException;

import static main.Utilities.range;

/**
 * Links
 *
 * @author Michael Mrozek
 *         Created Oct 10, 2009.
 */
public class Links extends NoiseModule implements Serializable {
	private static class CachedMessage implements Serializable, Comparable<CachedMessage> {
		private Date date;
		private Message message;
		
		private CachedMessage() {}
		public CachedMessage(Message message) {
			this.date = new Date();
			this.message = message;
		}

		public boolean fromUser(final String user) { return user.equals(this.message.getSender()); }

		@Override public String toString() {return this.date + " " + this.message;}
		@Override public int compareTo(CachedMessage c) { return c.date.compareTo(this.date); }
	}

	private Map<String, CachedMessage> links = new HashMap<>();
	private String[] lastN = {null, null, null, null, null};

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("dup", Style.RED);
			put("recap", Style.YELLOW);
		}};
	}

	@Command(".*((?:ftp|http|https):\\/\\/(?:\\w+:{0,1}\\w*@)?(?:\\S+)(?::[0-9]+)?(?:\\/|\\/(?:[\\w#!:.?+=&%@!\\-\\/]))?).*")
	public void url(CommandContext ctx, String url) {
		// We could make this command return data if the link is a duplicate; the problem is I don't really want to modify the list of seen links in that case
		if(this.links.containsKey(url)) {
			ctx.respond("#dup Duplicate URL: %s", this.links.get(url));
		} else {
			this.links.put(url, new CachedMessage(ctx.getMessage()));
			for(int i = this.lastN.length - 2; i >= 0; i--) {
				this.lastN[i + 1] = this.lastN[i];
			}
			this.lastN[0] = url;
			this.save();
		}
	}

	@Command("\\.(?:lasturls|links) ([0-9]+)")
	public JSONObject lastURLs(CommandContext ctx, int num) throws JSONException {
		num = range(num, 1, this.lastN.length);
		return new JSONObject().put("links", Arrays.stream(this.lastN).filter(url -> url != null).limit(num).map(url -> this.links.get(url)).toArray(CachedMessage[]::new));
	}

	@Command("\\.(?:lasturls|links) (\\w+)")
	public JSONObject lastURLs(CommandContext ctx, String user) throws JSONException {
		final int num = 5;
		return new JSONObject().put("links", this.links.values().stream().filter(c -> c.fromUser(user)).sorted().limit(num).toArray(CachedMessage[]::new));
	}

	@Command("\\.(?:lasturls|links)")
	public JSONObject lastURLsDefault(CommandContext ctx) throws JSONException {
		return this.lastURLs(ctx, lastN.length);
	}

	@View
	public void viewLastURLs(ViewContext ctx, JSONObject data) throws JSONException {
		final JSONArray cms = data.getJSONArray("links");
		cms.stream().forEach(link -> ctx.respond("#recap %s", link));
	}

	@View(Protocol.Slack)
	public void slackViewLastURLs(ViewContext ctx, JSONObject data) throws JSONException {
		final CachedMessage[] cms = data.getJavaArray("links", CachedMessage.class);
		//TODO Links come out with <> around them for some reason
		ctx.respond("#([\n] #recap " + MessageBuilder.BULLET + " %s)", (Object)cms);
	}

	@Override public String getFriendlyName() {return "Links";}
	@Override public String getDescription() {return "Keeps track of mentioned links and announces when a link has been duplicated";}
	@Override public String[] getExamples() {
		return new String[] {
				"http://www.google.com/",
				"http://www.google.com/ -- Announces that the link has been said in the past",
				".lasturls -- Shows the last " + this.lastN.length + " URLs sent to the channel",
				".lasturls _n_ -- Shows the last _n_ URLs sent to the channel",
				".lasturls _user_ -- Shows the last URLs sent to the channel by _user_",
				".links -- Same as .lasturls"
		};
	}
}
