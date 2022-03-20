package dk.cintix.application.server.ssl.certificate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 *
 * @author Michael Martinsen
 */
public class SSLCertificateManager {

    private KeyStore keyStore;
    private final int keysize = 1024 * 2;

    public SSLCertificateManager() {
        try {
            keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);

            //   X500Name x500Name = new X500Name(commonName, organizationalUnit, organization, city, state, country);
        } catch (Exception ex) {
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
        try {
            keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(".keystore"), key.toCharArray());
            return keyStore;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
