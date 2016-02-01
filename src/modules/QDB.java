package modules;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import com.ullink.slack.simpleslackapi.SlackAttachment;
import main.*;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import debugging.Log;

import static main.Utilities.getRandomInt;

/**
 * QDB
 *
 * @author Michael Mrozek
 *         Created Jul 12, 2010.
 */
public class QDB extends NoiseModule {
	private static class ParseException extends Exception {
		public ParseException(String message) {super(message);}
		public ParseException(Throwable t) {super(t);}
	}

	private static final int MAX_LINES = 2; // maximum lines in a random quote
	private static final int PERIOD = 30; // seconds
	private static final int TIMEOUT = 5; // seconds

	@Configurable("base-url")
	private String baseURL = null;

	private final Timer timer = new Timer();
	private int curID = 0;

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("quote", Style.CYAN);
		}};
	}

	@Override public void setConfig(Map<String, Object> config) throws ModuleInitException {
		super.setConfig(config);
		this.timer.scheduleAtFixedRate(new TimerTask() {
			@Override public void run() {
				QDB.this.checkForNewQuotes();
			}
		}, 0, PERIOD * 1000);
	}

	@Override public void unload() {
		super.unload();
		this.timer.cancel();
	}

	@Command("\\.(?:qdb|quote) ([0-9]+)")
	public JSONObject show(Message message, int id) throws JSONException {
		try {
			return this.getQuote(id);
		} catch(ParseException e) {
			Log.e(e);
			return new JSONObject().put("error", e.getMessage());
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to connect to QDB");
		}
	}

	@Command("\\.(?:qdb|quote)")
	public JSONObject showRandom(Message message) throws JSONException {
		try {
			final int maxId = this.getMaxID();
			Optional<JSONObject> quote = Optional.empty();
			do {
				try {
					quote = Optional.of(getQuote(getRandomInt(1, maxId)));
				} catch(ParseException e) {} // Probably the particular quy
			} while(!quote.isPresent() || quote.get().getJSONArray("lines").length() > MAX_LINES);
			return quote.get();
		} catch(ParseException e) {
			Log.e(e);
			return new JSONObject().put("error", e.getMessage());
		} catch(IOException e) {
			Log.e(e);
			return new JSONObject().put("error", "Unable to connect to QDB");
		}
	}

	@View
	public void plainView(Message message, JSONObject data) throws JSONException {
		final String[] lines = data.getStringArray("lines");
		if(lines.length == 0) { // I don't think this is ever true
			return;
		}
		final MessageBuilder builder = message.buildResponse();
		if(data.has("upvotes") && data.has("downvotes")) {
			builder.add("#quote (+%d/-%d) ", new Object[] {data.getInt("upvotes"), data.getInt("downvotes")});
		}
		builder.add("#quote %s", new Object[] {lines[0]}).send();
		for(int i = 1; i < lines.length; i++) {
			message.respond("#quote %s", lines[i]);
		}
	}

	@View(Protocol.Slack)
	public void slackView(Message message, JSONObject data) throws JSONException {
		final SlackNoiseBot bot = (SlackNoiseBot)this.bot;
		final String body = Arrays.stream(data.getStringArray("lines")).collect(Collectors.joining("\n"));
		StringBuilder votes = new StringBuilder();
		for(int i = 0; i < data.optInt("upvotes", 0); i++) {
			votes.append(":+1:");
		}
		for(int i = 0; i < data.optInt("downvotes", 0); i++) {
			votes.append(":-1:");
		}
		final SlackAttachment attachment = new SlackAttachment(String.format("Quote #%d", data.getInt("id")), body, body + "\n" + votes, null);
		attachment.setTitleLink(String.format("%s/%d", this.baseURL, data.getInt("id")));
		bot.sendAttachmentTo(message.getResponseTarget(), attachment);
	}

	private void checkForNewQuotes() {
		final int maxID;
		try {
			maxID = getMaxID();
		} catch(ParseException | IOException e) {
			Log.w(e);
			return;
		}

		if(this.curID == 0) {
			this.curID = maxID;
			Log.i("Initial QDB poll; set current ID to " + this.curID);
			return;
		}

		if(maxID == this.curID) {
			return;
		} else if(maxID < this.curID) {
			Log.e("QDB ID mismatch: %d < %d", maxID, this.curID);
		}

		Log.v("QDB poll; old ID was %d, new ID is ", this.curID, maxID);
		for(this.curID++; this.curID <= maxID; this.curID++) {
			this.bot.sendMessage("%s/%d", this.baseURL, this.curID);
		}

		this.curID--; // The for loop pushes it just past maxID
	}

	private JSONObject getQuote(int id) throws IOException, ParseException {
		Log.i("Getting quote %d", id);
		final Document doc = Jsoup.connect(String.format("%s/%d", this.baseURL, id)).timeout(TIMEOUT * 1000).get();

		Optional<Integer> upvotes = Optional.empty(), downvotes = Optional.empty();
		try {
			final Elements scoreSpan = doc.select("span.quote-rating");
			if(!scoreSpan.isEmpty()) {
				final int score = Integer.parseInt(scoreSpan.first().text().replace((char)8722, '-').replaceFirst("^\\+", ""));
				final Elements totalSpan = doc.select("span.quote-vote-count");
				if(!totalSpan.isEmpty() && totalSpan.first().text().charAt(0) == '/') {
					final int total = Integer.parseInt(totalSpan.first().text().substring(1));

					// Andy depresses me
					/*
					int upvotes = 0, downvotes = 0;
					if(score >= 0)
						upvotes = score;
					else
						downvotes = Math.abs(score);
					final int diff = total - Math.abs(score);
					assert(diff % 2 == 0);
					upvotes += diff/2;
					downvotes += diff/2;
					*/
					upvotes = Optional.of((total + score) / 2);
					downvotes = Optional.of((total - score) / 2);
				}
			}
		} catch(NumberFormatException e) {Log.e(e);}

		if(doc.select("title").first().html().contains("Quote Not Found")) {
			throw new ParseException("Quote not found");
		}

		final Elements e = doc.select("p");
		if(e.isEmpty()) {
			throw new ParseException("Unable to find quote block");
		}
		final String[] lines = e.first().html().replace("&nbsp;", " ").split("<br />");
		final String[] fmtLines = Arrays.stream(lines).map(line -> Jsoup.parse(line).text()).toArray(String[]::new);
		try {
			final JSONObject rtn = new JSONObject().put("id", id).put("lines", fmtLines);
			if(upvotes.isPresent()) {
				rtn.put("upvotes", upvotes.get().intValue());
			}
			if(downvotes.isPresent()) {
				rtn.put("downvotes", downvotes.get().intValue());
			}
			return rtn;
		} catch(JSONException ex) {
			throw new ParseException(ex);
		}
	}

	private int getMaxID() throws IOException, ParseException {
		final Document doc = Jsoup.connect(String.format("%s/browse", this.baseURL)).timeout(TIMEOUT * 1000).get();
		Elements e = doc.select("ul.quote-list > li");
		if(e.isEmpty()) {
			throw new ParseException("Unable to find top quote on browse page");
		}
		final String id = e.first().id();
		if(!id.startsWith("quote-")) {
			throw new ParseException("Unexpected ID on top quote on browse page");
		}
		return Integer.parseInt(id.substring("quote-".length()));
	}

	@Override public String getFriendlyName() {return "QDB";}
	@Override public String getDescription() {return "Displays quotes from the Quote Database at " + this.baseURL;}
	@Override public String[] getExamples() {
		return new String[] {
				".qdb -- Shows a random short quote",
				".qdb _id_ -- Shows quote _id_",
				".quote _id_ -- Same as .qdb"
		};
	}
}
