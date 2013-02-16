package main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.BeanAccess;

public class YamlSerializer {

  public static final String ROOT = "store";
  private static final Yaml yaml = new Yaml();
  
  static {
    yaml.setBeanAccess(BeanAccess.FIELD);
  }
  
  public static void serialize(String store, Object data) throws IOException {
    yaml.dump(data, new FileWriter(new File(ROOT, store)));
  }
  
  public static <T> T deserialize(String store, Class<T> type) throws FileNotFoundException {
    return yaml.loadAs(new FileReader(new File(ROOT, store)), type);
  }
}
