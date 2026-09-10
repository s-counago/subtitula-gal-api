package gal.subtitula.api.transparency.search;

import gal.subtitula.api.transparency.capability.TransparencyCapabilities;
import gal.subtitula.api.transparency.search.dto.PublicSearchResponse;
import gal.subtitula.api.transparency.search.dto.PublicSearchResult;
import gal.subtitula.api.transparency.search.dto.SearchClickRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PublicSearchService {

    private static final Logger log = LoggerFactory.getLogger(PublicSearchService.class);
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static final Set<String> ALLOWED_KINDS = Set.of(
        "EVIDENCE", "TOPIC", "CONTRIBUTION", "DECISION", "DOCUMENT_CHUNK");
    private static final Set<String> QUESTION_WORDS = Set.of(
        "a", "ao", "aos", "as", "como", "cando", "cal", "cales", "con", "da",
        "das", "de", "do", "dos", "e", "en", "foi", "foron", "na", "nas", "no",
        "nos", "o", "os", "para", "pola", "polas", "polo", "polos", "por", "que",
        "quen", "se", "sobre", "un", "unha", "uns", "unhas", "onde",
        "al", "cuando", "cual", "cuales", "del", "el", "ella", "ellas",
        "ellos", "fue", "fueron", "la", "las", "lo", "los", "quien", "quienes",
        "una", "uno", "unos", "unas", "y", "donde",
        "acordouse", "aprobouse", "comentouse", "debateuse", "dixo", "discutiu",
        "falouse", "mencionouse", "tratouse", "dijo", "discutio",
        "hablo", "menciono", "trato");

    private static final String SEARCH_SQL = """
        with ranked as (
            select
                sd.id,
                sd.publication_id,
                sd.public_slug,
                sd.publication_version,
                sd.document_kind,
                sd.source_entity_id,
                sd.evidence_segment_id,
                sd.session_title,
                sd.organization_id,
                organization.name as organization_name,
                sd.session_body,
                sd.session_date,
                sd.display_title,
                sd.display_text,
                sd.speaker_id,
                sd.speaker_label,
                sd.agenda_item_id,
                sd.agenda_title,
                sd.start_ms,
                sd.end_ms,
                sd.document_url,
                strpos(sd.normalized_text, :normalizedQuery) > 0 as exact_phrase,
                strpos(lower(unaccent(sd.display_title)), :keywordPhrase) > 0
                    as title_match,
                strpos(lower(unaccent(coalesce(sd.speaker_label, ''))), :keywordPhrase) > 0
                    as speaker_match,
                strpos(lower(unaccent(coalesce(sd.agenda_title, ''))), :keywordPhrase) > 0
                    as agenda_match,
                sd.search_vector @@ to_tsquery('simple', cast(:tsQuery as text))
                    as full_text_match,
                greatest(
                    word_similarity(:keywordPhrase, sd.normalized_text),
                    similarity(lower(unaccent(sd.display_title)), :keywordPhrase),
                    similarity(lower(unaccent(coalesce(sd.speaker_label, ''))), :keywordPhrase),
                    similarity(lower(unaccent(coalesce(sd.agenda_title, ''))), :keywordPhrase)
                ) as fuzzy_score,
                (
                    case when strpos(sd.normalized_text, :normalizedQuery) > 0
                        then 8.0 else 0.0 end
                    + case when strpos(lower(unaccent(sd.display_title)), :keywordPhrase) > 0
                        then 3.5 else 0.0 end
                    + case when strpos(
                            lower(unaccent(coalesce(sd.speaker_label, ''))),
                            :keywordPhrase
                        ) > 0 then 2.5 else 0.0 end
                    + case when strpos(
                            lower(unaccent(coalesce(sd.agenda_title, ''))),
                            :keywordPhrase
                        ) > 0 then 2.5 else 0.0 end
                    + ts_rank_cd(
                        sd.search_vector,
                        to_tsquery('simple', cast(:tsQuery as text)),
                        32
                    ) * 5.0
                    + greatest(
                        word_similarity(:keywordPhrase, sd.normalized_text),
                        similarity(lower(unaccent(sd.display_title)), :keywordPhrase),
                        similarity(
                            lower(unaccent(coalesce(sd.speaker_label, ''))),
                            :keywordPhrase
                        ),
                        similarity(
                            lower(unaccent(coalesce(sd.agenda_title, ''))),
                            :keywordPhrase
                        )
                    ) * 3.0
                    + case sd.document_kind
                        when 'DECISION' then 0.8
                        when 'TOPIC' then 0.6
                        when 'EVIDENCE' then 0.5
                        when 'CONTRIBUTION' then 0.4
                        else 0.0
                      end
                ) as public_rank
            from search_documents sd
            join publications publication
              on publication.id = sd.publication_id
             and publication.publication_state = 'PUBLISHED'
            left join organizations organization on organization.id = sd.organization_id
            where sd.active = true
              and (cast(:publicSlug as text) is null or sd.public_slug = :publicSlug)
              and (cast(:organizationId as uuid) is null
                or sd.organization_id = :organizationId)
              and (cast(:sessionBody as text) is null
                or lower(unaccent(sd.session_body)) = :sessionBody)
              and (cast(:dateFrom as date) is null or sd.session_date >= :dateFrom)
              and (cast(:dateTo as date) is null or sd.session_date <= :dateTo)
              and (cast(:speakerId as uuid) is null or sd.speaker_id = :speakerId)
              and (cast(:agendaItemId as uuid) is null
                or sd.agenda_item_id = :agendaItemId)
              and (cast(:language as text) is null or sd.language_code = :language)
              and (cast(:kind as text) is null or sd.document_kind = :kind)
              and (
                    strpos(sd.normalized_text, :normalizedQuery) > 0
                 or sd.search_vector @@ to_tsquery('simple', cast(:tsQuery as text))
                 or word_similarity(:keywordPhrase, sd.normalized_text) >= :fuzzyThreshold
                 or lower(unaccent(sd.display_title)) % :keywordPhrase
                 or lower(unaccent(coalesce(sd.speaker_label, ''))) % :keywordPhrase
                 or lower(unaccent(coalesce(sd.agenda_title, ''))) % :keywordPhrase
              )
        )
        select ranked.*, count(*) over() as total_count
        from ranked
        order by public_rank desc, session_date desc nulls last,
                 start_ms asc nulls last, id
        limit :limit offset :offset
        """;

    private static final String SEMANTIC_SQL = """
        select
            sd.id,
            sd.publication_id,
            sd.public_slug,
            sd.publication_version,
            sd.document_kind,
            sd.source_entity_id,
            sd.evidence_segment_id,
            sd.session_title,
            sd.organization_id,
            organization.name as organization_name,
            sd.session_body,
            sd.session_date,
            sd.display_title,
            sd.display_text,
            sd.speaker_id,
            sd.speaker_label,
            sd.agenda_item_id,
            sd.agenda_title,
            sd.start_ms,
            sd.end_ms,
            sd.document_url,
            false as exact_phrase,
            false as title_match,
            false as speaker_match,
            false as agenda_match,
            false as full_text_match,
            0.0 as fuzzy_score,
            0::bigint as total_count
        from search_documents sd
        join publications publication
          on publication.id = sd.publication_id
         and publication.publication_state = 'PUBLISHED'
        left join organizations organization on organization.id = sd.organization_id
        where sd.active = true
          and sd.embedding is not null
          and sd.embedding_model = :embeddingModel
          and sd.embedded_content_hash = sd.content_hash
          and (1 - (sd.embedding <=> cast(:embedding as vector))) >= :minimumSimilarity
          and (cast(:publicSlug as text) is null or sd.public_slug = :publicSlug)
          and (cast(:organizationId as uuid) is null
            or sd.organization_id = :organizationId)
          and (cast(:sessionBody as text) is null
            or lower(unaccent(sd.session_body)) = :sessionBody)
          and (cast(:dateFrom as date) is null or sd.session_date >= :dateFrom)
          and (cast(:dateTo as date) is null or sd.session_date <= :dateTo)
          and (cast(:speakerId as uuid) is null or sd.speaker_id = :speakerId)
          and (cast(:agendaItemId as uuid) is null
            or sd.agenda_item_id = :agendaItemId)
          and (cast(:language as text) is null or sd.language_code = :language)
          and (cast(:kind as text) is null or sd.document_kind = :kind)
        order by sd.embedding <=> cast(:embedding as vector), sd.id
        limit :candidateLimit
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransparencyCapabilities capabilities;
    private final SearchAnalyticsService analytics;
    private final String embeddingModel;
    private final int embeddingDimensions;
    private final double minimumSemanticSimilarity;

    public PublicSearchService(
            NamedParameterJdbcTemplate jdbc,
            TransparencyCapabilities capabilities,
            SearchAnalyticsService analytics,
            @Value("${app.search.embedding-model:@cf/baai/bge-m3}")
            String embeddingModel,
            @Value("${app.search.embedding-dimensions:1024}")
            int embeddingDimensions,
            @Value("${app.search.minimum-semantic-similarity:0.32}")
            double minimumSemanticSimilarity) {
        this.jdbc = jdbc;
        this.capabilities = capabilities;
        this.analytics = analytics;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        if (!Double.isFinite(minimumSemanticSimilarity)
                || minimumSemanticSimilarity < 0 || minimumSemanticSimilarity > 1) {
            throw new IllegalArgumentException("Invalid minimum semantic similarity");
        }
        this.minimumSemanticSimilarity = minimumSemanticSimilarity;
    }

    @Transactional(readOnly = true)
    public PublicSearchResponse hybrid(
            PublicSearchQuery request,
            List<Double> embedding) {
        requireHybridCapability();
        PreparedQuery query = prepare(request);
        String vector = vector(embedding);
        Instant started = Instant.now();
        MapSqlParameterSource lexicalParameters = parameters(request, query)
            .addValue("limit", 100)
            .addValue("offset", 0);
        List<Row> lexical = jdbc.query(
            SEARCH_SQL,
            lexicalParameters,
            (result, rowNumber) -> Row.from(result));
        MapSqlParameterSource semanticParameters = parameters(request, query)
            .addValue("embedding", vector)
            .addValue("embeddingModel", embeddingModel)
            .addValue("minimumSimilarity", minimumSemanticSimilarity)
            .addValue("candidateLimit", 100);
        List<Row> semantic = jdbc.query(
            SEMANTIC_SQL,
            semanticParameters,
            (result, rowNumber) -> Row.from(result));

        Map<UUID, Row> rows = new HashMap<>();
        Map<UUID, Double> scores = new HashMap<>();
        Set<UUID> semanticMatches = new HashSet<>();
        for (int index = 0; index < lexical.size(); index++) {
            Row row = lexical.get(index);
            rows.put(row.searchDocumentId(), row);
            scores.merge(
                row.searchDocumentId(),
                reciprocalRank(index),
                Double::sum);
        }
        for (int index = 0; index < semantic.size(); index++) {
            Row row = semantic.get(index);
            rows.putIfAbsent(row.searchDocumentId(), row);
            scores.merge(
                row.searchDocumentId(),
                reciprocalRank(index),
                Double::sum);
            semanticMatches.add(row.searchDocumentId());
        }
        List<UUID> ranked = rows.keySet().stream()
            .sorted(Comparator
                .<UUID>comparingDouble(id -> scores.getOrDefault(id, 0.0))
                .reversed()
                .thenComparing(UUID::toString))
            .toList();
        int from = Math.min(query.offset(), ranked.size());
        int to = Math.min(ranked.size(), from + query.limit());
        List<PublicSearchResult> results = ranked.subList(from, to).stream()
            .map(id -> rows.get(id).response(semanticMatches.contains(id)))
            .toList();
        long latencyMs = Math.max(0, Duration.between(started, Instant.now()).toMillis());
        UUID queryEventId = null;
        try {
            queryEventId = analytics.recordQuery(
                request,
                query.original(),
                query.normalized(),
                query.kind(),
                "HYBRID",
                ranked.size(),
                latencyMs);
        } catch (RuntimeException failure) {
            log.warn("Could not store privacy-minimized search analytics", failure);
        }
        return new PublicSearchResponse(
            query.original(),
            "HYBRID",
            ranked.size(),
            query.limit(),
            query.offset(),
            queryEventId,
            results);
    }

    @Transactional(readOnly = true)
    public PublicSearchResponse search(PublicSearchQuery request) {
        requireCapability();
        PreparedQuery query = prepare(request);
        Instant started = Instant.now();
        List<Row> rows = jdbc.query(
            SEARCH_SQL,
            parameters(request, query),
            (result, rowNumber) -> Row.from(result));
        long latencyMs = Math.max(0, Duration.between(started, Instant.now()).toMillis());
        long total = rows.isEmpty() ? 0 : rows.get(0).total();
        List<PublicSearchResult> results = rows.stream()
            .map(Row::response)
            .toList();
        UUID queryEventId = null;
        try {
            queryEventId = analytics.recordQuery(
                request,
                query.original(),
                query.normalized(),
                query.kind(),
                "LEXICAL",
                total,
                latencyMs);
        } catch (RuntimeException failure) {
            log.warn("Could not store privacy-minimized search analytics", failure);
        }
        return new PublicSearchResponse(
            query.original(),
            "LEXICAL",
            total,
            query.limit(),
            query.offset(),
            queryEventId,
            results);
    }

    public void recordClick(SearchClickRequest request) {
        requireCapability();
        try {
            analytics.recordClick(request);
        } catch (RuntimeException failure) {
            log.warn("Could not store privacy-minimized search click", failure);
        }
    }

    private PreparedQuery prepare(PublicSearchQuery request) {
        if (request == null || request.query() == null) {
            throw new SearchRequestException("Search query is required");
        }
        String original = request.query().trim().replaceAll("\\s+", " ");
        if (original.length() < 2 || original.length() > 300) {
            throw new SearchRequestException(
                "Search query must contain between 2 and 300 characters");
        }
        if (request.limit() < 1 || request.limit() > 50) {
            throw new SearchRequestException("Search limit must be between 1 and 50");
        }
        if (request.offset() < 0 || request.offset() > 500) {
            throw new SearchRequestException("Search offset must be between 0 and 500");
        }
        if (request.dateFrom() != null && request.dateTo() != null
                && request.dateFrom().isAfter(request.dateTo())) {
            throw new SearchRequestException("Search date range is invalid");
        }
        String kind = normalizedKind(request.kind());
        String normalized = normalize(original);
        LinkedHashSet<String> allTokens = tokens(normalized);
        LinkedHashSet<String> keywords = new LinkedHashSet<>(allTokens);
        keywords.removeAll(QUESTION_WORDS);
        if (keywords.isEmpty()) {
            keywords = allTokens;
        }
        if (keywords.isEmpty()) {
            throw new SearchRequestException("Search query has no searchable terms");
        }
        List<String> bounded = keywords.stream().limit(24).toList();
        String keywordPhrase = String.join(" ", bounded);
        String tsQuery = bounded.stream()
            .map(token -> token + ":*")
            .collect(java.util.stream.Collectors.joining(" | "));
        return new PreparedQuery(
            original,
            normalized,
            keywordPhrase,
            tsQuery,
            kind,
            request.limit(),
            request.offset());
    }

    private MapSqlParameterSource parameters(
            PublicSearchQuery request,
            PreparedQuery query) {
        return new MapSqlParameterSource()
            .addValue("normalizedQuery", query.normalized())
            .addValue("keywordPhrase", query.keywordPhrase())
            .addValue("tsQuery", query.tsQuery())
            .addValue("fuzzyThreshold", query.keywordPhrase().length() <= 5 ? 0.55 : 0.42)
            .addValue("publicSlug", blankToNull(request.publicSlug()))
            .addValue("organizationId", request.organizationId())
            .addValue("sessionBody", normalizeNullable(request.sessionBody()))
            .addValue("dateFrom", request.dateFrom())
            .addValue("dateTo", request.dateTo())
            .addValue("speakerId", request.speakerId())
            .addValue("agendaItemId", request.agendaItemId())
            .addValue("language", normalizeLanguage(request.language()))
            .addValue("kind", query.kind())
            .addValue("limit", query.limit())
            .addValue("offset", query.offset());
    }

    private void requireCapability() {
        if (!capabilities.lexicalSearch()) {
            throw new SearchUnavailableException();
        }
    }

    private void requireHybridCapability() {
        if (!capabilities.lexicalSearch() || !capabilities.hybridSearch()) {
            throw new SearchUnavailableException();
        }
    }

    private static String normalizedKind(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_KINDS.contains(normalized)) {
            throw new SearchRequestException("Search document type is invalid");
        }
        return normalized;
    }

    private static String normalizeLanguage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 20
                || !normalized.matches("[a-z]{2,3}(?:-[a-z0-9]{2,8})?")) {
            throw new SearchRequestException("Search language is invalid");
        }
        return normalized;
    }

    private static LinkedHashSet<String> tokens(String normalized) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(normalized);
        while (matcher.find() && values.size() < 48) {
            values.add(matcher.group());
        }
        return values;
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : normalize(value.trim());
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String vector(List<Double> values) {
        if (values == null || values.size() != embeddingDimensions) {
            throw new SearchRequestException("Search embedding contract is invalid");
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (Double value : values) {
            if (value == null || !Double.isFinite(value) || Math.abs(value) > 1_000) {
                throw new SearchRequestException("Search embedding is invalid");
            }
            normalized.add(Double.toString(value));
        }
        return "[" + String.join(",", normalized) + "]";
    }

    private static double reciprocalRank(int zeroBasedRank) {
        return 1.0 / (60.0 + zeroBasedRank + 1.0);
    }

    private record PreparedQuery(
            String original,
            String normalized,
            String keywordPhrase,
            String tsQuery,
            String kind,
            int limit,
            int offset) {
    }

    private record Row(
            UUID searchDocumentId,
            UUID publicationId,
            String publicSlug,
            int publicationVersion,
            String kind,
            UUID sourceEntityId,
            UUID evidenceSegmentId,
            String sessionTitle,
            UUID organizationId,
            String organizationName,
            String sessionBody,
            LocalDate sessionDate,
            String title,
            String excerpt,
            UUID speakerId,
            String speakerLabel,
            UUID agendaItemId,
            String agendaTitle,
            Long startMs,
            Long endMs,
            String documentUrl,
            boolean exactPhrase,
            boolean titleMatch,
            boolean speakerMatch,
            boolean agendaMatch,
            boolean fullTextMatch,
            double fuzzyScore,
            long total) {

        private static Row from(java.sql.ResultSet result) throws java.sql.SQLException {
            return new Row(
                result.getObject("id", UUID.class),
                result.getObject("publication_id", UUID.class),
                result.getString("public_slug"),
                result.getInt("publication_version"),
                result.getString("document_kind"),
                result.getObject("source_entity_id", UUID.class),
                result.getObject("evidence_segment_id", UUID.class),
                result.getString("session_title"),
                result.getObject("organization_id", UUID.class),
                result.getString("organization_name"),
                result.getString("session_body"),
                result.getObject("session_date", LocalDate.class),
                result.getString("display_title"),
                result.getString("display_text"),
                result.getObject("speaker_id", UUID.class),
                result.getString("speaker_label"),
                result.getObject("agenda_item_id", UUID.class),
                result.getString("agenda_title"),
                nullableLong(result, "start_ms"),
                nullableLong(result, "end_ms"),
                result.getString("document_url"),
                result.getBoolean("exact_phrase"),
                result.getBoolean("title_match"),
                result.getBoolean("speaker_match"),
                result.getBoolean("agenda_match"),
                result.getBoolean("full_text_match"),
                result.getDouble("fuzzy_score"),
                result.getLong("total_count"));
        }

        private PublicSearchResult response() {
            return response(false);
        }

        private PublicSearchResult response(boolean relatedMeaning) {
            List<String> reasons = new ArrayList<>();
            if (exactPhrase) {
                reasons.add("EXACT_PHRASE");
            }
            if (titleMatch) {
                reasons.add("TITLE");
            }
            if (speakerMatch) {
                reasons.add("SPEAKER");
            }
            if (agendaMatch) {
                reasons.add("AGENDA");
            }
            if (fullTextMatch) {
                reasons.add("TERMS");
            }
            if (fuzzyScore >= 0.42 && !exactPhrase) {
                reasons.add("NEAR_MATCH");
            }
            if (relatedMeaning) {
                reasons.add("RELATED_MEANING");
            }
            String href = "/transparencia/" + publicSlug
                + (startMs == null ? "" : "?t=" + Math.max(0, startMs / 1_000))
                + (evidenceSegmentId == null ? "" :
                    (startMs == null ? "?" : "&") + "evidence=" + evidenceSegmentId);
            return new PublicSearchResult(
                searchDocumentId,
                publicationId,
                publicSlug,
                publicationVersion,
                kind,
                sourceEntityId,
                evidenceSegmentId,
                sessionTitle,
                organizationId,
                organizationName,
                sessionBody,
                sessionDate,
                title,
                excerpt,
                speakerId,
                speakerLabel,
                agendaItemId,
                agendaTitle,
                startMs,
                endMs,
                documentUrl,
                href,
                List.copyOf(reasons));
        }

        private static Long nullableLong(
                java.sql.ResultSet result,
                String column) throws java.sql.SQLException {
            long value = result.getLong(column);
            return result.wasNull() ? null : value;
        }
    }
}
