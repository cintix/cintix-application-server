package dk.tv2.swag.web.engine;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author migo
 */
public class HTTPRequestHelper {

    /**
     *
     * @param exchange
     * @return
     */
    public static Map<String, String> requestFields(HttpExchange exchange) {
        String data = exchange.getRequestURI().getQuery();
        return fieldsToMap(data);
    }

    /**
     * 
     * @param exchange
     * @return 
     */
    public static Map<String, String> requestQueryStrings(HttpExchange exchange) {
        String data = exchange.getRequestURI().getRawQuery();
        return fieldsToMap(data);
    }

    /**
     *
     * @param from
     * @param url
     * @return
     */
    public static String[] parameterURL(String from, String url) {
        if (from.equals(url) || from.length() > url.length() || url.equals("/")) {
            return new String[0];
        }

        String parameters = url.substring(from.length());
        return parameters.split("/");
    }

    /**
     *
     * @param exchange
     * @return
     */
    public static Map<String, String> postFields(HttpExchange exchange) {
        try {
            InputStream inputStream = exchange.getRequestBody();
            byte[] buf = new byte[1024];
            int read = 0;
            String data = "";

            while (read != -1) {
                read = inputStream.read(buf);
                if (read == -1) {
                    break;
                }
                data += new String(buf, 0, read);
            }
            return fieldsToMap(data);

        } catch (IOException iOException) {
        }

        return null;
    }

    /**
     * 
     * @param data
     * @return 
     */
    private static Map<String, String> fieldsToMap(String data) {
        Map<String, String> map = new TreeMap<>();
        if (data == null) {
            return map;
        }
        if (data.contains("&")) {
            String[] split = data.split("&");
            for (String key : split) {
                String[] keyValues = key.split("=");
                if (keyValues.length > 1) {
                    try {
                        map.put(keyValues[0], URLDecoder.decode(keyValues[1], StandardCharsets.UTF_8.toString()));
                    } catch (UnsupportedEncodingException ex) {
                        Logger.getLogger(HTTPRequestHelper.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if (keyValues.length == 1) {
                    map.put(keyValues[0], null);
                }
            }
        } else {
            String[] keyValues = data.split("=");
            if (keyValues.length > 1) {
                try {
                    map.put(keyValues[0], URLDecoder.decode(keyValues[1], StandardCharsets.UTF_8.toString()));
                } catch (UnsupportedEncodingException ex) {
                    Logger.getLogger(HTTPRequestHelper.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return map;
    }
}
