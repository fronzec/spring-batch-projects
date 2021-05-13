package com.fronzec.batch

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/persons")
class PersonsController(private val personRepository: PersonRepository) {

    @GetMapping("/{id}")
    fun getById(@PathVariable(value = "id") id: Long): ResponseEntity<Person> {
        return personRepository.findById(id).map { article ->
            ResponseEntity.ok(article)
        }.orElse(ResponseEntity.notFound().build())
    }
}