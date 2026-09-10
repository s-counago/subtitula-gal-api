package gal.subtitula.api.transparency.internal.dto;

import java.util.List;
import java.util.UUID;

public record EmbeddingBatchCommand(
        String workflowInstanceId,
        String modelVersion,
        int dimensions,
        List<Item> items) {

    public record Item(
            UUID searchDocumentId,
            String contentHash,
            List<Double> embedding) {
    }
}
