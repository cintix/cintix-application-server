package dk.cintix.application.server.ssl;

import dk.cintix.application.server.ssl.certificate.SSLCertificateManager;
import dk.cintix.application.server.ssl.certificate.SignedBy;
import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 *
 * @author Michael Martinsen
 */
public class SSLContextManager {

    private SignedBy signedBy;

    public SSLContextManager() {
        signedBy = new SignedBy("Cintix", "Development", "Cintix", "Dalmose", "", "Denmark", "cintix");
    }

    public SSLContext getContext(SignedBy signedBy, String key) {
        this.signedBy = signedBy;
        return getContext(key);
    }

    public SSLContext getContext(String key) {
        try {
            SSLCertificateManager certificateManager = new SSLCertificateManager();

            KeyStore keyStore = certificateManager.loadKeystore(key);

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");

            keyManagerFactory.init(keyStore, key.toCharArray());

            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init(keyStore);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);

            return sslContext;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
