package main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;

import debugging.Log;

public class Serializer {
	public static final String ROOT = "store";
	private static final Gson gson = new Gson();
  
	public static void serialize(String store, Object data) throws IOException {
		final File file = new File(ROOT, store);
		final FileWriter w = new FileWriter(file);
		Log.d(String.format("Writing %s to %s", data, file.getAbsolutePath()));
		gson.toJson(data, w);
		w.close();
	}
  
	public static <T> T deserialize(String store, Class<T> type) throws FileNotFoundException {
		final File file = new File(ROOT, store);
		Log.d(String.format("Reading from %s", file.getAbsolutePath()));
		return gson.fromJson(new FileReader(file), type);
	}
}
