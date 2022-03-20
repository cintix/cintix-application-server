package dk.cintix.application.server.ssl.certificate;

/**
 *
 * @author Michael Martinsen
 */
public class SignedBy {

    private String commonName;
    private String organizationalUnit;
    private String organization;
    private String city;
    private String state;
    private String country;
    private long validity = 1096 * 3; // 3 years
    private String alias;

    public SignedBy() {
    }

    public SignedBy(String commonName, String organizationalUnit, String organization, String city, String state, String country, String alias) {
        this.commonName = commonName;
        this.organizationalUnit = organizationalUnit;
        this.organization = organization;
        this.city = city;
        this.state = state;
        this.country = country;
        this.alias = alias;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getOrganizationalUnit() {
        return organizationalUnit;
    }

    public void setOrganizationalUnit(String organizationalUnit) {
        this.organizationalUnit = organizationalUnit;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public long getValidity() {
        return validity;
    }

    public void setValidity(long validity) {
        this.validity = validity;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Override
    public String toString() {
        return "SignedBy{" + "commonName=" + commonName + ", organizationalUnit=" + organizationalUnit + ", organization=" + organization + ", city=" + city + ", state=" + state + ", country=" + country + ", validity=" + validity + ", alias=" + alias + '}';
    }

}
