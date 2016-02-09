package main;

import org.json.JSONException;

import java.util.Collection;

/**
 * @author Michael Mrozek
 *         Created Feb 9, 2016.
 */
public class JSONArray extends org.json.JSONArray {
	public JSONArray(String source) throws JSONException {
		super(source);
	}

	public JSONArray(Collection collection) throws JSONException {
		super(collection);
	}

	public JSONArray(Object array) throws JSONException {
		super(array);
	}

	public JSONArray(org.json.JSONArray wrap) throws JSONException {
		for(int i = 0; i < wrap.length(); i++) {
			this.put(wrap.get(i));
		}
	}

	public JSONObject toJSONObject(JSONArray names) throws JSONException {
		return new JSONObject(super.toJSONObject(names));
	}

	public JSONArray optJSONArray(int index) {
		try {
			return new JSONArray(super.optJSONArray(index));
		} catch(JSONException e) {
			return null;
		}
	}

	public JSONObject optJSONObject(int index) {
		try {
			return new JSONObject(super.optJSONObject(index));
		} catch(JSONException e) {
			return null;
		}
	}

	public JSONObject getJSONObject(int index) throws JSONException {
		return new JSONObject(super.getJSONObject(index));
	}

	public JSONArray getJSONArray(int index) throws JSONException {
		return new JSONArray(super.getJSONArray(index));
	}
}
