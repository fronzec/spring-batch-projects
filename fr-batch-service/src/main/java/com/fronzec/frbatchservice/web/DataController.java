/* 2024 */
package com.fronzec.frbatchservice.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

/**
 * Controller for data management operations. Available only in local development environment.
 */
@RestController
@RequestMapping("/data")
@Profile("!production")
public class DataController {

    private static final Logger logger = LoggerFactory.getLogger(DataController.class);

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Resets data by truncating the persons, persons_v2, and dispatched_group tables.
     * This endpoint is only available in local development environment.
     *
     * @param body Empty request body (ignored)
     * @return 200 OK with empty body on success, 503 Service Unavailable with empty body on error
     */
    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Void> resetData(@RequestBody(required = false) Object body) {
        try {
            logger.info("Starting data reset operation");

            // Disable foreign key checks temporarily to allow truncate in any order
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();

            // Truncate tables in order: persons_v2 (has FK), dispatched_group, persons
            entityManager.createNativeQuery("TRUNCATE TABLE persons_v2").executeUpdate();
            logger.info("Truncated table persons_v2");

            entityManager.createNativeQuery("TRUNCATE TABLE dispatched_group").executeUpdate();
            logger.info("Truncated table dispatched_group");

            entityManager.createNativeQuery("TRUNCATE TABLE persons").executeUpdate();
            logger.info("Truncated table persons");

            // Re-enable foreign key checks
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();

            logger.info("Data reset operation completed successfully");
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            logger.error("Error during data reset operation", e);
            try {
                // Ensure foreign key checks are re-enabled even on error
                entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
            } catch (Exception ex) {
                logger.error("Error re-enabling foreign key checks", ex);
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
