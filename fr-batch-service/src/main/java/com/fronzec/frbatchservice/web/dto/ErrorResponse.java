/* 2024-2025 */
package com.fronzec.frbatchservice.web.dto;

import java.time.LocalDateTime;

/**
 * Structured error envelope for HTTP error responses.
 *
 * <p>Returned by {@code JobManagementController} validation handlers and later by
 * {@code GlobalExceptionHandler} (PR 5).
 */
public record ErrorResponse(int status, String error, String message, LocalDateTime timestamp, String path) {}
