package dk.cintix.tinyserver.demo.entities;

import dk.cintix.tinyserver.demo.managers.ChannelManager;
import dk.cintix.tinyserver.jdbc.annotations.Entity;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The type Blok.
 *
 * @author hjep
 */
@Entity(manager = ChannelManager.class)
public abstract class Channel {

    private int id;
    private int providerId;
    private boolean isTVCHannel;
    private boolean enable;
    private int countryCode;
    private int view;
    private String type;
    private long externalId;

    private String name;
    private String url;
    private String identifier;
    private String label;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getExternalId() {
        return externalId;
    }

    public void setExternalId(long externalId) {
        this.externalId = externalId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProviderId() {
        return providerId;
    }

    public void setProviderId(int providerId) {
        this.providerId = providerId;
    }

    public boolean isIsTVCHannel() {
        return isTVCHannel;
    }

    public void setIsTVCHannel(boolean isTVCHannel) {
        this.isTVCHannel = isTVCHannel;
    }

    public int getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(int countryCode) {
        this.countryCode = countryCode;
    }

    public int getView() {
        return view;
    }

    public void setView(int view) {
        this.view = view;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIdentifier() {
        if (identifier == null || identifier.isEmpty()) {
            return "" + getExternalId();
        }
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getLabel() {
        return (label);
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getAlias() {
        return (label == null || label.equals("")) ? name : label;
    }

    /**
     * Create.
     *
     * @return the boolean
     */
    public abstract boolean create();

    /**
     * Update.
     *
     * @return the boolean
     */
    public abstract boolean update();

    /**
     * Delete.
     *
     * @return the boolean
     */
    public abstract boolean delete();

    public abstract boolean delete(int id);

    public abstract List<Channel> loadAll();

    public abstract Channel load();

    public abstract Channel load(int id);

    public abstract Map<Long, Channel> loadAllExternalId();

    public abstract Map<Integer, String> loadChannelLogoMap();

    public abstract String getLogoUrl();

    public abstract Map<Integer, String> loadRunningNowMap();

    public abstract int getServiceId();

    public abstract boolean setAlias(String name);

    public abstract Map<String, Channel> loadAllWhatsOnChannels();

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + this.providerId;
        hash = 97 * hash + (this.isTVCHannel ? 1 : 0);
        hash = 97 * hash + this.countryCode;
        hash = 97 * hash + this.view;
        hash = 97 * hash + Objects.hashCode(this.type);
        hash = 97 * hash + (int) (this.externalId ^ (this.externalId >>> 32));
        hash = 97 * hash + Objects.hashCode(this.name);
        hash = 97 * hash + Objects.hashCode(this.url);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Channel other = (Channel) obj;
        if (this.providerId != other.providerId) {
            return false;
        }
        if (this.isTVCHannel != other.isTVCHannel) {
            return false;
        }

        if (this.countryCode != other.countryCode) {
            return false;
        }
        if (this.view != other.view) {
            return false;
        }
        if (this.externalId != other.externalId) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return Objects.equals(this.url, other.url);
    }

    @Override
    public String toString() {
        return "Channel{" + "id=" + id + ", providerId=" + providerId + ", isTVCHannel=" + isTVCHannel + ", enable=" + enable + ", countryCode=" + countryCode + ", view=" + view + ", type=" + type + ", externalId=" + externalId + ", name=" + name + ", url=" + url + ", identifier=" + identifier + '}';
    }

}
