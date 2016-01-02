package main;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Map;

/**
 * @author Michael Mrozek
 *         Created Jan 2, 2016.
 */
public class JSONObject extends org.json.JSONObject {
	public JSONObject put(String key, Object[] arr) throws JSONException {
		for(Object i : arr) {
			this.append(key, i);
		}
		return this;
	}

	public boolean[] getBooleanArray(String key) throws JSONException {
		final JSONArray arr = this.getJSONArray(key);
		final boolean[] rtn = new boolean[arr.length()];
		for(int i = 0; i < rtn.length; i++) {
			rtn[i] = arr.getBoolean(i);
		}
		return rtn;
	}

	public double[] getDoubleArray(String key) throws JSONException {
		final JSONArray arr = this.getJSONArray(key);
		final double[] rtn = new double[arr.length()];
		for(int i = 0; i < rtn.length; i++) {
			rtn[i] = arr.getDouble(i);
		}
		return rtn;
	}

	public int[] getIntArray(String key) throws JSONException {
		final JSONArray arr = this.getJSONArray(key);
		final int[] rtn = new int[arr.length()];
		for(int i = 0; i < rtn.length; i++) {
			rtn[i] = arr.getInt(i);
		}
		return rtn;
	}

	public long[] getLongArray(String key) throws JSONException {
		final JSONArray arr = this.getJSONArray(key);
		final long[] rtn = new long[arr.length()];
		for(int i = 0; i < rtn.length; i++) {
			rtn[i] = arr.getLong(i);
		}
		return rtn;
	}

	public String[] getStringArray(String key) throws JSONException {
		final JSONArray arr = this.getJSONArray(key);
		final String[] rtn = new String[arr.length()];
		for(int i = 0; i < rtn.length; i++) {
			rtn[i] = arr.getString(i);
		}
		return rtn;
	}

	// Every superclass method that returns this:

	public JSONObject accumulate(String key, Object value) throws JSONException {
		super.accumulate(key, value);
		return this;
	}

	public JSONObject append(String key, Object value) throws JSONException {
		super.append(key, value);
		return this;
	}

	public JSONObject put(String key, boolean value) throws JSONException {
		super.put(key, value);
		return this;
	}

	public JSONObject put(String key, java.util.Collection value) throws JSONException {
		super.put(key, value);
		return this;
	}

	public JSONObject put(String key, double value) throws JSONException {
		super.put(key, value);
		return this;
	}

	public JSONObject put(String key, int value) throws JSONException {
		super.put(key, value);
		return this;
	}

	public JSONObject put(String key, long value) throws JSONException {
		super.put(key, value);
		return this;
	}

	public JSONObject put(String key, Map value) throws JSONException {
		super.put(key, value);
		return this;
	}

	public JSONObject put(String key, Object value) throws JSONException {
		super.put(key, value);
		return this;
	}

	public JSONObject putOnce(String key, Object value) throws JSONException {
		super.putOnce(key, value);
		return this;
	}

	public JSONObject putOpt(String key, Object value) throws JSONException {
		super.putOpt(key, value);
		return this;
	}
}
