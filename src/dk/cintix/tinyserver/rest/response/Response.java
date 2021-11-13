/*
 */
package dk.cintix.tinyserver.rest.response;

import com.google.gson.Gson;
import dk.cintix.tinyserver.Application;
import dk.cintix.tinyserver.io.memory.ByteMemoryStream;
import dk.cintix.tinyserver.model.ModelGenerator;
import dk.cintix.tinyserver.model.generators.JSONGenerator;
import dk.cintix.tinyserver.model.generators.TextGenerator;
import dk.cintix.tinyserver.rest.http.Status;
import dk.cintix.tinyserver.rest.http.request.RestHttpRequest;
import dk.cintix.tinyserver.web.engine.Document;
import dk.cintix.tinyserver.web.engine.DocumentEngine;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author cix
 */
public class Response {

    private final static Map<String, ModelGenerator> contextGenerators = new TreeMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
    private final Gson gson = new Gson();

    private int status = 200;
    private Map<String, String> header = new LinkedHashMap<>();
    private byte[] content = new byte[0];
    private String contentType = "application/json";

    static {
        if (!contextGenerators.containsKey("application/json")) {
            contextGenerators.put("application/json", new JSONGenerator());
        }

        if (!contextGenerators.containsKey("text/plain")) {
            contextGenerators.put("text/plain", new TextGenerator());
            contextGenerators.put("default", new TextGenerator());
        }
    }

    public Response() {
    }

    public int getStatus() {
        return status;
    }

    public static Map<String, ModelGenerator> getContextGenerators() {
        return contextGenerators;
    }

    public ModelGenerator getGenerator() {
        ModelGenerator generator = null;
        if (contextGenerators.containsKey(contentType)) {
            generator = contextGenerators.get(contentType);
        } else {
            generator = new TextGenerator();
            contentType = "text/plain";
        }
        return generator;
    }

    public static void registerModelGenerator(String contentType, ModelGenerator mg) {
        contextGenerators.put(contentType, mg);
    }

    public Response OK() {
        status = Status.OK.getValue();
        return this;
    }

    public Response Created() {
        status = Status.Created.getValue();
        return this;
    }

    public Response Accpeted() {
        status = Status.Accpeted.getValue();
        return this;
    }

    public Response BadRequest() {
        status = Status.BadRequest.getValue();
        return this;
    }

    public Response Unauthorized() {
        status = Status.Unauthorized.getValue();
        return this;
    }

    public Response Forbidden() {
        status = Status.Forbidden.getValue();
        return this;
    }

    public Response NotFound() {
        status = Status.NotFound.getValue();
        return this;
    }

    public Response BadGateway() {
        status = Status.BadGateway.getValue();
        return this;
    }

    public Response ServiceUnavailable() {
        status = Status.ServiceUnavailable.getValue();
        return this;
    }

    public Response InternalServerError() {
        status = Status.InternalServerError.getValue();
        return this;
    }

    public Response status(int code) {
        status = code;
        return this;
    }

    public Response MovedPermanently() {
        status = Status.MovedPermanently.getValue();
        return this;
    }

    public Response NoContent() {
        status = Status.NoContent.getValue();
        return this;
    }

    public Response header(String key, String value) {
        header.put(key, value);
        return this;
    }

    public Response ContentType(String content) {
        contentType = content;
        return this;
    }

    public Response model(Object object) {
        ModelGenerator generator = getGenerator();
        content = generator.fromModel(object).getBytes();
        return this;
    }

    public Response document(RestHttpRequest request, String name) {
        contentType = "text/html";
        String path = Application.get("DOCUMENT_ROOT");
        File file = new File(path + "/" + name);
        if (file.exists()) {
            try {
                Document document = DocumentEngine.readTemplate(request, file);
                content = document.getData().getBytes();
                document = null;
            } catch (Exception ex) {
                Logger.getLogger(Response.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return this;
    }

    public Response Content(byte[] content) {
        this.content = content;
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
            response += "Server: TinyRest/1.1 (Java)\n";
        }

        for (String key : header.keySet()) {
            response += key + ": " + header.get(key) + "\n";
        }
        if (!header.containsKey("Content-Type") && content.length > 0) {
            response += "Content-Type: " + contentType;
            if (contentType.toLowerCase().contains("/text")) response += "; charset=utf-8";
            if (contentType.toLowerCase().contains("/json")) response += "; charset=utf-8";
            if (contentType.toLowerCase().contains("plain")) response += "; charset=utf-8";
            if (contentType.toLowerCase().contains("html"))  response += "; charset=utf-8";
            response += "\n";
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
