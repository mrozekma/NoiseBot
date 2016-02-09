package modules;

import main.*;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import debugging.Log;

import java.util.*;
import java.io.Serializable;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Weather
 * Based off the SO module
 *
 * @author Michael Auchter
 *         Created Sep 23, 2011.
 */
public class Weather extends NoiseModule implements Serializable
{
	private static class Location implements Comparable<Location>, Serializable {
		public final int woeid;
		public final String city;
		public final String state;

		public Location(int woeid, String city, String state) {
			this.woeid = woeid;
			this.city = city;
			this.state = state;
		}

		public static Location parse(Document doc) {
			try {
				final int woeid = Integer.parseInt(doc.select("place > woeid").first().text());
				final String city = doc.select("place > [type=Town]").first().text();
				final String state;
				{
					Element e = doc.select("place > [type=State]").first();
					if(e != null) {
						if(e.hasAttr("code") && e.attr("code").startsWith("US-")) {
							state = e.attr("code").substring(3);
						} else {
							state = e.text();
						}
					} else {
						e = doc.select("place > country[code]").first();
						state = e.attr("code");
					}
				}
				return new Location(woeid, city, state);
			} catch(Exception e) {
				Log.e(e);
				return null;
			}
		}

		public JSONObject pack() throws JSONException {
			return new JSONObject().put("woeid", this.woeid).put("city", this.city).put("state", this.state);
		}

		public static Location unpack(JSONObject data) throws JSONException {
			return new Location(data.getInt("woeid"), data.getString("city"), data.getString("state"));
		}

		@Override public boolean equals(Object o) {
			return (o instanceof Location) && ((Location)o).woeid == this.woeid;
		}

		// Controls the order conditions will be displayed in
		@Override public int compareTo(Location o) {
			return Integer.compare(this.woeid, o.woeid);
		}

		@Override public String toString() {
			return String.format("%s, %s", this.city, this.state);
		}
	}

	private static class Condition implements Comparable<Condition> {
		public final Location loc;
		public final int temp;
		public final String text;

		public Condition(Location loc, int temp, String text) {
			this.loc = loc;
			this.temp = temp;
			this.text = text;
		}

		public JSONObject pack() throws JSONException {
			return new JSONObject().put("location", this.loc.pack()).put("temp", this.temp).put("condition", this.text);
		}

		public static Condition unpack(JSONObject data) throws JSONException {
			return new Condition(Location.unpack(data.getJSONObject("location")), data.getInt("temp"), data.getString("condition"));
		}

		@Override public int compareTo(Condition o) {
			return this.loc.compareTo(o.loc);
		}
	}

	private static final String WEATHER_URL = "http://weather.yahooapis.com/forecastrss?w=";
	private static final String PLACE_TO_WOEID_URL = "http://where.yahooapis.com/v1/places.q('%s')?appid=%s";
	private static final String WOEID_TO_PLACE_URL = "http://where.yahooapis.com/v1/place/%d?appid=%s";
	private static final int TIMEOUT = 5; // seconds

	private static final Map<String, String> shortNames = new HashMap<String, String>() {{
		put("Partly Cloudy", "cloudy-");
		put("Mostly Cloudy", "cloudy+");
		put("Thunderstorms", "storms");
	}};

	@Configurable("appid")
	private transient String appid = null;

	// nick -> user's location. Perfect for NSA surveillance teams
	private final Map<String, Location> locations = new HashMap<>();

	@Override protected Map<String, Style> styles() {
		return new HashMap<String, Style>() {{
			put("info", Style.MAGENTA);
			put("loc", Style.CYAN);
			put("text", Style.YELLOW);
			put("temp", Style.MAGENTA.update("bold"));
		}};
	}

	private static Document getXML(String url) throws Exception {
		return Jsoup.parse(new URL(url), TIMEOUT * 1000);
	}

	private Condition getWeather(Location loc) {
		try {
			final String uri = WEATHER_URL + loc.woeid;
			final Node cond = getXML(uri).select("yweather|condition").first();
			return new Condition(loc, Integer.parseInt(cond.attr("temp")), cond.attr("text"));
		} catch (Exception e) {
			Log.e(e);
			return null;
		}
	}

	private Location locationLookup(int woeid) {
		if(this.appid == null) {
			return null;
		}

		try {
			return Location.parse(getXML(String.format(WOEID_TO_PLACE_URL, woeid, this.appid)));
		} catch(Exception e) {
			Log.e(e);
			return null;
		}
	}

	private Location locationLookup(String desc) {
		if(this.appid == null) {
			return null;
		}

		try {
			return Location.parse(getXML(String.format(PLACE_TO_WOEID_URL, URLEncoder.encode(desc, "UTF-8"), this.appid)));
		} catch(Exception e) {
			Log.e(e);
			return null;
		}
	}

	private Map<Location, Condition> getWeather(boolean all) {
		final List<String> nicks = Arrays.asList(this.bot.getNicks());
		final Set<Location> locations = new HashSet<>();

		if(all) {
			locations.addAll(this.locations.values());
		} else {
			for(Map.Entry<String, Location> entry : this.locations.entrySet()) {
				if(nicks.contains(entry.getKey())) {
					locations.add(entry.getValue());
				}
			}
		}

		final Map<Location, Condition> rtn = new LinkedHashMap<>();
		for(Location location : locations) {
			rtn.put(location, this.getWeather(location));
		}
		return rtn;
	}

	@Command("\\.weatheradd ([^:]+)")
	public void weatherAdd(CommandContext ctx, String loc) {
		this.weatherAdd(ctx, ctx.getMessageSender(), loc);
	}

	@Command("\\.weatheradd ([^ :]+): (.+)")
	public void weatherAdd(CommandContext ctx, String nick, String locDesc) {
		Location loc = null;
		try {
			// I'm just hoping WOEIDs are never 5 digits, since they'll be indistinguishable from zipcodes
			if(locDesc.length() != 5) {
				final int woeid = Integer.parseInt(locDesc);
				loc = this.locationLookup(woeid);
			}
		} catch(NumberFormatException e) {}

		if(loc == null) {
			loc = this.locationLookup(locDesc);
		}

		if(loc == null) {
			ctx.respond("#error Unable to determine location");
			return;
		}

		this.locations.put(nick, loc);
		this.save();
		ctx.respond("#success Added location %d (%s) for %s", loc.woeid, loc, nick);
	}

	@Command("\\.weatherrm ([^ :]+)")
	public void weatherRemove(CommandContext ctx, String nick) {
		if(this.locations.containsKey(nick)) {
			final Location loc = this.locations.remove(nick);
			this.save();
			ctx.respond("#success Removed %s (%s) from weather listings", nick, loc);
		} else {
			ctx.respond("#error No location known for %s", nick);
		}
	}

	@Command("\\.weatherls")
	public JSONObject weatherList(CommandContext ctx) throws JSONException {
		final JSONObject rtn = new JSONObject();
		for(Map.Entry<String, Location> entry : this.locations.entrySet()) {
			rtn.put(entry.getKey(), entry.getValue().pack());
		}
		return rtn;
	}

	@View(method = "weatherList")
	public void plainWeatherListView(ViewContext ctx, JSONObject data) throws JSONException {
		final List<Object> args = new LinkedList<>();
		for(Iterator<String> iter = data.keys(); iter.hasNext();) {
			final String nick = iter.next();
			args.add(nick);
			args.add(Location.unpack(data.getJSONObject(nick)));
		}
		ctx.respond("#([; ] #info %s - %s)", (Object)args.toArray());
	}

	@Command("\\.(weather|wx)(?: ([.*]))?")
	public JSONObject weather(CommandContext ctx, String type, String filter) throws JSONException {
		final boolean shortForm = type.equals("wx");
		final JSONObject rtn = new JSONObject().put("short_form", shortForm).put("weather", new Object[0]);
		if(".".equals(filter)) { // Show only the sender's weather
			rtn.put("filter", "sender");
			if(this.locations.containsKey(ctx.getMessageSender())) {
				rtn.append("weather", this.getWeather(this.locations.get(ctx.getMessageSender())).pack());
			}
		} else {
			final boolean includeOfflineUsers = "*".equals(filter);
			rtn.put("filter", includeOfflineUsers ? "all" : "online");
			List<String> send = new Vector<>();
			for(Map.Entry<Location, Condition> wx : this.getWeather(includeOfflineUsers).entrySet()) {
				if(wx.getValue() == null) {
					return new JSONObject().put("error", "Problem parsing Weather data");
				}
				rtn.append("weather", wx.getValue().pack());
			}
		}
		return rtn;
	}

	@View(method = "weather")
	public void plainWeatherView(ViewContext ctx, JSONObject data) throws JSONException {
		final JSONArray entries = data.getJSONArray("weather");
		final Set<Condition> sortedConditions = new TreeSet<>();
		for(int i = 0; i < entries.length(); i++) {
			sortedConditions.add(Condition.unpack(entries.getJSONObject(i)));
		}
		final List<Object> args = new LinkedList<>();
		if(data.getBoolean("short_form")) {
			for(Condition cond : sortedConditions) {
				final String txt = shortNames.entrySet().stream().reduce(cond.text, (text, entry) -> text.replace(entry.getKey(), entry.getValue()), (before, after) -> after);
				args.add(cond.loc.city);
				args.add(cond.temp);
				args.add(txt.toLowerCase());
			}
			ctx.respond("#([  |  ] %(#loc)s %(#temp)s %(#text)s)", (Object)args.toArray());
		} else {
			for(Condition cond : sortedConditions) {
				args.add(cond.loc);
				args.add(cond.text);
				args.add(cond.temp + "F");
			}
			ctx.respond("#([ ] #info [%(#loc)s: %(#text)s, %(#temp)s])", (Object)args.toArray());
		}
	}

	@Override public String getFriendlyName() { return "Weather"; }
	@Override public String getDescription() { return "Outputs the current weather conditions in cities occupied by #rhnoise"; }
	@Override public String[] getExamples() {
		return new String[] {
			".weather -- Show weather for users currently in the room",
			".weather _*_ -- Show weather for all recorded users",
			".weather _._ -- Show weather for the sender",
			".wx -- Show weather information in a condensed form",
			".weatheradd _location_ -- Record the user's location",
			".weatheradd _nick_: _location_ -- Record _nick_'s location",
			".weatherrm _nick_ -- Remove _nick_'s recorded location",
			".weatherls -- List recorded locations"
		};
	}
}
