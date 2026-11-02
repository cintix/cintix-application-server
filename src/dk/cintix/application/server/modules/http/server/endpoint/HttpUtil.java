package dk.cintix.application.server.modules.http.server.endpoint;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author cix
 */
public class HttpUtil {

    private static final Logger logger = Logger.getLogger(HttpUtil.class.getName());

    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

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
        parsePostFields(linesProcessed, requestLines, null, postFields, null);
    }

    /**
     * Splits the body out of {@code requestLines} into form fields.
     *
     * <p>Fields are only mined when the body is a form body — that is, when
     * {@code Content-Type} is {@code application/x-www-form-urlencoded}, or
     * absent (older hand-written clients send no type). For any other content
     * type the field map stays empty apart from {@code !RAW}: a JSON body such
     * as {@code {"a":"x=1"}} must not turn into a bogus field named {@code a}.
     * Callers read such bodies through {@code getRawPost()}.</p>
     *
     * <p>Every value is also appended to {@code postValues}, so a repeated field
     * ({@code tag=a&tag=b}) no longer collapses to its last value. The
     * single-value map keeps its last-wins behaviour for existing callers.</p>
     *
     * <p>{@code !RAW} is the untouched body and is deliberately <b>not</b>
     * URL-decoded, so a JSON payload containing {@code %} or {@code +}
     * survives intact. Form fields are decoded.</p>
     */
    public static void parsePostFields(int linesProcessed, String[] requestLines, String contentType,
            final Map<String, String> postFields, final Map<String, List<String>> postValues) {
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

        if (!isFormContentType(contentType)) {
            return;
        }

        // A form body may span several lines, so every body line contributes
        // fields — not just the first one.
        String[] postParams = body.split("[&\\r\\n]+");
        for (int index = 0; index < postParams.length; index++) {
            if (postParams[index].contains("=")) {
                String[] keyValue = postParams[index].split("=", 2);
                String value = (keyValue.length > 1 && keyValue[1] != null) ? keyValue[1].trim() : "";
                String key = urlDecode(keyValue[0].trim());
                String decoded = urlDecode(value);
                postFields.put(key, decoded);
                addValue(postValues, key, decoded);
            }
        }
    }

    /**
     * @return {@code true} when the body should be parsed as a form. A missing
     *         content type is treated as a form for backwards compatibility;
     *         every other type (JSON, multipart, text/plain, …) is not.
     */
    public static boolean isFormContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return true;
        }
        String type = contentType.toLowerCase(Locale.ROOT).trim();
        int parameters = type.indexOf(';');
        if (parameters != -1) {
            type = type.substring(0, parameters).trim();
        }
        return FORM_CONTENT_TYPE.equals(type);
    }

    private static void addValue(final Map<String, List<String>> values, String key, String value) {
        if (values == null) {
            return;
        }
        List<String> existing = values.get(key);
        if (existing == null) {
            existing = new ArrayList<>();
            values.put(key, existing);
        }
        existing.add(value);
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
            // Keep the raw value so clients that send a bare '%' keep working,
            // but leave a trace: a malformed escape is otherwise invisible.
            logger.log(Level.FINE, "Malformed percent-encoding left unchanged: {0} ({1})",
                    new Object[]{value, e.getMessage()});
            return value;
        }
    }

    /**
     * Reads one header value out of a raw header block.
     *
     * <p>Matched case-insensitively because the header name on the wire is
     * case-insensitive. Only the first occurrence is returned — a duplicated
     * header is a framing error the accumulator does not attempt to reconcile,
     * so the first value is as good as any and keeps the behaviour predictable.
     * A folded (multi-line) value is not unfolded; callers only need a single
     * line here.</p>
     *
     * @param headerBlock the raw request headers, terminator included
     * @param name        the header name, case-insensitive
     * @return the trimmed value, or {@code null} when the header is absent or
     *         has no name/value split
     */
    public static String parseHeaderValue(String headerBlock, String name) {
        if (headerBlock == null || name == null) {
            return null;
        }
        String[] lines = headerBlock.split("\n");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if (!line.substring(0, colon).trim().equalsIgnoreCase(name)) {
                continue;
            }
            return line.substring(colon + 1).trim();
        }
        return null;
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
        String value = parseHeaderValue(headerBlock, "Content-Length");
        if (value == null) {
            return -1;
        }
        try {
            long length = Long.parseLong(value);
            return length < 0 ? -1 : length;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String parseQueryStrings(String contextPath, final Map<String, String> queryStrings) {
        return parseQueryStrings(contextPath, queryStrings, null);
    }

    /**
     * Parses the query string, filling both the last-wins map used by existing
     * callers and, when supplied, a multi-value map that keeps every value of a
     * repeated key ({@code ?tag=a&tag=b}).
     */
    public static String parseQueryStrings(String contextPath, final Map<String, String> queryStrings,
            final Map<String, List<String>> queryStringValues) {
        if (contextPath.contains("?")) {
            int offset = contextPath.indexOf("?");
            String queryStringLine = contextPath.substring(offset + 1);
            String[] queryStrins = queryStringLine.split("&");
            for (int index = 0; index < queryStrins.length; index++) {
                if (queryStrins[index].contains("=")) {
                    String[] keyValue = queryStrins[index].split("=", 2);
                    String value = (keyValue.length > 1 && keyValue[1] != null) ? keyValue[1].trim() : "";
                    String key = urlDecode(keyValue[0].trim());
                    String decoded = urlDecode(value);
                    queryStrings.put(key, decoded);
                    addValue(queryStringValues, key, decoded);
                } else {
                    // Valueless keys are inserted with an empty value on purpose:
                    // the "?jsd" documentation switch is a containsKey lookup.
                    String key = urlDecode(queryStrins[index].trim());
                    queryStrings.put(key, "");
                    addValue(queryStringValues, key, "");
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
