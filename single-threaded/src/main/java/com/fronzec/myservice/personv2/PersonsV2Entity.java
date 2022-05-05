package com.fronzec.myservice.personv2;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "persons_v2")
public class PersonsV2Entity {

  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Id
  @Column(name = "id", nullable = false)
  private long id;

  @Basic
  @Column(name = "first_name", nullable = false, length = 50)
  private String firstName;

  @Basic
  @Column(name = "last_name", nullable = false, length = 50)
  private String lastName;

  @Basic
  @Column(name = "email", nullable = false, length = 50)
  private String email;

  @Basic
  @Column(name = "profession", nullable = false, length = 15)
  private String profession;

  @Basic
  @Column(name = "salary", nullable = false)
  private BigDecimal salary;

  @Basic
  @Column(name = "uuid_v4", nullable = false, length = 36)
  private String uuidV4;

  @Basic
  @Column(name = "fk_dispatched_group_id", nullable = true)
  private Long fkDispatchedGroupId;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
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


  public BigDecimal getSalary() {
    return salary;
  }

  public void setSalary(BigDecimal salary) {
    this.salary = salary;
  }

  public String getUuidV4() {
    return uuidV4;
  }

  public void setUuidV4(String uuidV4) {
    this.uuidV4 = uuidV4;
  }

  public Long getFkDispatchedGroupId() {
    return fkDispatchedGroupId;
  }

  public void setFkDispatchedGroupId(Long fkDispatchedGroupId) {
    this.fkDispatchedGroupId = fkDispatchedGroupId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    PersonsV2Entity that = (PersonsV2Entity) o;

    if (id != that.id) {
      return false;
    }
    if (firstName != null ? !firstName.equals(that.firstName) : that.firstName != null) {
      return false;
    }
    if (lastName != null ? !lastName.equals(that.lastName) : that.lastName != null) {
      return false;
    }
    if (email != null ? !email.equals(that.email) : that.email != null) {
      return false;
    }
    if (profession != null ? !profession.equals(that.profession) : that.profession != null) {
      return false;
    }
    if (uuidV4 != null ? !uuidV4.equals(that.uuidV4) : that.uuidV4 != null) {
      return false;
    }

    if (fkDispatchedGroupId != null ? !fkDispatchedGroupId.equals(that.fkDispatchedGroupId) : that.fkDispatchedGroupId != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = (int) (id ^ (id >>> 32));
    result = 31 * result + (firstName != null ? firstName.hashCode() : 0);
    result = 31 * result + (lastName != null ? lastName.hashCode() : 0);
    result = 31 * result + (email != null ? email.hashCode() : 0);
    result = 31 * result + (profession != null ? profession.hashCode() : 0);
    result = 31 * result + (uuidV4 != null ? uuidV4.hashCode() : 0);
    result = 31 * result + (fkDispatchedGroupId != null ? fkDispatchedGroupId.hashCode() : 0);
    return result;
  }
}
