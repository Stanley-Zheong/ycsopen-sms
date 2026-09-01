package com.ycsopen.sms.core.common.security.key;

/**
 * Internal, monotonic admission seam used by key-protection adapters.
 *
 * <p>A successful reservation is permanently consumed. There is deliberately
 * no release, decrement, reset, or reuse operation.</p>
 */
@FunctionalInterface
interface WrapOperationAdmissionPort {

    long reserve(String canonicalKeyReference);
}
