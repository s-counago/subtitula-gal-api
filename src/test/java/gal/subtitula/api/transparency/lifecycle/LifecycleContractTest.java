package gal.subtitula.api.transparency.lifecycle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LifecycleContractTest {

    @Test
    void projectHappyPathIsExplicit() {
        assertThat(InstitutionalProjectStatus.DRAFT.canTransitionTo(
            InstitutionalProjectStatus.UPLOADING)).isTrue();
        assertThat(InstitutionalProjectStatus.UPLOADING.canTransitionTo(
            InstitutionalProjectStatus.UPLOADED)).isTrue();
        assertThat(InstitutionalProjectStatus.UPLOADED.canTransitionTo(
            InstitutionalProjectStatus.TRANSCRIBING)).isTrue();
        assertThat(InstitutionalProjectStatus.TRANSCRIBING.canTransitionTo(
            InstitutionalProjectStatus.REVIEW_REQUIRED)).isTrue();
        assertThat(InstitutionalProjectStatus.REVIEW_REQUIRED.canTransitionTo(
            InstitutionalProjectStatus.ENRICHING)).isTrue();
        assertThat(InstitutionalProjectStatus.ENRICHING.canTransitionTo(
            InstitutionalProjectStatus.READY)).isTrue();
        assertThat(InstitutionalProjectStatus.READY.canTransitionTo(
            InstitutionalProjectStatus.PUBLISHED)).isTrue();
        assertThat(InstitutionalProjectStatus.PUBLISHED.canTransitionTo(
            InstitutionalProjectStatus.ARCHIVED)).isTrue();
    }

    @Test
    void projectCannotSkipTheRequiredTranscriptReview() {
        assertThat(InstitutionalProjectStatus.TRANSCRIBING.canTransitionTo(
            InstitutionalProjectStatus.ENRICHING)).isFalse();
        assertThat(InstitutionalProjectStatus.REVIEW_REQUIRED.canTransitionTo(
            InstitutionalProjectStatus.PUBLISHED)).isFalse();
        assertThat(InstitutionalProjectStatus.READY.canTransitionTo(
            InstitutionalProjectStatus.ARCHIVED)).isFalse();
    }

    @Test
    void retryableJobCanResumeButTerminalJobCannot() {
        assertThat(ProcessingJobState.RUNNING.canTransitionTo(
            ProcessingJobState.FAILED_RETRYABLE)).isTrue();
        assertThat(ProcessingJobState.FAILED_RETRYABLE.canTransitionTo(
            ProcessingJobState.QUEUED)).isTrue();
        assertThat(ProcessingJobState.FAILED_TERMINAL.canTransitionTo(
            ProcessingJobState.QUEUED)).isFalse();
        assertThat(ProcessingJobState.SUCCEEDED.canTransitionTo(
            ProcessingJobState.RUNNING)).isFalse();
    }

    @Test
    void errorRetryPolicyMatchesObservedProviderFailure() {
        assertThat(ProcessingErrorCode.PROVIDER_REJECTED.isRetryable()).isFalse();
        assertThat(ProcessingErrorCode.PROVIDER_RATE_LIMITED.isRetryable()).isTrue();
        assertThat(ProcessingErrorCode.R2_UNAVAILABLE.isRetryable()).isTrue();
        assertThat(ProcessingErrorCode.WEBHOOK_SIGNATURE_INVALID.isRetryable()).isFalse();
    }
}
