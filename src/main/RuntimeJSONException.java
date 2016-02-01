package main;

import org.json.JSONException;

/**
 * This class exists to soften JSONException for use in functional interfaces
 * @author Michael Mrozek
 *         Created Jan 27, 2016.
 */
public class RuntimeJSONException extends RuntimeException {
	@FunctionalInterface
	public interface ThrowingSupplier {
		JSONObject apply() throws JSONException;
	}

	private final JSONException jsonException;

	public RuntimeJSONException(JSONException e) {
		super(e);
		this.jsonException = e;
	}

	public JSONException getException() {
		return this.jsonException;
	}

	public static JSONObject wrap(ThrowingSupplier fn) throws RuntimeJSONException {
		try {
			return fn.apply();
		} catch(JSONException e) {
			throw new RuntimeJSONException(e);
		}
	}
}
