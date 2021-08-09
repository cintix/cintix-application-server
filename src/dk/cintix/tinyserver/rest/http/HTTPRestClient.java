/*
 */
package dk.cintix.tinyserver.rest.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 *
 * @author migo
 */
public class HTTPRestClient {

    private static final Logger logger = Logger.getLogger(HTTPRestClient.class.getName());
    private static final String CONTENT_XML = "application/xml";
    private static final String CONTENT_JSON = "application/json";
    private Map<String, String> headersMap = new LinkedHashMap<>();
    private Map<String, String> responseHeadersMap = new LinkedHashMap<>();

    public Map<String, String> getResponseHeadersMap() {
        return responseHeadersMap;
    }

    /**
     * Get Host
     *
     * @return
     */
    public String getHost() {
        return host;
    }

    public Map<String, String> getHeadersMap() {
        return headersMap;
    }

    public void setHeadersMap(Map<String, String> headersMap) {
        this.headersMap = headersMap;
    }

    public int getReponseCode() {
        return reponseCode;
    }

    public void setReponseCode(int reponseCode) {
        this.reponseCode = reponseCode;
    }

    /**
     * Get Content type
     */
    public enum HTTPContentType {

        /**
         * JSON Content
         */
        APPLICATION_JSON("application/json"),
        /**
         * XML Content
         */
        APPLICATION_XML("application/xml"),
        /**
         * Binary Content
         */
        APPLICATION_BINARY("application/octet-stream"),
        /**
         * FormPost Content
         */
        APPLICATION_FORM("application/x-www-form-urlencoded"),
        /**
         * Defalt Content
         */
        APPLICATION_DEFAULT("text/html");

        private final String value;

        private HTTPContentType(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

    }

    private String host;
    private final HTTPContentType content;
    private int reponseCode;
    private String location;

    private int readTimeOut = 16000 * 2;
    private int connectionTimeOut = 16000;

    /**
     * Constructor with Host and Content-Type
     *
     * @param host
     * @param content
     */
    public HTTPRestClient(String host, HTTPContentType content) {
        this.host = host;
        this.content = content;
    }

    /**
     * Set the host
     *
     * @param host
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Get read timeout
     *
     * @return
     */
    public int getReadTimeOut() {
        return readTimeOut;
    }

    /**
     * Set read timeout
     *
     * @param readTimeOut
     */
    public void setReadTimeOut(int readTimeOut) {
        this.readTimeOut = readTimeOut;
    }

    /**
     * Get connection timeout
     *
     * @return
     */
    public int getConnectionTimeOut() {
        return connectionTimeOut;
    }

    /**
     * Return location given by host
     *
     * @return
     */
    public String getLocation() {
        return location;
    }

    /**
     * Set connection timeout
     *
     * @param connectionTimeOut
     */
    public void setConnectionTimeOut(int connectionTimeOut) {
        this.connectionTimeOut = connectionTimeOut;
    }

    /**
     * Get content from path
     *
     * @param path
     * @return
     */
    public String get(String path) {
        return action("GET", path, null);
    }

    /**
     * Post content to path
     *
     * @param path
     * @param data
     * @return
     */
    public String post(String path, String data) {
        return action("POST", path, data);
    }

    /**
     * Put content to path
     *
     * @param path
     * @param data
     * @return
     */
    public String put(String path, String data) {
        return action("PUT", path, data);
    }

    /**
     * Delete content on path
     *
     * @param path
     * @return
     */
    public String delete(String path) {
        return action("DELETE", path, null);
    }

    /**
     * Download from path
     *
     * @param path
     * @return
     */
    public InputStream download(String path) {
        try {
            URL remoteURL = new URL(host + path);
            HttpURLConnection urlConnection = (HttpURLConnection) remoteURL.openConnection();

            urlConnection.setRequestMethod("GET");
            urlConnection.setUseCaches(false);
            urlConnection.setDefaultUseCaches(false);

            urlConnection.setConnectTimeout(connectionTimeOut);
            urlConnection.setReadTimeout(readTimeOut);

            StringBuilder sb = new StringBuilder();

            if (content != HTTPContentType.APPLICATION_DEFAULT) {
                urlConnection.setRequestProperty("Content-Type", content.toString());
                if (content != HTTPContentType.APPLICATION_BINARY) {
                    urlConnection.setRequestProperty("Accept", content.toString());
                } else {
                    urlConnection.setRequestProperty("Accept", "*/*");
                }
            }

            urlConnection.connect();
            reponseCode = urlConnection.getResponseCode();
            InputStream isr;

            try {
                if (urlConnection.getHeaderField("Content-Encoding") != null && urlConnection.getHeaderField("Content-Encoding").equals("gzip")) {
                    isr = new GZIPInputStream(urlConnection.getInputStream());
                } else {
                    isr = urlConnection.getInputStream();
                }
            } catch (IOException ioException) {
                //if we fail to get the inputstream, we will try to get the error stream. If this also fails ioException will be caught further down
                if (urlConnection.getHeaderField("Content-Encoding") != null && urlConnection.getHeaderField("Content-Encoding").equals("gzip")) {
                    isr = new GZIPInputStream(urlConnection.getErrorStream());
                } else {
                    isr = urlConnection.getErrorStream();
                }
                logger.log(Level.SEVERE, "download() threw an ioException", ioException);
            }

            return isr;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "download() threw an exception", e);

        }
        return null;

    }

    public String action(String method, String path, String data) {
        try {
            System.out.println("host + path " + host + path);
            URL remoteURL = new URL(host + path);
            HttpURLConnection urlConnection = (HttpURLConnection) remoteURL.openConnection();
            HttpURLConnection.setFollowRedirects(true);
            urlConnection.setRequestMethod(method);
            urlConnection.setUseCaches(false);
            urlConnection.setDefaultUseCaches(false);
            urlConnection.setConnectTimeout(connectionTimeOut);
            urlConnection.setReadTimeout(readTimeOut);

            StringBuilder sb = new StringBuilder();

            if (content != HTTPContentType.APPLICATION_DEFAULT) {
                urlConnection.setRequestProperty("Content-Type", content.toString());
                if (content != HTTPContentType.APPLICATION_BINARY) {
                    urlConnection.setRequestProperty("Accept", content.toString());
                } else {
                    urlConnection.setRequestProperty("Accept", "*/*");
                }
            }

            if (headersMap != null && headersMap.size() > 0) {
                for (String hKey : headersMap.keySet()) {
                    if (!hKey.isEmpty()) {
                        urlConnection.setRequestProperty(hKey, headersMap.get(hKey));
                    }
                }
            }

            if (data != null && !data.isEmpty()) {
                urlConnection.setDoOutput(true);
                urlConnection.connect();
                OutputStream outputStream = urlConnection.getOutputStream();
                outputStream.write(data.getBytes());
                outputStream.flush();
            } else {
                urlConnection.connect();
            }

            reponseCode = urlConnection.getResponseCode();
            location = urlConnection.getHeaderField("Location");
            InputStreamReader isr = null;

            try {
                if (urlConnection.getHeaderField("Content-Encoding") != null && urlConnection.getHeaderField("Content-Encoding").equals("gzip")) {
                    isr = new InputStreamReader(new GZIPInputStream(urlConnection.getInputStream()));
                } else {
                    isr = new InputStreamReader(urlConnection.getInputStream());
                }
            } catch (IOException ioException) {
                //if we fail to get the inputstream, we will try to get the error stream. If this also fails ioException will be caught further down
                if (urlConnection.getHeaderField("Content-Encoding") != null && urlConnection.getHeaderField("Content-Encoding").equals("gzip")) {
                    isr = new InputStreamReader(new GZIPInputStream(urlConnection.getErrorStream()));
                } else {
                    try {
                        isr = new InputStreamReader(urlConnection.getErrorStream());
                    } catch (Exception e) {
                    }
                }
                //logger.log(Level.SEVERE, "action() threw an ioException", ioException);
            }

            responseHeadersMap = new LinkedHashMap<>();
            responseHeadersMap.put("Response", "" + reponseCode);

            for (String headerKey : urlConnection.getHeaderFields().keySet()) {
                if (headerKey != null) {
                    responseHeadersMap.put(headerKey, urlConnection.getHeaderField(headerKey));
                }
            }

            if (isr == null) {
                urlConnection.disconnect();
                return sb.toString();
            }

            int numCharsRead;
            char[] charArray = new char[2048];
            while ((numCharsRead = isr.read(charArray)) > 0) {
                sb.append(charArray, 0, numCharsRead);
            }

            isr.close();
            urlConnection.disconnect();
            return sb.toString();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "action() threw an exception", e);

        }
        return null;
    }

    /**
     * Get HTTP status code
     *
     * @return
     */
    public int getHttpReponseCode() {
        return reponseCode;
    }

}
