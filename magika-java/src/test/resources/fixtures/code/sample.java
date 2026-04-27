package fixtures;

import java.util.ArrayList;
import java.util.List;

public final class Greeter {

  private final String prefix;

  public Greeter(String prefix) {
    this.prefix = prefix;
  }

  public List<String> greetAll(List<String> names) {
    List<String> out = new ArrayList<>(names.size());
    for (String n : names) {
      out.add(prefix + ", " + n + "!");
    }
    return out;
  }

  public static void main(String[] args) {
    Greeter g = new Greeter("Hello");
    for (String s : g.greetAll(List.of("world", "java", "magika"))) {
      System.out.println(s);
    }
  }
}
