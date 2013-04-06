package main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.google.gson.Gson;

public class Serializer {
	public static final String ROOT = "store";
	private static final Gson gson = new Gson();
  
	public static void serialize(String store, Object data) throws IOException {
		gson.toJson(data, new FileWriter(new File(ROOT, store)));
	}
  
	public static <T> T deserialize(String store, Class<T> type) throws FileNotFoundException {
		return gson.fromJson(new FileReader(new File(ROOT, store)), type);
	}
}
