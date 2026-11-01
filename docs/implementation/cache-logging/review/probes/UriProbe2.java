import guru.interlis.repository.RepositorySearchStep;
public class UriProbe2 {
  public static void main(String[] a){
    for(String s:new String[]{"https://example.org/a b.ili","https://example.org/ä.ili","https://example.org/%E2%80%AE.ili","https://example.org/a\nb.ili","https://example.org/\u001b[31m.ili"}){
      System.out.println(s.replace("\n","\\n").replace("\u001b","\\e")+" -> "+RepositorySearchStep.safeUri(s));
    }
  }
}
