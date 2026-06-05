package dk.cintix.application.server.modules.http.server.endpoint;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author cix
 */
public class HttpUtil {

    public static void parsePostFields(int linesProcessed, String[] requestLines, final Map<String, String> postFields) {
        if (linesProcessed < (requestLines.length)) {
            StringBuilder rawRequest = new StringBuilder();
            for (int index = linesProcessed; index < requestLines.length - 1; index++) {
                rawRequest.append(requestLines[index]);
            }

            postFields.put("!RAW", rawRequest.toString());
            String[] postParams = requestLines[linesProcessed++].split("&");
            for (int index = 0; index < postParams.length; index++) {
                if (postParams[index].contains("=")) {
                    String[] keyValue = postParams[index].split("=", 2);
                    String value = (keyValue.length > 1 && keyValue[1] != null) ? keyValue[1].trim() : "";
                    postFields.put(keyValue[0], value);
                }
            }
        }
    }

    public static String parseQueryStrings(String contextPath, final Map<String, String> queryStrings) {
        if (contextPath.contains("?")) {
            int offset = contextPath.indexOf("?");
            String queryStringLine = contextPath.substring(offset + 1);
            String[] queryStrins = queryStringLine.split("&");
            for (int index = 0; index < queryStrins.length; index++) {
                if (queryStrins[index].contains("=")) {
                    String[] keyValue = queryStrins[index].split("=", 2);
                    String value = (keyValue.length > 1 && keyValue[1] != null) ? keyValue[1].trim() : "";
                    queryStrings.put(keyValue[0], value);
                } else {
                    queryStrings.put(queryStrins[index], "");
                }
            }
            return contextPath.substring(0, offset);
        }
        return contextPath;
    }

    public static int parseHeaderKeys(String[] requestLines, final Map<String, String> headers, int linesProcessed) {
        for (int index = 1; index < requestLines.length; index++) {
            if (requestLines[index] == null
                    || requestLines[index].isEmpty()
                    || requestLines[index].charAt(0) == 10
                    || requestLines[index].charAt(0) == 13) {
                linesProcessed++;
                break;
            }
            String[] keyValue = requestLines[index].split(":", 2);
            String value = (keyValue.length > 1 && keyValue[1] != null) ? keyValue[1].trim() : "";
            headers.put(keyValue[0].toUpperCase().trim(), value);
            linesProcessed++;
        }
        linesProcessed++;
        return linesProcessed;
    }

    public static String buildContextPath(String[] oldPath) {
        if (oldPath == null || oldPath.length == 0) {
            return "";
        }

        StringBuilder path = new StringBuilder();
        for (int index = 0; index < oldPath.length - 1; index++) {
            path.append(oldPath[index]).append("/");
        }

        if (path.length() > 0) {
            path.setLength(path.length() - 1);
        }
        return path.toString();
    }

    public static boolean contentTypeMatch(String accept, String contentType) {
        String patternString = "^" + accept.replaceAll("\\*", "\\\\S+").replaceAll("/", "\\\\/");
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(contentType);
        return matcher.find();
    }

    public static String complieRegexFromPath(String path) {
        String patternString = "(\\{\\w+\\})";
        String realPattern = path.replaceAll("/", "\\\\/");
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(path);
        while (matcher.find()) {
            realPattern = realPattern.replaceAll(Pattern.quote(matcher.group(0)), "([^/]+)");
        }
        return "^(" + realPattern + ")$";
    }

}
