import guru.interlis.repository.RepositorySearchStep;
public class UriProbe {
  public static void main(String[] a){
    for(String s:new String[]{"http://[::1]:8080/x?q#f","http://user:pass@[2001:db8::1]/p?q#f","http://127.0.0.1:1/p?q#f","https://example.org/p","file:///tmp/a.ili"}){
      System.out.println(s+" -> "+RepositorySearchStep.safeUri(s));
    }
  }
}
