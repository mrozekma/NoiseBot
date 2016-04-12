package main;

import org.json.JSONException;

import java.util.Arrays;
import java.util.Collection;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Michael Mrozek
 *         Created Feb 9, 2016.
 */
public class JSONArray extends org.json.JSONArray {
	private static class JSONArraySpliterator<T> implements Spliterator<T> {
		private final JSONArray arr;
		private int pos, len;

		JSONArraySpliterator(JSONArray arr) {
			this(arr, 0, arr.length());
		}

		JSONArraySpliterator(JSONArray arr, int pos, int len) {
			this.arr = arr;
			this.pos = pos;
			this.len = len;

			if(this.pos < 0 || this.pos > len || this.len > arr.length()) {
				throw new IllegalArgumentException("Bad pos/len");
			}
		}

		@Override public boolean tryAdvance(Consumer<? super T> consumer) {
			if(this.pos == this.arr.length()) {
				return false;
			}
			try {
				consumer.accept((T)this.arr.get(this.pos++));
				return true;
			} catch(JSONException e) {
				throw new RuntimeException(e);
			}
		}

		@Override public Spliterator<T> trySplit() {
			final int newPos = this.pos + (this.len - this.pos) / 2;
			if(newPos == this.pos) {
				return null;
			}
			try {
				return new JSONArraySpliterator(this.arr, newPos, this.len);
			} finally {
				this.len = newPos;
			}
		}

		@Override public long estimateSize() {
			return this.len - this.pos;
		}

		@Override public int characteristics() {
			return ORDERED | SIZED | SUBSIZED;
		}
	}

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

	public boolean contains(Object seek) {
		return this.stream().anyMatch(seek::equals);
	}

	public <T> Stream<T> stream() {
		return StreamSupport.stream(new JSONArraySpliterator<>(this), false);
	}

	public <T> Stream<T> parallelStream() {
		return StreamSupport.stream(new JSONArraySpliterator<>(this), true);
	}

	// This assumes that this array contains JSONObjects, and returns a stream of Object[]s.
	// Each Object[] corresponds to one entry in this JSONArray, and the elements of the Object[] correspond to the values of the keys provided by 'keys'
	public Stream<Object[]> valueStream(final String... keys) {
		return this.<JSONObject>stream().map(obj -> Arrays.stream(keys).map(key -> obj.getSoft(key)).toArray());
	}

	// This flattens the result of valueStream() so you just get a single Object[] with all the values from all the entries.
	// Intended for use with multipart messages
	public Object[] flatValueStream(final String... keys) {
		return this.valueStream(keys).flatMap(Arrays::stream).toArray();
	}
}
