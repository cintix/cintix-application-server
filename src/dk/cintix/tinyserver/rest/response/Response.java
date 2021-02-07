/*
 */
package dk.cintix.tinyserver.rest.response;

import com.google.gson.Gson;
import dk.cintix.tinyserver.io.ByteMemoryStream;
import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author cix
 */
public class Response {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
    private final Gson gson = new Gson();

    private int status = 200;
    private Map<String, String> header = new LinkedHashMap<>();
    private byte[] content = new byte[0];

    public Response OK() {
        status = 200;
        return this;
    }

    public Response Created() {
        status = 201;
        return this;
    }

    public Response Accpeted() {
        status = 202;
        return this;
    }

    public Response BadRequest() {
        status = 400;
        return this;
    }

    public Response Unauthorized() {
        status = 401;
        return this;
    }

    public Response Forbidden() {
        status = 403;
        return this;
    }

    public Response NotFound() {
        status = 404;
        return this;
    }

    public Response BadGateway() {
        status = 502;
        return this;
    }

    public Response ServiceUnavailable() {
        status = 503;
        return this;
    }

    public Response InternalServerError() {
        status = 500;
        return this;
    }

    public Response status(int code) {
        status = code;
        return this;
    }

    public Response MovedPermanently() {
        status = 301;
        return this;
    }

    public Response NoContent() {
        status = 204;
        return this;
    }

    public Response header(String key, String value) {
        header.put(key, value);
        return this;
    }

    public Response model(Object object) {
        content = gson.toJson(object).getBytes();
        return this;
    }

    public Response data(String data) {
        content = data.getBytes();
        return this;
    }

    public byte[] build() {
        ByteMemoryStream outputStream = new ByteMemoryStream();
        String response = "HTTP/1.1 " + status + " " + messageFromStatus(status) + "\n";
        response += "Date: " + dateFormat.format(new Date()) + "\n";

        if (!header.containsKey("Server")) {
            response += "Server: TinyRest/1.0 (Java)\n";
        }

        for (String key : header.keySet()) {
            response += key + ": " + header.get(key) + "\n";
        }
        if (!header.containsKey("Content-Type") && content.length > 0) {
            response += "Content-Type: application/json\n";
        }

        if (!header.containsKey("Connection")) {
            response += "Connection: Closed\n";
        }
        
        response += "Content-Length: " + content.length + "\n";
        response += "\n";

        outputStream.writeBytes(response.getBytes());
        if (content.length > 0) {
            outputStream.writeBytes(content);
        }
        System.out.println("");
        return outputStream.toByteArray();
    }

    private String messageFromStatus(int code) {
        if (code == 200) {
            return "OK";
        }
        if (code == 201) {
            return "Created";
        }
        if (code == 202) {
            return "Accpeted";
        }
        if (code == 204) {
            return "No Content";
        }
        if (code == 301) {
            return "Moved Permanently";
        }
        if (code == 400) {
            return "Bad Request";
        }
        if (code == 401) {
            return "Unauthorized";
        }
        if (code == 403) {
            return "Forbidden";
        }
        if (code == 404) {
            return "Not Found";
        }
        if (code == 502) {
            return "Bad Gateway";
        }
        if (code == 503) {
            return "Service Unavailable";
        }
        if (code == 500) {
            return "Internal Server Error";
        }

        return "Status";
    }

}
