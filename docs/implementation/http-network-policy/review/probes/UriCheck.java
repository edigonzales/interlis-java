import java.net.*;
public class UriCheck {
  public static void main(String[] a) {
    URI base=URI.create("http://host/a");
    for (String loc : new String[]{"http://[", "http://a b", "%%%", "//[", "http://host/%zz", "http://host/ok"}) {
      try { System.out.println(loc + " -> " + base.resolve(loc)); }
      catch (Exception e) { System.out.println(loc + " -> " + e.getClass().getName() + ": " + e.getMessage()); }
    }
  }
}
