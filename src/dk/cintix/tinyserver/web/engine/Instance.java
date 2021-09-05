package dk.cintix.tinyserver.web.engine;

import dk.cintix.tinyserver.Application;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author migo
 */
public class Instance {

    private final String name;
    private final int offset;
    private final int length;
    private String content = "";
    private Map<String, String> variables = new LinkedHashMap<>();

    public Instance(String name, int offset, int length) {
        this.name = name;
        this.offset = offset;
        this.length = length;
    }

    public String getName() {
        return name;
    }

    public int getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables;
    }

    
    public GeneratedHTML buildTagFromVariables(String variableName, String variableValue) {
        String tmp = null;
        Document document = null;
        Set<String> keySet = new HashSet<String>();
        keySet.addAll(variables.keySet());

        if (keySet.contains("data-file")) {
            String filename = Application.get("DOCUMENT_ROOT") + variables.get("data-file");
            document = DocumentEngine.readTemplate(null, new File(filename));
            return new GeneratedHTML(document, tmp);
        }

        if (keySet.contains("data-in-tag")) {
            tmp = "<" + variables.get("data-in-tag");
            keySet.remove("data-in-tag");
            for (String key : keySet) {
                if (key.toLowerCase().startsWith("data-with-")) {
                    String value = variables.get(key);
                    if (value.contains(variableName)) {
                        value = value.replaceAll(variableName, variableValue);
                    }
                    tmp += " " + key.substring(10) + "=" + "\"" + value + "\"";
                }
            }
            tmp += " />";
        }
        if (keySet.contains("data-in-tags")) {
            String tag = variables.get("data-in-tags");
            tmp = "<" + tag;
            keySet.remove("data-in-tags");
            for (String key : keySet) {
                if (key.toLowerCase().startsWith("data-with-")) {
                    String value = variables.get(key);
                    tmp += " " + key.substring(10) + "=" + "\"" + value + "\"";
                }
            }
            tmp += ">" + variableValue + "</" + tag + ">";
        }
        return new GeneratedHTML(document, tmp);
    }

    
    @Override
    public String toString() {
        return "Instance{" + "name=" + name + ", offset=" + offset + ", length=" + length + ", content=" + content + ", variables=" + variables + '}';
    }

}
