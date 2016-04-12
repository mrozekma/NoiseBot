package modules;

import main.*;
import static main.Utilities.getJSON;

import org.json.JSONException;

import debugging.Log;

import java.io.IOException;
import java.util.*;
import java.io.Serializable;
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
	// https://developers.google.com/maps/documentation/geocoding/intro#GeocodingResponses
	private enum GeocodeStatusCode {
		OK("Ok"),
		ZERO_RESULTS("No location found"),
		OVER_QUERY_LIMIT("Too many geocode requests"),
		REQUEST_DENIED("Request denied"),
		INVALID_REQUEST("Invalid request"),
		UNKNOWN_ERROR("Unknown error");

		final String msg;
		GeocodeStatusCode(String msg) {
			this.msg = msg;
		}
	}

	private static class Location implements Comparable<Location>, Serializable {
		public final String city;
		public final String state;
		public final double lat, lon;

		public Location(String city, String state, double lat, double lon) {
			this.city = city;
			this.state = state;
			this.lat = lat;
			this.lon = lon;
		}

		public JSONObject pack() throws JSONException {
			return new JSONObject().put("city", this.city).put("state", this.state).put("latitude", this.lat).put("longitude", this.lon);
		}

		public static Location unpack(JSONObject data) throws JSONException {
			return new Location(data.getString("city"), data.getString("state"), data.getDouble("latitude"), data.getDouble("longitude"));
		}

		@Override public boolean equals(Object o) {
			if(!(o instanceof Location)) {
				return false;
			}
			final Location other = (Location)o;
			return this.lat == other.lat && this.lon == other.lon;
		}

		// Controls the order conditions will be displayed in
		@Override public int compareTo(Location o) {
			int rtn = Double.compare(this.lat, o.lat);
			if(rtn == 0) {
				rtn = Double.compare(this.lon, o.lon);
			}
			return rtn;
		}

		@Override public String toString() {
			return String.format("%s, %s", this.city, this.state);
		}
	}

	private static class Condition implements Comparable<Condition> {
		public final Location loc;
		public final double temp;
		public final String shortText;
		public final String text;

		public Condition(Location loc, double temp, String shortText, String text) {
			this.loc = loc;
			this.temp = temp;
			this.shortText = shortText;
			this.text = text;
		}

		public JSONObject pack() throws JSONException {
			return new JSONObject().put("location", this.loc.pack()).put("temp", this.temp).put("short_condition", this.shortText).put("condition", this.text);
		}

		public static Condition unpack(JSONObject data) throws JSONException {
			return new Condition(Location.unpack(data.getJSONObject("location")), data.getDouble("temp"), data.getString("short_condition"), data.getString("condition"));
		}

		@Override public int compareTo(Condition o) {
			return this.loc.compareTo(o.loc);
		}
	}

	private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json?key=%s&address=%s";
	private static final String FORECAST_URL = "https://api.forecast.io/forecast/%s/%f,%f";
	private static final int TIMEOUT = 5; // seconds

	@Configurable("forecast-key")
	private transient String forecastKey = null;

	@Configurable("geocode-key")
	private transient String geocodeKey = null;

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

	private Condition getWeather(Location loc) {
		try {
			final JSONObject json = getJSON(String.format(FORECAST_URL, this.forecastKey, loc.lat, loc.lon));
			final JSONObject currently = json.getJSONObject("currently");
			return new Condition(loc, currently.getDouble("temperature"), currently.getString("icon"), currently.getString("summary"));
		} catch(Exception e) {
			Log.e(e);
			return null;
		}
	}

	private Location locationLookup(String desc) throws IOException, JSONException {
		if(this.geocodeKey == null) {
			return null;
		}

		final JSONObject json = getJSON(String.format(GEOCODE_URL, this.geocodeKey, URLEncoder.encode(desc, "UTF-8")));
		GeocodeStatusCode status = GeocodeStatusCode.valueOf(json.getString("status"));
		if(status == GeocodeStatusCode.OK) {
			final JSONArray results = json.getJSONArray("results");
			if(results.length() == 0) {
				status = GeocodeStatusCode.ZERO_RESULTS;
			} else {
				final JSONObject result = results.getJSONObject(0);
				final JSONArray addressComponents = result.getJSONArray("address_components");
				Optional<String> city = Optional.empty(), state = Optional.empty(), country = Optional.empty();
				for(int i = 0; i < addressComponents.length(); i++) {
					final JSONObject component = addressComponents.getJSONObject(i);
					final JSONArray types = component.getJSONArray("types");
					if(types.contains("locality")) {
						city = Optional.of(component.getString("long_name"));
					} else if(types.contains("administrative_area_level_1")) {
						state = Optional.of(component.getString("short_name"));
					} else if(types.contains("country")) {
						country = Optional.of(component.getString("short_name"));
					}
				}
				// My Americentrism comes back to bite me
				if(!country.orElse("").equals("US")) {
					state = country;
				}
				if(!(city.isPresent() && state.isPresent())) {
					throw new JSONException("Missing city and/or state");
				}
				final JSONObject location = result.getJSONObject("geometry").getJSONObject("location");
				final double lat = location.getDouble("lat"), lon = location.getDouble("lng");
				return new Location(city.get(), state.get(), lat, lon);
			}
		}
		if(status == null) {
			status = GeocodeStatusCode.UNKNOWN_ERROR;
		}
		String msg = status.msg;
		if(json.has("error_message")) {
			msg += ": " + json.getString("error_message");
		}
		throw new IOException(msg);
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

	@Command("\\.weather(?:add|set) ([^:]+)")
	public void weatherAdd(CommandContext ctx, String loc) {
		this.weatherAdd(ctx, ctx.getMessageSender(), loc);
	}

	@Command("\\.weather(?:add|set) ([^ :]+): (.+)")
	public void weatherAdd(CommandContext ctx, String nick, String locDesc) {
		final Location loc;
		try {
			loc = this.locationLookup(locDesc);
		} catch(IOException | JSONException e) {
			Log.e(e);
			ctx.respond("#error %s", e.getMessage());
			return;
		}

		this.locations.put(nick, loc);
		this.save();
		ctx.respond("#success Added location %s for %s", loc, nick);
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
				args.add(cond.loc.city);
				args.add((int)cond.temp);
				args.add(cond.shortText.replaceAll("-day$", "").replaceAll("-night$", "").replace("partly-cloudy", "cloudy-"));
			}
			ctx.respond("#([  |  ] %(#loc)s %(#temp)s %(#text)s)", (Object)args.toArray());
		} else {
			for(Condition cond : sortedConditions) {
				args.add(cond.loc);
				args.add(cond.text);
				args.add(String.format("%.2fF", cond.temp));
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
			".weatherset _location_ -- Record the user's location",
			".weatherset _nick_: _location_ -- Record _nick_'s location",
			".weatherrm _nick_ -- Remove _nick_'s recorded location",
			".weatherls -- List recorded locations"
		};
	}
}
