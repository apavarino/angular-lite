import java.nio.file.Path;
import org.jetbrains.plugins.textmate.bundles.*;
import org.jetbrains.plugins.textmate.plist.*;
public class GrammarCheck {
 public static void main(String[] args) {
  var reader=VSCBundleReaderKt.readVSCBundle(new JsonPlistReader(),new TextMateNioResourceReader(Path.of(args[0])));
  var iter=reader.readGrammars().iterator();
  while(iter.hasNext()) {var g=iter.next(); g.getPlist().getValue(); System.out.println(g.getOverrideScopeName()+" "+g.getFileNameMatchers());}
 }
}
