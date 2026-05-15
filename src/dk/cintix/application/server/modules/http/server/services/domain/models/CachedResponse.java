package dk.cintix.application.server.modules.http.server.services.domain.models;

/**
 *
 * @author migo
 */
public class CachedResponse extends Response {

    private final byte[] data;

    public CachedResponse(byte[] data) {
        this.data = data;
    }

    @Override
    public byte[] build() {
        return data;
    }

    @Override
    public String toString() {
        return "CachedResponse{" + "data=" + data + '}';
    }

}
