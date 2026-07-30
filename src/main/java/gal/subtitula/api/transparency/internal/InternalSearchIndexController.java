package gal.subtitula.api.transparency.internal;

import gal.subtitula.api.transparency.internal.dto.EmbeddingBatchCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingCompleteCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingFailureCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingIndexContextResponse;
import gal.subtitula.api.transparency.internal.dto.EmbeddingStartCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingStartResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/processing")
public class InternalSearchIndexController {

    private final InternalSearchIndexService indexes;

    public InternalSearchIndexController(InternalSearchIndexService indexes) {
        this.indexes = indexes;
    }

    @GetMapping("/jobs/{jobId}/publications/{publicationId}/embedding-context")
    public EmbeddingIndexContextResponse context(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId) {
        return indexes.context(jobId, publicationId);
    }

    @PostMapping("/jobs/{jobId}/publications/{publicationId}/embedding-start")
    public EmbeddingStartResponse start(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId,
            @RequestBody EmbeddingStartCommand command) {
        return indexes.start(jobId, publicationId, command);
    }

    @PostMapping("/jobs/{jobId}/publications/{publicationId}/embeddings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void batch(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId,
            @RequestBody EmbeddingBatchCommand command) {
        indexes.ingestBatch(jobId, publicationId, command);
    }

    @PostMapping("/jobs/{jobId}/publications/{publicationId}/embedding-complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId,
            @RequestBody EmbeddingCompleteCommand command) {
        indexes.complete(jobId, publicationId, command);
    }

    @PostMapping("/jobs/{jobId}/publications/{publicationId}/embedding-failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fail(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId,
            @RequestBody EmbeddingFailureCommand command) {
        indexes.fail(jobId, publicationId, command);
    }
}
