package main;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

import static panacea.Panacea.*;

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

	public static JSONObject getJSON(URLConnection c) throws IOException, JSONException {
		final Scanner s = new Scanner(c.getInputStream());
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

	public static String truncateOnWord(String line, int maxLength) {
		if(line.length() <= maxLength) {
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
			System.out.printf("%d %d\n", prev, cur);
		}
		line = line.substring(0, prev);
		if(!line.endsWith("...")) {
			line += "...";
		}
		return line;
	}
}
