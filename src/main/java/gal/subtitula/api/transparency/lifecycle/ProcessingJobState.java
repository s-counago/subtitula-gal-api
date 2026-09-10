package gal.subtitula.api.transparency.lifecycle;

public enum ProcessingJobState {
    QUEUED,
    RUNNING,
    WAITING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
    CANCELLED;

    public boolean canTransitionTo(ProcessingJobState target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case QUEUED -> target == RUNNING || target == CANCELLED;
            case RUNNING ->
                target == WAITING
                    || target == SUCCEEDED
                    || target == FAILED_RETRYABLE
                    || target == FAILED_TERMINAL
                    || target == CANCELLED;
            case WAITING ->
                target == RUNNING
                    || target == FAILED_RETRYABLE
                    || target == FAILED_TERMINAL
                    || target == CANCELLED;
            case FAILED_RETRYABLE -> target == QUEUED || target == RUNNING || target == CANCELLED;
            case SUCCEEDED, FAILED_TERMINAL, CANCELLED -> false;
        };
    }
}
