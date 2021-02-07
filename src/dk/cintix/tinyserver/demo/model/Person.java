/*
 */
package dk.cintix.tinyserver.demo.model;

/**
 *
 * @author cix
 */
public class Person {

    private String name;
    private int pinCode;
    private String email;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPinCode() {
        return pinCode;
    }

    public void setPinCode(int pinCode) {
        this.pinCode = pinCode;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "Person{" + "name=" + name + ", pinCode=" + pinCode + ", email=" + email + '}';
    }
}