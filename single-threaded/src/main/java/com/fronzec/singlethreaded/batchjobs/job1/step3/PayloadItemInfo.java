package com.fronzec.singlethreaded.batchjobs.job1.step3;

public class PayloadItemInfo {

  private String uuidV4;

  private String firstName;

  private String lastName;

  private String email;

  private String profession;

  public PayloadItemInfo() {}

  public PayloadItemInfo(
      String uuidV4, String firstName, String lastName, String email, String profession) {
    this.uuidV4 = uuidV4;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.profession = profession;
  }

  public String getUuidV4() {
    return uuidV4;
  }

  public void setUuidV4(String uuidV4) {
    this.uuidV4 = uuidV4;
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
}
