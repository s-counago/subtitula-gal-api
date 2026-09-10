package gal.subtitula.api.transparency.internal;

import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.internal.dto.EmbeddingBatchCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingCompleteCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingFailureCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingIndexContextResponse;
import gal.subtitula.api.transparency.internal.dto.EmbeddingStartCommand;
import gal.subtitula.api.transparency.internal.dto.EmbeddingStartResponse;
import gal.subtitula.api.transparency.internal.dto.LexicalIndexResponse;
import gal.subtitula.api.transparency.internal.dto.LexicalWorkflowCommand;
import gal.subtitula.api.transparency.search.SearchIndexService;
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
    private final SearchIndexService lexicalIndexes;
    private final TransparencyCapabilities capabilities;

    public InternalSearchIndexController(
            InternalSearchIndexService indexes,
            SearchIndexService lexicalIndexes,
            TransparencyCapabilities capabilities) {
        this.indexes = indexes;
        this.lexicalIndexes = lexicalIndexes;
        this.capabilities = capabilities;
    }

    @PostMapping("/jobs/{jobId}/publications/{publicationId}/lexical-workflow")
    public LexicalIndexResponse prepareLexicalWorkflow(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId,
            @RequestBody LexicalWorkflowCommand command) {
        capabilities.requireLexicalSearch();
        return lexicalIndexes.prepare(
            publicationId,
            jobId,
            command.projectId(),
            command.workflowInstanceId());
    }

    @PostMapping("/jobs/{jobId}/publications/{publicationId}/lexical-index")
    public LexicalIndexResponse buildLexicalIndex(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId,
            @RequestBody LexicalWorkflowCommand command) {
        capabilities.requireLexicalSearch();
        return lexicalIndexes.index(
            publicationId,
            jobId,
            command.projectId(),
            command.workflowInstanceId());
    }

    @PostMapping("/jobs/{jobId}/lexical-index-failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void failLexicalIndex(@PathVariable UUID jobId) {
        lexicalIndexes.fail(jobId);
    }

    @GetMapping("/jobs/{jobId}/publications/{publicationId}/embedding-context")
    public EmbeddingIndexContextResponse context(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId) {
        capabilities.requireHybridSearch();
        return indexes.context(jobId, publicationId);
    }

    @PostMapping("/jobs/{jobId}/publications/{publicationId}/embedding-start")
    public EmbeddingStartResponse start(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId,
            @RequestBody EmbeddingStartCommand command) {
        capabilities.requireHybridSearch();
        return indexes.start(jobId, publicationId, command);
    }

    @PostMapping("/jobs/{jobId}/publications/{publicationId}/embeddings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void batch(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId,
            @RequestBody EmbeddingBatchCommand command) {
        capabilities.requireHybridSearch();
        indexes.ingestBatch(jobId, publicationId, command);
    }

    @PostMapping("/jobs/{jobId}/publications/{publicationId}/embedding-complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId,
            @RequestBody EmbeddingCompleteCommand command) {
        capabilities.requireHybridSearch();
        indexes.complete(jobId, publicationId, command);
    }

    @PostMapping("/jobs/{jobId}/publications/{publicationId}/embedding-failed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fail(
            @PathVariable UUID jobId,
            @PathVariable UUID publicationId,
            @RequestBody EmbeddingFailureCommand command) {
        capabilities.requireHybridSearch();
        indexes.fail(jobId, publicationId, command);
    }
}
