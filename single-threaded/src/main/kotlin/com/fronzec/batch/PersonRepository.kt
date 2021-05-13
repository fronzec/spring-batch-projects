package com.fronzec.batch

import org.springframework.data.jpa.repository.JpaRepository

interface PersonRepository : JpaRepository<Person, Long> {
    fun getByAddressStreet(street: String): Person?
}