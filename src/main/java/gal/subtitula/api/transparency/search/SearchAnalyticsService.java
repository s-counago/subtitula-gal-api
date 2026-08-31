package gal.subtitula.api.transparency.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gal.subtitula.api.transparency.search.dto.SearchClickRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchAnalyticsService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String analyticsSecret;
    private final long embeddingMicrounitsPerMillionTokens;
    private final double estimatedCharsPerToken;

    public SearchAnalyticsService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper,
            @Value("${app.search.analytics-hmac-secret:}") String analyticsSecret,
            @Value("${app.search.embedding-microusd-per-million-input-tokens:12000}")
            long embeddingMicrounitsPerMillionTokens,
            @Value("${app.search.estimated-chars-per-token:3}")
            double estimatedCharsPerToken) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.analyticsSecret = analyticsSecret == null ? "" : analyticsSecret.trim();
        this.embeddingMicrounitsPerMillionTokens =
            Math.max(0, embeddingMicrounitsPerMillionTokens);
        this.estimatedCharsPerToken =
            estimatedCharsPerToken > 0 ? estimatedCharsPerToken : 3;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordQuery(
            PublicSearchQuery request,
            String originalQuery,
            String normalizedQuery,
            String normalizedKind,
            String mode,
            long resultCount,
            long latencyMs) {
        if (analyticsSecret.isBlank()) {
            return null;
        }
        UUID id = UUID.randomUUID();
        Map<String, Object> filters = new LinkedHashMap<>();
        put(filters, "session", request.publicSlug());
        put(filters, "organizationId", request.organizationId());
        put(filters, "body", request.sessionBody());
        put(filters, "dateFrom", request.dateFrom());
        put(filters, "dateTo", request.dateTo());
        put(filters, "speakerId", request.speakerId());
        put(filters, "agendaItemId", request.agendaItemId());
        put(filters, "language", request.language());
        put(filters, "kind", normalizedKind);
        try {
            long estimatedCost = "HYBRID".equals(mode)
                    && embeddingMicrounitsPerMillionTokens > 0
                ? Math.max(1, (long) Math.ceil(
                    (normalizedQuery.length() / estimatedCharsPerToken)
                        * embeddingMicrounitsPerMillionTokens
                        / 1_000_000.0))
                : 0;
            jdbc.update("""
                insert into search_query_events (
                    id, query_hmac, query_length, result_count, latency_ms,
                    search_mode, filters, estimated_ai_cost_microunits,
                    cost_currency
                ) values (
                    :id, :queryHmac, :queryLength, :resultCount, :latencyMs,
                    :mode, cast(:filters as jsonb), :estimatedCost, 'USD'
                )
                """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("queryHmac", hmac(normalizedQuery))
                    .addValue("queryLength", originalQuery.length())
                    .addValue("resultCount", resultCount)
                    .addValue("latencyMs", latencyMs)
                    .addValue("mode", mode)
                    .addValue("estimatedCost", estimatedCost)
                    .addValue("filters", mapper.writeValueAsString(filters)));
            return id;
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Search filter metadata could not be encoded", impossible);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordClick(SearchClickRequest request) {
        if (analyticsSecret.isBlank()) {
            return;
        }
        jdbc.update("""
            insert into search_click_events (
                id, query_event_id, search_document_id, result_rank
            )
            select :id, event.id, document.id, :rank
            from search_query_events event
            join search_documents document
              on document.id = :documentId
             and document.active = true
            join publications publication
              on publication.id = document.publication_id
             and publication.publication_state = 'PUBLISHED'
            where event.id = :eventId
            """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("eventId", request.queryEventId())
                .addValue("documentId", request.searchDocumentId())
                .addValue("rank", request.resultRank()));
    }

    private String hmac(String normalizedQuery) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                analyticsSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
            return HexFormat.of().formatHex(
                mac.doFinal(normalizedQuery.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC is unavailable", impossible);
        }
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof String text) || !text.isBlank())) {
            target.put(key, value);
        }
    }
}
