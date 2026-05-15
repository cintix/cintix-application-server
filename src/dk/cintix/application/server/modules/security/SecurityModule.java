package dk.cintix.application.server.modules.security;

import dk.cintix.application.server.modules.security.services.domain.models.SignedBy;
import javax.net.ssl.SSLContext;

/**
 * Public contract for the security module.
 *
 * @author Michael Martinsen
 */
public interface SecurityModule {
    SSLContext getContext(SignedBy signedBy, String key);
    SSLContext getContext(String key);
}
