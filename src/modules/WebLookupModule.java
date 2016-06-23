package modules;

import com.ullink.slack.simpleslackapi.SlackAttachment;
import debugging.Log;
import main.*;
import org.json.JSONException;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * @author Michael Mrozek
 *         Created Feb 24, 2016.
 */
public abstract class WebLookupModule extends NoiseModule {
	protected static class EntryNotFound extends Exception {}
	protected static class BodyNotFound extends Exception {}

	public JSONObject lookup(String term, String url) throws JSONException {
		final Document doc;
		try {
			doc = Jsoup.connect(url).get();
		} catch(HttpStatusException e) {
			if(e.getStatusCode() == 404) {
				return new JSONObject().put("fail_type", "no_entry").put("warning", String.format("No entry for %s", term));
			} else {
				Log.e(e);
				return new JSONObject().put("fail_type", "connect").put("warning", String.format("Unable to connect to %s: %s", this.getSiteName(), e.getMessage()));
			}
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("fail_type", "connect").put("warning", String.format("Unable to connect to %s: %s", this.getSiteName(), e.getMessage()));
		}

		try {
			final String text = this.getBody(term, url, doc);
			return new JSONObject().put("term", term).put("url", url).put("text", text);
		} catch(EntryNotFound e) {
			return new JSONObject().put("fail_type", "no_entry").put("warning", String.format("No entry for %s", term));
		} catch(BodyNotFound e) {
			return new JSONObject().put("fail_type", "no_body").put("warning", "Unable to find body");
		}
	}

	@View
	public void plainView(ViewContext ctx, JSONObject data) throws JSONException {
		final boolean includeLink = this.shouldIncludeLink(ctx.getCommandMethod());
		final boolean showErrors = this.shouldShowErrors(ctx.getCommandMethod());
		if(data.has("warning")) {
			if(showErrors) {
				ctx.respond("#error %s", data.get("warning"));
			}
			return;
		}

		ctx.buildResponse().addBulk(data.getString("text"), "...", includeLink ? " " + data.getString("url") : "", true).send();
	}

	@View(Protocol.Slack)
	public void slackView(ViewContext ctx, JSONObject data) throws JSONException {
		final boolean showErrors = this.shouldShowErrors(ctx.getCommandMethod());
		if(data.has("warning")) {
			if(showErrors) {
				ctx.respond("#error %s", data.get("warning"));
			}
			return;
		}

		final String text = data.getString("text");
		final SlackAttachment attachment = new SlackAttachment(data.getString("term"), text, text, null);
		attachment.setTitleLink(data.getString("url")); // We include the link regardless of shouldIncludeLink(), since it doesn't take up any space
		attachment.setThumbURL(this.getThumbnailURL());
		((SlackNoiseBot)this.bot).sendAttachmentTo(ctx.getResponseTarget(), attachment);
	}

	protected String getSiteName() {
		return this.getClass().getSimpleName();
	}

	protected abstract String getThumbnailURL();

	protected abstract String getBody(String term, String url, Document document) throws EntryNotFound, BodyNotFound;

	protected abstract boolean shouldIncludeLink(Method commandMethod);

	protected boolean shouldShowErrors(Method commandMethod) {
		// Provided a default for this because most modules should want to show errors all the time; Wikipedia is the exception with [[inline]] terms
		return true;
	}
}
