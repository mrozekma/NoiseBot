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
		final URLConnection c = new URL(url).openConnection();
		final Scanner s = new Scanner(c.getInputStream());
		final StringBuffer buffer = new StringBuffer();
		while(s.hasNextLine()) {
			buffer.append(s.nextLine());
		}
		return new JSONObject(buffer.toString());
	}
}
