package dk.cintix.application.server.modules.http.server.endpoint;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author cix
 */
public class HttpUtil {

    /**
     * Splits the body out of {@code requestLines} into {@code postFields}.
     *
     * <p>The body starts at {@code linesProcessed} and runs to the end of the
     * array. {@code requestLines} comes from {@code String.split("\n")}, so
     * every line except the last still carries its trailing {@code \r};
     * joining with {@code \n} therefore reproduces the body byte for byte.</p>
     *
     * <p>{@code !RAW} is the untouched body and is deliberately <b>not</b>
     * URL-decoded, so a JSON payload containing {@code %} or {@code +}
     * survives intact. Form fields are decoded.</p>
     */
    public static void parsePostFields(int linesProcessed, String[] requestLines, final Map<String, String> postFields) {
        if (linesProcessed >= requestLines.length) {
            return;
        }

        StringBuilder rawRequest = new StringBuilder();
        for (int index = linesProcessed; index < requestLines.length; index++) {
            if (index > linesProcessed) {
                rawRequest.append('\n');
            }
            rawRequest.append(requestLines[index]);
        }
        String body = rawRequest.toString();

        postFields.put("!RAW", body);

        // A form body may span several lines, so every body line contributes
        // fields — not just the first one.
        String[] postParams = body.split("[&\\r\\n]+");
        for (int index = 0; index < postParams.length; index++) {
            if (postParams[index].contains("=")) {
                String[] keyValue = postParams[index].split("=", 2);
                String value = (keyValue.length > 1 && keyValue[1] != null) ? keyValue[1].trim() : "";
                postFields.put(urlDecode(keyValue[0].trim()), urlDecode(value));
            }
        }
    }

    /**
     * Percent-decodes a query-string or form value as UTF-8, treating
     * {@code +} as a space.
     *
     * <p>A value that is not validly encoded (a bare {@code %}, or {@code %ZZ})
     * is returned unchanged rather than rejected, so clients that send such
     * values keep getting exactly the response they got before.</p>
     */
    public static String urlDecode(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.indexOf('%') == -1 && value.indexOf('+') == -1) {
            return value;
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return value;
        }
    }

    /**
     * Reads {@code Content-Length} out of a raw header block.
     *
     * <p>Matched case-insensitively because the header name on the wire is
     * case-insensitive; the value is not trimmed of anything but surrounding
     * whitespace, so a non-numeric value is reported as absent rather than
     * guessed at.</p>
     *
     * @param headerBlock the raw request headers, terminator included
     * @return the declared body length, or {@code -1} when the header is
     *         missing, unparseable, or negative
     */
    public static long parseContentLength(String headerBlock) {
        if (headerBlock == null) {
            return -1;
        }
        String[] lines = headerBlock.split("\n");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if (!line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                continue;
            }
            try {
                long length = Long.parseLong(line.substring(colon + 1).trim());
                return length < 0 ? -1 : length;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
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
                    queryStrings.put(urlDecode(keyValue[0].trim()), urlDecode(value));
                } else {
                    // Valueless keys are inserted with an empty value on purpose:
                    // the "?jsd" documentation switch is a containsKey lookup.
                    queryStrings.put(urlDecode(queryStrins[index].trim()), "");
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
