package com.acme.apigatewaycafelab.client;

/**
 * Subconjunto del ProfileResource del iam-service (campo {@code id}).
 */
public record ProfileResponse(Long id, String email) {
}
