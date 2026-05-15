package dk.cintix.application.server.modules.security.services;

import dk.cintix.application.server.modules.security.SecurityModule;
import dk.cintix.application.server.modules.security.services.domain.models.SignedBy;
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
public class SSLContextManager implements SecurityModule {

    private SignedBy signedBy;

    public SSLContextManager() {
        signedBy = new SignedBy("Cintix", "Development", "Cintix", "Dalmose", "", "Denmark", "cintix");
    }

    @Override
    public SSLContext getContext(SignedBy signedBy, String key) {
        this.signedBy = signedBy;
        return getContext(key);
    }

    @Override
    public SSLContext getContext(String key) {
        try {
            SSLCertificateManager certificateManager = new SSLCertificateManager();

            KeyStore keyStore = certificateManager.loadKeystore(key);
            if (keyStore == null) {
                return null;
            }

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
