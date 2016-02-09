package main;

import org.json.JSONException;

import java.lang.reflect.Array;
import java.util.*;

/**
 * @author Michael Mrozek
 *         Created Jan 2, 2016.
 */
public class JSONObject extends org.json.JSONObject {
	public JSONObject() {}

	public JSONObject(String source) throws JSONException {
		super(source);
	}

	public JSONObject(org.json.JSONObject wrap) throws JSONException {
		super(wrap, getKeys(wrap.keys()));
	}

	private static String[] getKeys(Iterator iter) {
		final List<String> rtn = new LinkedList<>();
		iter.forEachRemaining(o -> rtn.add(o.toString()));
		return rtn.toArray(new String[0]);
	}

	public <T> T getT(String key) throws JSONException {
		return (T)this.get(key);
	}

	public <T> T[] getJavaArray(String key, Class<T> cls) throws JSONException {
		final JSONArray arr = this.getJSONArray(key);
		final T[] rtn = (T[])Array.newInstance(cls, arr.length());
		for(int i = 0; i < rtn.length; i++) {
			rtn[i] = (T)arr.get(i);
		}
		return rtn;
	}

	public JSONObject put(String key, Object[] arr) throws JSONException {
		this.put(key, Arrays.asList(arr));
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

	public JSONArray getJSONArray(String key) throws JSONException {
		return new JSONArray(super.getJSONArray(key));
	}

	public JSONObject getJSONObject(String key) throws JSONException {
		return new JSONObject(super.getJSONObject(key));
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

	public <T> Optional<T> getOpt(String key) throws JSONException {
		return this.has(key) ? Optional.of((T)this.get(key)) : Optional.empty();
	}

	public <T> JSONObject putOpt(String key, Optional<T> value) throws JSONException {
		if(value.isPresent()) {
			this.put(key, value.get());
		}
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
