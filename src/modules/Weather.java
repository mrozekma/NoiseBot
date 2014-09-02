package modules;

import static org.jibble.pircbot.Colors.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import debugging.Log;

import java.util.*;
import java.io.Serializable;
import java.net.URL;
import java.net.URLEncoder;

import main.Message;
import main.ModuleInitException;
import main.NoiseBot;
import main.NoiseModule;

import static panacea.Panacea.*;

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

	private static class Condition {
		public final Location loc;
		public final int temp;
		public final String text;

		public Condition(Location loc, int temp, String text) {
			this.loc = loc;
			this.temp = temp;
			this.text = text;
		}

		public String getString(boolean shortForm) {
			return shortForm ? this.getShortString() : this.getLongString();
		}

		public String getShortString() {
			String txt = this.text;
			for(Map.Entry<String, String> entry : shortNames.entrySet()) {
				txt = txt.replace(entry.getKey(), entry.getValue());
			}

			return COLOR_LOC + this.loc.city + " " +
			        COLOR_TEMP + this.temp + " " +
			        COLOR_TEXT + txt.toLowerCase() + COLOR_NORMAL;
		}

		public String getLongString() {
			return COLOR_INFO + "[" +
			        COLOR_LOC + this.loc +
			        COLOR_INFO + ": " +
			        COLOR_TEXT + this.text +
			        COLOR_INFO + ", " +
			        COLOR_TEMP + this.temp + "F" +
			        COLOR_INFO + "]";
		}
	};

	private static final String WEATHER_URL = "http://weather.yahooapis.com/forecastrss?w=";
	private static final String PLACE_TO_WOEID_URL = "http://where.yahooapis.com/v1/places.q('%s')?appid=%s";
	private static final String WOEID_TO_PLACE_URL = "http://where.yahooapis.com/v1/place/%d?appid=%s";
	private static final int TIMEOUT = 5; // seconds

	private static final String COLOR_INFO = PURPLE;
	private static final String COLOR_SUCCESS = GREEN;
	private static final String COLOR_LOC = CYAN;
	private static final String COLOR_TEXT = YELLOW;
	private static final String COLOR_TEMP = MAGENTA;
	private static final String COLOR_ERROR = RED + REVERSE;
	private static final String COLOR_NORMAL = NORMAL;

	private static final Map<String, String> shortNames = new HashMap<String, String>() {{
		put("Partly Cloudy", "cloudy-");
		put("Mostly Cloudy", "cloudy+");
		put("Thunderstorms", "storms");
	}};

	@Configurable("appid")
	private transient String appid = null;

	// nick -> user's location. Perfect for NSA surveillance teams
	private final Map<String, Location> locations = new HashMap<String, Location>();

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
			return Location.parse(getXML(String.format(PLACE_TO_WOEID_URL, URLEncoder.encode(desc), this.appid)));
		} catch(Exception e) {
			Log.e(e);
			return null;
		}
	}

	private Map<Location, Condition> getWeather(boolean all) {
		final List<String> nicks = Arrays.asList(this.bot.getNicks());
		final Set<Location> locations = new TreeSet<Location>();

		if(all) {
			locations.addAll(this.locations.values());
		} else {
			for(Map.Entry<String, Location> entry : this.locations.entrySet()) {
				if(nicks.contains(entry.getKey())) {
					locations.add(entry.getValue());
				}
			}
		}

		final Map<Location, Condition> rtn = new LinkedHashMap<Location, Condition>();
		for(Location location : locations) {
			rtn.put(location, this.getWeather(location));
		}
		return rtn;
	}

	@Command("\\.weatheradd ([^:]+)")
	public void weatherAdd(Message message, String loc) {
		this.weatherAdd(message, message.getSender(), loc);
	}

	@Command("\\.weatheradd ([^ :]+): (.+)")
	public void weatherAdd(Message message, String nick, String locDesc) {
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
			this.bot.sendMessage(COLOR_ERROR + "Unable to determine location");
			return;
		}

		this.locations.put(nick, loc);
		this.save();
		this.bot.sendMessage(COLOR_SUCCESS + String.format("Added location %d (%s) for %s", loc.woeid, loc, nick));
	}

	@Command("\\.weatherrm ([^ :]+)")
	public void weatherRemove(Message message, String nick) {
		if(this.locations.containsKey(nick)) {
			final Location loc = this.locations.remove(nick);
			this.save();
			this.bot.sendMessage(COLOR_SUCCESS + String.format("Removed %s (%s) from weather listings", nick, loc));
		} else {
			this.bot.sendMessage(COLOR_ERROR + "No location known for " + nick);
		}
	}

	@Command("\\.weatherls")
	public void weatherList(Message message) {
		final List<String> send = new Vector<String>(this.locations.size());
		for(Map.Entry<String, Location> entry : this.locations.entrySet()) {
			send.add(COLOR_INFO + String.format("%s - %s", entry.getKey(), entry.getValue()));
		}
		this.bot.sendMessageParts("; ", send.toArray(new String[0]));
	}

	@Command("\\.(weather|wx)(?: ([.*]))?")
	public void weather(Message message, String type, String filter) {
		final boolean shortForm = type.equals("wx");
		if(".".equals(filter)) { // Show only the sender's weather
			if(!this.locations.containsKey(message.getSender())) {
				this.bot.sendMessage(COLOR_ERROR + "Your location is unknown");
			}
			this.bot.sendMessage(this.getWeather(this.locations.get(message.getSender())).getString(shortForm));
		} else {
			final boolean includeOfflineUsers = "*".equals(filter);
			List<String> send = new Vector<String>();
			for(Map.Entry<Location, Condition> wx : this.getWeather(includeOfflineUsers).entrySet()) {
				if(wx.getValue() == null) {
					this.bot.sendMessage(COLOR_ERROR + "Problem parsing Weather data");
					return;
				}
				send.add(wx.getValue().getString(shortForm));
			}
			this.bot.sendMessageParts(shortForm ? "  |  " : " ", send.toArray(new String[0]));
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
