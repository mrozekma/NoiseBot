package main;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.json.JSONException;

/**
 * Utilities
 *
 * @author Michael Mrozek
 *         Created Jun 16, 2009.
 */
public class Utilities {
	public static String urlEncode(String text) {
		try {
			return URLEncoder.encode(text, "UTF-8");
		} catch(UnsupportedEncodingException e) {
			throw new RuntimeException("UTF-8 unsupported");
		}
	}

	public static String urlDecode(String text) {
		try {
			return URLDecoder.decode(text, "UTF-8");
		} catch(UnsupportedEncodingException e) {
			throw new RuntimeException("UTF-8 unsupported");
		}
	}

	public static JSONObject getJSON(String url) throws IOException, JSONException {
		return getJSON(new URL(url).openConnection());
	}

	public static JSONObject getJSON(String url, boolean allowErrors) throws IOException, JSONException {
		return getJSON(new URL(url).openConnection(), allowErrors);
	}

	public static JSONObject getJSON(URLConnection c) throws IOException, JSONException {
		return getJSON(c, false);
	}

	public static JSONObject getJSON(URLConnection c, boolean allowErrors) throws IOException, JSONException {
		// http://stackoverflow.com/a/9129991/309308
		InputStream is = null;
		try {
			is = c.getInputStream();
		} catch(IOException e) {
			if(c instanceof HttpURLConnection) {
				HttpURLConnection httpConn = (HttpURLConnection)c;
				if(httpConn.getResponseCode() == 200) {
					throw e;
				}
				is = httpConn.getErrorStream();
			}
		}

		final Scanner s = new Scanner(is);
		final StringBuffer buffer = new StringBuffer();
		while(s.hasNextLine()) {
			buffer.append(s.nextLine());
		}
		return new JSONObject(buffer.toString());
	}

	public static String formatSeconds(long seconds) {
		if(seconds < 60) {
			return pluralize((int)seconds, "second", "seconds");
		} else if(seconds < 60 * 60) {
			return String.format("%d:%02d", seconds / 60, seconds % 60);
		} else {
			return String.format("%d:%02d:%02d", seconds / (60 * 60), (seconds / 60) % 60, seconds % 60);
		}
	}

	public static double round(double number, int places) {
		return places > 0 ? (Math.round(number * Math.pow(10, places)) / Math.pow(10, places)) : number;
	}

	public static String bytesToFriendly(double bytes, int places) {
		for(String size : new String[] {"bytes", "KB", "MB", "GB"}) {
			if(bytes < 1024) {
				return round(bytes, places) + " " + size;
			}
			bytes /= 1024;
		}
		return round(bytes, places) + " PB";
	}

	// Return the size of the UTF-8 encoded string in bytes
	public static int utf8Size(String s)
	{
		try {
			return s.getBytes("UTF8").length;
		} catch (UnsupportedEncodingException e) {
			return s.length();
		}
	}

	public static String truncateOnWord(String line, int maxLength) {
		if(utf8Size(line) <= maxLength) {
			return line;
		} else if(maxLength < 3) {
			return "";
		}

		maxLength -= 3; // Ellipses
		int prev = -1, cur = 0;
		while((cur = line.indexOf(' ', prev + 1)) >= 0) {
			if(cur > maxLength) {
				break;
			}
			prev = cur;
		}
		line = line.substring(0, prev);
		if(!line.endsWith("...")) {
			line += "...";
		}
		return line;
	}

	public static String pluralize(long number, String singular, String plural) {
		return pluralize(number, singular, plural, true);
	}

	public static String pluralize(long number, String singular, String plural, boolean includeNumber) {
		return (includeNumber ? number + " " : "") + (number == 1 ? singular : plural);
	}

	// min and max are both inclusive
	public static int getRandomInt(int min, int max) {
		return ((int)(Math.random() * (max - min + 1))) + min;
	}

	public static <T> T getRandom(T[] arr) {
		return arr.length > 0 ? arr[getRandomInt(0, arr.length - 1)] : null;
	}

	public static String[] getMatches(String[] arr, String key) {
		Vector<String> matches = new Vector<String>();
		Pattern pattern = Pattern.compile(key);
		for(int i = 0; i < arr.length; i++) {
			if(pattern.matcher(arr[i]).matches()) {
				matches.add(arr[i]);
			}
		}
		return matches.toArray(new String[0]);
	}

	public static String getRandomMatch(String[] arr, String key) {return getRandom(getMatches(arr, key));}

	public static int range(int value, int min, int max) {
		return Math.min(max, Math.max(min, value));
	}

	public static void sleep(double seconds) {
		try {
			Thread.sleep((long)(seconds * 1000));
		} catch(InterruptedException e) {
		}
	}

	public static String substring(String s, int start) {
		return start < 0 ? substring(s, s.length() + start) : substring(s, start, s.length() - start);
	}

	public static String substring(String s, int start, int length) {
		if(length + start > s.length()) {
			throw new IndexOutOfBoundsException("Length extends past the end of the string");
		}
		if(start < 0) {
			return substring(s, s.length() + start,length);
		}
		if(length <= start - s.length()) {
			throw new IndexOutOfBoundsException("Negative length extends past the beginning of the string");
		}
		if(length < 0) {
			return substring(s, start, s.length() + length - start);
		}
		return s.substring(start, start + length);
	}

	public static <T> T[] reverse(T[] array) {
		List<T> list = Arrays.asList(array);
		Collections.reverse(list);
		return list.toArray(array);
	}

	public static Optional<Integer> strstr(String haystack, String needle, int start) {
		final int off = haystack.indexOf(needle, start);
		return (off == -1) ? Optional.empty() : Optional.of(off);
	}

	// Parse an RFC822-esque date/time and return a string indicating, fuzzily, how long ago that was
	public static String fuzzyTimeAgo(String rfc822date)
	{
		final DateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss ZZZZZ");
		long ms = 0;
		try {
			ms = new Date().getTime() - dateFormat.parse(rfc822date).getTime();
		} catch (ParseException pe) {
			return "";
		}

		if (ms < 0)
			return "(sometime in the future...)";
		if (ms < 1000)
			return "(now)";

		StringBuilder s = new StringBuilder("");

		class FuzzyTime {
			FuzzyTime(StringBuilder in, long milliseconds)
			{
				this.in = in;
				this.milliseconds = milliseconds;
			}

			StringBuilder in;
			final long milliseconds;

			public void xlate(TimeUnit unit, String unitStr) {
				if (!in.toString().equals(""))
					return;

				final long duration = unit.convert(milliseconds, TimeUnit.MILLISECONDS);
				if (duration > 1)
					unitStr += "s";

				if (duration > 0)
					in.append("(").append(duration).append(" ").append(unitStr).append(" ago)");
			}
		};

		FuzzyTime f = new FuzzyTime(s, ms);
		f.xlate(TimeUnit.DAYS,    "day");
		f.xlate(TimeUnit.HOURS,   "hour");
		f.xlate(TimeUnit.MINUTES, "minute");
		f.xlate(TimeUnit.SECONDS, "second");

		return s.toString();
	}

	public static String exceptionString(Throwable e) {
		final StringBuilder rtn = new StringBuilder();
		rtn.append(e.getClass().getSimpleName());
		if(e.getMessage() != null) {
			rtn.append(": ").append(e.getMessage());
		}
		return rtn.toString();
	}
}
