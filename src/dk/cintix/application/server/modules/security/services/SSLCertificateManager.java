package dk.cintix.application.server.modules.security.services;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Michael Martinsen
 */
public class SSLCertificateManager {

    private static final Logger logger = Logger.getLogger(SSLCertificateManager.class.getName());
    private KeyStore keyStore;
    private final int keysize = 1024 * 2;

    public SSLCertificateManager() {
        try {
            keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to initialize default KeyStore", ex);
        }
    }

    public boolean loadSignedCertificate(String key) {
        if (new File(".keystore").exists()) {
            System.setProperty("javax.net.ssl.trustStore", ".keystore");
            System.setProperty("javax.net.ssl.keyStorePassword", key);
            return true;
        }
        return false;
    }

    public KeyStore loadKeystore(String key) {
        try (FileInputStream inputStream = new FileInputStream(".keystore")) {
            keyStore = KeyStore.getInstance("JKS");
            keyStore.load(inputStream, key.toCharArray());
            return keyStore;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load keystore from file", e);
        }
        return null;
    }

}
