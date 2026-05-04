
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 *
 * @author cintix
 */
public class Test {
      public static boolean contentTypeMatch(String accept, String contentType) {
        String patternString = "^" + accept.replaceAll("\\*", "\\\\S+").replaceAll("/", "\\\\/");
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(contentType);
        return matcher.find();
    }
}
