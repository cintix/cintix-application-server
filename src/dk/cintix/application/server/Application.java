package dk.cintix.application.server;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author migo
 */
public class Application {

    private static final Map<String, String> _CONTEXT_MAP = new LinkedHashMap<>();

    /**
     *
     * @param key
     * @param value
     */
    public static void set(String key, String value) {
        _CONTEXT_MAP.put(key, value);
    }

    /**
     *
     * @param key
     * @return
     */
    public static String get(String key) {
        if (_CONTEXT_MAP.containsKey(key)) {
            return _CONTEXT_MAP.get(key);
        }
        return null;
    }

    /**
     * Get Application Folder
     *
     * @return
     */
    public static String getPath() {
        File file = new File("");
        return file.getAbsolutePath();
    }

    /**
     *
     * @return
     */
    public static File getConfigFolder() {
        File configFolder = new File(getPath() + "/conf");
        if (!configFolder.exists() || configFolder.isDirectory()) {
            configFolder.mkdir();
        }
        return configFolder;
    }

    /**
     *
     * @return
     */
    public static File getCacheFolder() {
        File cacheFolder = new File(getPath() + "/cache");
        if (!cacheFolder.exists() || cacheFolder.isDirectory()) {
            cacheFolder.mkdir();
        }
        return cacheFolder;
    }
}
