/*
 */
package dk.cintix.tinyserver.demo.model;

/**
 *
 * @author cix
 */
public class ResponseModel {

    private final String message;

    public ResponseModel(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "ResponseModel{" + "message=" + message + '}';
    }

}