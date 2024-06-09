package com.fronzec.singlethreaded.batchjobs.job1;

public class Person {

  private String firstName;

  private String lastName;

  private String email;

  private String profession;

  public Person() {}

  public Person(String firstName, String lastName, String email, String profession) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.profession = profession;
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
    return ("Person{" + ", firstName='" + firstName + '\'' + ", lastName='" + lastName + '\'' + '}');
  }
}
