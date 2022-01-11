/*
 */
package dk.cintix.application.server.rest.jsd.models;

import dk.cintix.application.server.rest.http.Status;

/**
 *
 * @author cix
 */
public class Cache {

    private Status status;
    private String description;
    private long timeToLive;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public long getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
