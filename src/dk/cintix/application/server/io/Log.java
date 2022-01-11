/*
 */
package dk.cintix.application.server.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * @author migo
 */
public class Log {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss.SSS");
    private static Log instance = null;
    private final String filename = "log/server.log";
    private FileOutputStream stream = null;
    private int maxTurnOvers = 5;

    private Log() {
        File logDirectory = new File("log");
        if (!logDirectory.isDirectory()) {
            logDirectory.mkdir();
        }
    }

    public static Log instance() {
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

    public void error(String s) {
        appendToLog("ERROR", s);
    }

    public void error(Object o) {
        appendToLog("ERROR", o);
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

    public long size() {
        File file = new File(filename);
        if (file.exists()) {
            return file.length();
        }
        return 0;
    }

    public void turn() {
        int moveFrom = maxTurnOvers;

        for (int index = 1; index <= maxTurnOvers; index++) {
            File logFileToCheckFor = new File(filename + "." + index);
            if (!logFileToCheckFor.exists()) {
                moveFrom = index;
                close();

                if (index > 1) {
                    moveLogFiles(index);
                }

                File newLogFile = new File(filename + ".1");
                File oldFile = new File(filename);
                oldFile.renameTo(newLogFile);
                reset();

                return;
            }
        }
        moveLogFiles(maxTurnOvers);
    }

    private void moveLogFiles(int moveFrom) {
        if (moveFrom == maxTurnOvers) {
            File mustGo = new File(filename + "." + maxTurnOvers);
            mustGo.delete();
            close();
        }

        for (int index = moveFrom; index > 1; index--) {
            File newLogFile = new File(filename + "." + index);
            File oldFile = new File(filename + "." + (index - 1));
            oldFile.renameTo(newLogFile);
        }

        if (moveFrom == maxTurnOvers) {
            File newLogFile = new File(filename + ".1");
            File oldFile = new File(filename);
            oldFile.renameTo(newLogFile);
        }
    }

    private void close() {
        if (stream != null) {
            try {
                stream.flush();
                stream.close();
                stream = null;
            } catch (IOException iOException) {
            }
        }
    }

    public void reset() {
        try {
            if (stream != null) {
                stream.flush();
                stream.close();
                stream = null;
            }

            if (stream == null) {
                stream = new FileOutputStream(filename);
            }
        } catch (Exception exception) {
        }
    }

    private void appendToLog(String level, Object s) {
        try {
            if (stream == null) {
                stream = new FileOutputStream(filename, true);
            }
            stream.write(("[" + level + "][" + dateFormat.format(new Date()) + "] " + s.toString() + "\n").getBytes());
        } catch (Exception exception) {
        }
    }

}
