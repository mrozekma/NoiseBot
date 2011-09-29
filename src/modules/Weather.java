package modules;

import static org.jibble.pircbot.Colors.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.json.JSONException;
import org.json.JSONObject;

import main.Message;
import main.NoiseModule;

import static panacea.Panacea.*;

/**
 * Weather
 * Based off the SO module
 *
 * @author Michael Auchter
 *         Created Sep 23, 2011.
 */
public class Weather extends NoiseModule
{
	private static final String WEATHER_URL = "http://weather.yahooapis.com/forecastjson?w=";
	private static final String COLOR_INFO = PURPLE;
	private static final String COLOR_LOC = CYAN;
	private static final String COLOR_TEXT = YELLOW;
	private static final String COLOR_TEMP = MAGENTA;
	private static final String COLOR_ERROR = RED + REVERSE;

	private static final String[][] cities = {
		{ "12778384", "Terre Haute, IN"},
		{ "2357536",  "Austin, TX"},
		{ "2374418",  "Canoga Park, CA"},
		{ "2401279",  "Fairbanks, AK"},
		{ "2402488",  "Farmington Hills, MI"},
		{ "2470874",  "Petaluma, CA"},
		{ "2490383",  "Seattle, WA"},
		{ "2517274",  "West Lafayette, IN"},
	};

	private static final String[][] icons = {
		/* You may need to install "Symbolata" to see the first two symbols */
		{"Partly Cloudy", "\u00e2\u009b\u0085"}, /* SUN BEHIND CLOUD */
		{"Thunderstorms", "\u00e2\u009b\u0088"}, /* THUNDER CLOUD AND RAIN */
		/* The rest are in DejaVu Sans */
		{"Cloudy", "\u00e2\u0098\u0081"}, /* CLOUD */
		{"Rain", /*"\u00e2\u0098\u0094"*/ "\u00e2\u009b\u0086"}, /* UMBRELLA WITH RAIN DROPS */
		{"Snow", "\u00e2\u0098\u0083"}, /* SNOWMAN */
		{"Sunny", "\u00e2\u0098\u0080"}, /* BLACK SUN WITH RAYS */
		{"Hail", "\u00e2\u0098\u0084"}, /* COMET */
	};

	private static JSONObject getJSON(String url) throws IOException, JSONException {
		final URLConnection c = new URL(url).openConnection();
		final DataInputStream s = new DataInputStream(c.getInputStream());
		final StringBuffer b = new StringBuffer();
		
		byte[] buffer = new byte[1024];
		int size;
		while((size = s.read(buffer)) >= 0)
			b.append(new String(buffer, 0, size));

		return new JSONObject(b.toString());
	}

	public String getWeather(String woe, String location) throws Exception
	{
		final JSONObject j = getJSON(WEATHER_URL + woe);
		final double temperature = j.getJSONObject("condition").getDouble("temperature");
		String text  = j.getJSONObject("condition").getString("text");

		for (int i = 0; i < icons.length; i++)
			text = text.replace(icons[i][0], icons[i][1]);

		return	COLOR_INFO + "[" +
			COLOR_LOC  + location +
			COLOR_INFO + ": " +
			COLOR_TEXT + text +
			COLOR_INFO + ", " +
			COLOR_TEMP + temperature + "F" +
			COLOR_INFO + "] ";
	}

	@Command(".weather")
	public void weather(Message message)
	{
		try {
			String s = "";
			for (int i = 0; i < cities.length; i++)
				s += getWeather(cities[i][0], cities[i][1]);
			this.bot.sendMessage(s);
		} catch (Exception e) {
			this.bot.sendMessage(COLOR_ERROR + "Problem parsing Weather data");
		}
	}

	@Override public String getFriendlyName() { return "Weather"; }
	@Override public String getDescription() { return "Outputs the current weather conditions in cities occupied by #rhnoise"; }
	@Override public String[] getExamples() { return new String[] { ".weather" }; }
}
