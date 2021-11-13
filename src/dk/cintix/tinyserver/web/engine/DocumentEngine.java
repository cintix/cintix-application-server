package dk.cintix.tinyserver.web.engine;

import dk.cintix.tinyserver.rest.http.request.RestHttpRequest;
import dk.cintix.tinyserver.web.tags.Tag;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author migo
 */
public class DocumentEngine {

    private static final Map<File, Long> documentModifiedDate = new LinkedHashMap<>();
    private static final Map<String, Document> documentList = new LinkedHashMap<>();
    private static final Map<String, Class> tagToClass = new LinkedHashMap<>();
    private static String tagPrefix = "<tiny-data";
    
    public static Tag get(String name) {
        if (tagToClass.containsKey(name)) {
            try {
                return (Tag) tagToClass.get(name).newInstance();
            } catch (InstantiationException | IllegalAccessException ex) {
                Logger.getLogger(DocumentEngine.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    public static String getTagPrefix() {
        return tagPrefix;
    }

    public static void setTagPrefix(String tagPrefix) {
        DocumentEngine.tagPrefix = tagPrefix;
    }

    public static void addDataClass(String name, Class<?> tagclass) throws Exception {
        if (tagclass == null) {
            return;
        }
        if (Tag.class.isAssignableFrom(tagclass)) {
            tagToClass.put(name, tagclass);
        } else {
            throw new Exception(tagclass.getName() + " doesn't implements Tag class");
        }
    }

    public static Document readTemplate(RestHttpRequest exchange, File file) {

        if (documentModifiedDate.containsKey(file)) {
            if (file.lastModified() == documentModifiedDate.get(file)) {
                return processDocument(exchange, documentList.get(file.getAbsolutePath()));
            }
        }

        byte[] fileContent = new byte[0];
        try {
            if (file.exists()) {
                fileContent = Files.readAllBytes(file.toPath());
            }
        } catch (IOException ioe) {
        }

        String html = new String(fileContent, 0, fileContent.length);
        Document document = new Document(file.getAbsolutePath(), file.lastModified());
        Map<String, Instance> variableInstances = new LinkedHashMap<>();

        int offset = 0;
        while (offset != -1) {
            String searchData = html.toLowerCase();
            offset = searchData.indexOf(tagPrefix, offset);

            if (offset == -1) {
                break;
            }

            int length = searchData.indexOf(">", offset + 1) - offset;
            length++;

            String variablesData = html.substring(offset, offset + length);

            variablesData = variablesData.replaceAll(tagPrefix, "");
            variablesData = variablesData.replaceAll("/>", "");

            String start = html.substring(0, offset);
            String end = html.substring(offset + length);

            Map<String, String> variables = new LinkedHashMap<>();

            int variableOffset = 0;
            while (variableOffset != -1) {
                variableOffset = variablesData.indexOf("\"", variableOffset);
                if (variableOffset == -1) {
                    break;
                }

                String variableName = variablesData.substring(0, variableOffset);
                variableName = variableName.replaceAll("=", "");
                variablesData = variablesData.substring(variableOffset);

                variableOffset = 1;

                variableOffset = variablesData.indexOf("\"", variableOffset);
                if (variableOffset == -1) {
                    break;
                }
                variableOffset++;

                String variableValue = variablesData.substring(1, variableOffset - 1);
                variablesData = variablesData.substring(variableOffset);
                variableOffset = 0;
                variables.put(variableName.trim(), variableValue.trim());
            }

            Instance instance = new Instance(variables.get("data-class"), offset, offset + length);
            variableInstances.put(variables.get("data-class"), instance);
            html = start + "${" + variables.get("data-class") + "}" + end;
            variables.remove("data-class");
            instance.setVariables(variables);

            offset = 0;
        }

        document.setData(html);
        document.setVariables(variableInstances);

        documentModifiedDate.put(file, document.getModified());
        documentList.put(file.getAbsolutePath(), document);

        return processDocument(exchange, document);

    }

    private static Document processDocument(RestHttpRequest exchange, Document document) {

        Document clone = new Document(null, 0);
        clone.setData(document.getData());
        clone.setVariables(document.getVariables());

        Map<String, String> requestQueryStrings = new LinkedHashMap<>();
        Map<String, String> postFields = new LinkedHashMap<>();
        Map<String, String> requestFields = new LinkedHashMap<>();

        if (exchange != null) {
            requestQueryStrings = exchange.getQueryStrings();
            postFields = exchange.getPostParams();
            requestFields = exchange.getQueryStrings();
            String requestedURL = "http://" + exchange.getHeader("Host") + exchange.getContextPath();

            clone.setPostFields(postFields);
            clone.setRequestFields(requestFields);
            clone.setRequestQueryStrings(requestQueryStrings);
            clone.setPost(exchange.getMethod().equalsIgnoreCase("post"));
            clone.setRequestedUrl(requestedURL);
        }

        for (String variable : clone.getVariables().keySet()) {
            Tag tag = DocumentEngine.get(variable);
            if (tag != null) {
                if (exchange != null) {
                    tag.setPost(postFields);
                    tag.setQuery(requestQueryStrings);
                    tag.setRequest(requestFields);
                    tag.setCustomObjects(exchange.getCustomObjects());
                }
                Instance instance = clone.getVariables().get(variable);
                clone.replace(instance, tag.toHTML(clone.getVariables()));
            } else {
                System.err.println("Tag not setup " + variable);
                Instance instance = clone.getVariables().get(variable);
                clone.replace(instance, "");
            }
        }
        return clone;
    }

    public static void main(String[] args) {
        Document document = DocumentEngine.readTemplate(null, new File("index.htm"));
    }

}
