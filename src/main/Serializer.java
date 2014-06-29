package main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;

import debugging.Log;

public class Serializer {
	private static final Gson gson = new Gson();

	public static void serialize(String file, Object data) throws IOException {
		serialize(new File(file), data);
	}

	public static void serialize(File file, Object data) throws IOException {
		file.getParentFile().mkdirs();
		final FileWriter w = new FileWriter(file);
		Log.d(String.format("Writing %s to %s", data, file.getAbsolutePath()));
		gson.toJson(data, w);
		w.close();
	}

	public static <T> T deserialize(String file, Class<T> type) throws FileNotFoundException {
		return deserialize(new File(file), type);
	}

	public static <T> T deserialize(File file, Class<T> type) throws FileNotFoundException {
		Log.d(String.format("Reading from %s", file.getAbsolutePath()));
		return gson.fromJson(new FileReader(file), type);
	}
}
