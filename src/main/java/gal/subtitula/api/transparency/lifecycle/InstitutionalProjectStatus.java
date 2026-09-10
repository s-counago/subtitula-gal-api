package gal.subtitula.api.transparency.lifecycle;

/**
 * Canonical institutional project lifecycle. The transition policy is explicit so HTTP
 * handlers and Workflow callbacks cannot invent out-of-order state changes.
 */
public enum InstitutionalProjectStatus {
    DRAFT,
    UPLOADING,
    UPLOADED,
    TRANSCRIBING,
    REVIEW_REQUIRED,
    ENRICHING,
    READY,
    PUBLISHED,
    ARCHIVED,
    PROCESSING_FAILED;

    public boolean canTransitionTo(InstitutionalProjectStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == UPLOADING;
            case UPLOADING -> target == UPLOADED || target == PROCESSING_FAILED;
            case UPLOADED -> target == TRANSCRIBING || target == PROCESSING_FAILED;
            case TRANSCRIBING -> target == REVIEW_REQUIRED || target == PROCESSING_FAILED;
            case REVIEW_REQUIRED -> target == ENRICHING;
            case ENRICHING -> target == READY || target == PROCESSING_FAILED;
            case READY -> target == PUBLISHED;
            case PUBLISHED -> target == REVIEW_REQUIRED || target == ARCHIVED;
            case PROCESSING_FAILED ->
                target == UPLOADING
                    || target == UPLOADED
                    || target == TRANSCRIBING
                    || target == ENRICHING
                    || target == READY;
            case ARCHIVED -> false;
        };
    }
}
