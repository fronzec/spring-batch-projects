/* (C)2024 */
package com.fronzec.frbatchservice.person;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import java.util.Objects;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "persons")
public class PersonsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", nullable = false)
    private long id;

    @Basic
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Basic
    @Column(name = "last_name", length = 50)
    private String lastName;

    @Basic
    @Column(name = "profession", nullable = false, length = 30)
    private String profession;

    @Basic
    @Column(name = "email", nullable = false, length = 50)
    private String email;

    @Basic
    @Column(name = "processed", nullable = false)
    private boolean processed;

    @Basic
    @Column(name = "created_at", nullable = false)
    private Timestamp createdAt;

    @Basic
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

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

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PersonsEntity that = (PersonsEntity) o;

        if (id != that.id) {
            return false;
        }
        if (processed != that.processed) {
            return false;
        }
        if (firstName != null ? !firstName.equals(that.firstName) : that.firstName != null) {
            return false;
        }
        if (lastName != null ? !lastName.equals(that.lastName) : that.lastName != null) {
            return false;
        }
        if (profession != null ? !profession.equals(that.profession) : that.profession != null) {
            return false;
        }
        if (email != null ? !email.equals(that.email) : that.email != null) {
            return false;
        }
        if (createdAt != null ? !createdAt.equals(that.createdAt) : that.createdAt != null) {
            return false;
        }
        return updatedAt != null ? updatedAt.equals(that.updatedAt) : that.updatedAt == null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id, firstName, lastName, profession, email, processed, createdAt, updatedAt);
    }
}
