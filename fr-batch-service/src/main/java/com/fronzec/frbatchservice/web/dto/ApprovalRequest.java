/* 2024-2026 */
package com.fronzec.frbatchservice.web.dto;

/**
 * Request body for approve/reject endpoints.
 *
 * <p>Carries the identifier of the user performing the approval action.
 * Used by {@code PUT /jobs/definitions/{id}/approve} and
 * {@code PUT /jobs/definitions/{id}/reject}.
 */
public record ApprovalRequest(String approvedBy) {}
