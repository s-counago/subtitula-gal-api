package gal.subtitula.api.transparency.internal.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record EmbeddingCompleteCommand(
        String workflowInstanceId,
        String modelVersion,
        JsonNode providerUsage,
        Long costMicrounits,
        String costCurrency) {
}
