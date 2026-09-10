package gal.subtitula.api.transparency.review.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReviewQueueResponse(
        UUID projectId,
        UUID revisionId,
        long revisionVersion,
        long projectVersion,
        int openRequired,
        int openWarnings,
        int resolved,
        int dismissed,
        Map<String, Integer> openByType,
        List<ReviewIssueResponse> issues) {
}
