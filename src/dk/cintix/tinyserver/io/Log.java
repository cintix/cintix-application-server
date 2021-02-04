/*
 */
package dk.cintix.tinyserver.io;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;

/**
 *
 * @author migo
 */
public class Log {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss.S");
    private static Log instance = null;
    private FileOutputStream stream = null;

    private Log() {
        File logDirectory = new File("log");
        if (!logDirectory.isDirectory()) {
            logDirectory.mkdir();
        }
    }

    public static Log instane() {
        if (instance == null) {
            instance = new Log();
        }
        return instance;
    }

    public void log(String s) {
        appendToLog("LOG", s);
    }

    public void log(Object o) {
        appendToLog("LOG", o);
    }

    public void warn(String s) {
        appendToLog("WARRING", s);
    }

    public void warn(Object o) {
        appendToLog("WARRING", o);
    }

    public void debug(String s) {
        appendToLog("DEBUG", s);
    }

    public void debug(Object o) {
        appendToLog("DEBUG", o);
    }

    private void appendToLog(String level, Object s) {
        try {
            if (stream == null) {
                stream = new FileOutputStream("log/server.log", true);
            }
            stream.write(("[" + level + "][" + s + "] " + s.toString() + "\n").getBytes());
        } catch (Exception exception) {
        }
    }

}
