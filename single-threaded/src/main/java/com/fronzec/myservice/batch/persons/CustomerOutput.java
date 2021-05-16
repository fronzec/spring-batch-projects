package com.fronzec.myservice.batch.persons;

import java.time.LocalDateTime;

public class CustomerOutput {

    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String profession;

    public CustomerOutput() {
    }

    public CustomerOutput(String firstName, String lastName, String fullName, String email, String profession) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.fullName = fullName;
        this.email = email;
        this.profession = profession;
    }

    public static CustomerOutput fromCustomer(Customer customer) {
        return new CustomerOutput(customer.getFirstName(), customer.getLastName(),
                customer.getFirstName() + " "+customer.getLastName(), customer.getEmail(), customer.getProfession() );

    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public String toString() {
        return "Customer{" +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", fullName='" + fullName + '\'' +
                '}';
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }
}
