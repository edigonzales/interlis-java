import java.net.*;
public class Ipv6Check {
  public static void main(String[] a) throws Exception {
    System.out.println("getByName=" + InetAddress.getByName("[::1]"));
    var u = InetSocketAddress.createUnresolved("[::1]", 80);
    System.out.println("unresolved=" + u + " host=" + u.getHostString() + " addr=" + u.getAddress());
    System.out.println("resolved=" + new InetSocketAddress("[::1]", 80));
  }
}
