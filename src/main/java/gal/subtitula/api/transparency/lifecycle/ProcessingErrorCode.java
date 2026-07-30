package gal.subtitula.api.transparency.lifecycle;

/**
 * Stable, safe classifications. Raw provider/storage errors remain in restricted
 * telemetry and are never returned to a browser.
 */
public enum ProcessingErrorCode {
    UPLOAD_INTENT_EXPIRED(false),
    UPLOAD_MISSING(false),
    UPLOAD_SIZE_MISMATCH(false),
    UPLOAD_CHECKSUM_MISMATCH(false),
    UPLOAD_TYPE_UNSUPPORTED(false),
    PROVIDER_UNAVAILABLE(true),
    PROVIDER_RATE_LIMITED(true),
    PROVIDER_TIMEOUT(true),
    PROVIDER_REJECTED(false),
    PROVIDER_PAYLOAD_INVALID(true),
    WEBHOOK_SIGNATURE_INVALID(false),
    WEBHOOK_REPLAYED(false),
    R2_UNAVAILABLE(true),
    INTERNAL_API_UNAVAILABLE(true),
    INTERNAL_SIGNATURE_INVALID(false),
    TRANSCRIPT_INVALID(false),
    GUIDE_GENERATION_FAILED(true),
    GUIDE_INVALID(true),
    EMBEDDING_UNAVAILABLE(true),
    INDEX_FAILED(true),
    UNKNOWN(true);

    private final boolean retryable;

    ProcessingErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
