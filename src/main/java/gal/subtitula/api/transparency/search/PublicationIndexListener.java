package gal.subtitula.api.transparency.search;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PublicationIndexListener {

    private static final Logger log =
        LoggerFactory.getLogger(PublicationIndexListener.class);

    private final SearchIndexService index;

    public PublicationIndexListener(SearchIndexService index) {
        this.index = index;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicationReady(PublicationReadyForIndexEvent event) {
        try {
            index.index(event.publicationId(), event.processingJobId());
        } catch (RuntimeException failure) {
            log.error(
                "Lexical indexing failed for publication {} and job {}",
                event.publicationId(),
                event.processingJobId(),
                failure);
            try {
                index.fail(event.processingJobId());
            } catch (RuntimeException statusFailure) {
                log.error(
                    "Could not persist failed lexical index status for job {}",
                    event.processingJobId(),
                    statusFailure);
            }
        }
    }
}
