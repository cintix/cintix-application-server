package dk.cintix.application.server.modules.http.server.services.domain.models;

import com.google.gson.Gson;
import dk.cintix.application.server.infrastructure.Application;
import dk.cintix.application.server.infrastructure.ByteMemoryStream;
import dk.cintix.application.server.infrastructure.Status;
import dk.cintix.application.server.modules.http.server.endpoint.RestHttpRequest;
import dk.cintix.application.server.modules.http.server.services.domain.ModelGenerator;
import dk.cintix.application.server.modules.http.server.services.domain.generators.JSONGenerator;
import dk.cintix.application.server.modules.http.server.services.domain.generators.TextGenerator;
import dk.cintix.html.engine.HTMLEngine;
import java.io.File;
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
    private final Map<String, String> variables = new TreeMap<>();

    private int status = 200;
    private Map<String, String> header = new LinkedHashMap<>();
    private byte[] content = new byte[0];
    private String contentType = "application/json";
    private boolean useChunkedEncoding;

    static {
        contextGenerators.put("application/json", new JSONGenerator());
        contextGenerators.put("text/plain", new TextGenerator());
        contextGenerators.put("default", new TextGenerator());
    }

    public Response() {
    }

    public int getStatus() {
        return status;
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isChunked() {
        return useChunkedEncoding;
    }

    public static Map<String, ModelGenerator> getContextGenerators() {
        return contextGenerators;
    }

    public ModelGenerator getGenerator() {
        ModelGenerator generator = null;
        if (contextGenerators.containsKey(contentType)) {
            generator = contextGenerators.get(contentType);
        } else {
            generator = (ModelGenerator) contextGenerators.get("default");
            contentType = "text/plain";
        }
        return generator;
    }

    public static void registerModelGenerator(String contentType, ModelGenerator mg) {
        contextGenerators.put(contentType, mg);
    }

    public Response variable(String name, String value) {
        variables.put("@" + name, value);
        return this;
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

    public Response RequestTimeout() {
        status = 408;
        return this;
    }

    public Response TooManyRequests() {
        status = Status.TooManyRequests.getValue();
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

    public Response MovedTemporary() {
        status = Status.MovedTemporary.getValue();
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

    public Response Location(String uri) {
        header.put("Location", uri);
        return this;
    }

    public Response ContentType(String content) {
        contentType = content;
        return this;
    }

    /**
     * Enables chunked transfer encoding for this response.
     * When enabled, Content-Length is omitted and the body is sent
     * as a series of sized chunks terminated by a zero-length chunk.
     */
    public Response chunked() {
        useChunkedEncoding = true;
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
                Map<String, String> properties = new TreeMap<>();
                properties.putAll(request.getPostParams());
                properties.putAll(request.getQueryStrings());
                properties.putAll(variables);
                Map<String, Object> resources = new TreeMap<>();
                resources.put(RestHttpRequest.class.getName(), request);
                content = HTMLEngine.process(file, properties, resources).getBytes();
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
        String response = "HTTP/1.1 " + status + " " + messageFromStatus(status) + "\r\n";
        response += "Date: " + dateFormat.format(new Date()) + "\r\n";

        if (!header.containsKey("Server")) {
            response += "Server: Cintix-Application-Server(CAS)/3.0.2\r\n";
        }

        for (String key : header.keySet()) {
            response += key + ": " + header.get(key) + "\r\n";
        }
        if (!header.containsKey("Content-Type") && content.length > 0) {
            response += "Content-Type: " + contentType;
            if (contentType.toLowerCase().contains("/text")) {
                response += "; charset=utf-8";
            }
            if (contentType.toLowerCase().contains("/json")) {
                response += "; charset=utf-8";
            }
            if (contentType.toLowerCase().contains("plain")) {
                response += "; charset=utf-8";
            }
            if (contentType.toLowerCase().contains("html")) {
                response += "; charset=utf-8";
            }
            response += "\r\n";
        }

        if (!header.containsKey("Connection")) {
            response += "Connection: Closed\r\n";
        }

        if (useChunkedEncoding) {
            response += "Transfer-Encoding: chunked\r\n";
            response += "\r\n";
            outputStream.writeBytes(response.getBytes());
            // Write body in chunked format
            if (content.length > 0) {
                String chunkSize = Integer.toHexString(content.length) + "\r\n";
                outputStream.writeBytes(chunkSize.getBytes());
                outputStream.writeBytes(content);
                outputStream.writeBytes("\r\n".getBytes());
            }
            // Terminating chunk
            outputStream.writeBytes("0\r\n\r\n".getBytes());
        } else {
            response += "Content-Length: " + content.length + "\r\n";
            response += "\r\n";

            outputStream.writeBytes(response.getBytes());
            if (content.length > 0) {
                outputStream.writeBytes(content);
            }
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
        if (code == 302) {
            return "Temporary Redirect";
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
        if (code == 408) {
            return "Request Timeout";
        }
        if (code == 429) {
            return "Too Many Requests";
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
