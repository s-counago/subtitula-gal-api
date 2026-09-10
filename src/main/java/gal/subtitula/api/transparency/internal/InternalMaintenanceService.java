package gal.subtitula.api.transparency.internal;

import gal.subtitula.api.transparency.TransparencyStateConflictException;
import gal.subtitula.api.transparency.internal.dto.SearchAnalyticsCleanupCommand;
import gal.subtitula.api.transparency.internal.dto.SearchAnalyticsCleanupResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Service
public class InternalMaintenanceService {

    private final NamedParameterJdbcTemplate jdbc;

    public InternalMaintenanceService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public SearchAnalyticsCleanupResponse cleanupSearchAnalytics(
            SearchAnalyticsCleanupCommand command) {
        Instant now = Instant.now();
        if (command == null
                || command.createdBefore() == null
                || command.createdBefore().isAfter(now.minusSeconds(86_400))) {
            throw new TransparencyStateConflictException(
                "Search analytics cleanup cutoff is invalid");
        }
        int deleted = jdbc.update("""
            delete from search_query_events
            where created_at < :createdBefore
            """, new MapSqlParameterSource(
                "createdBefore",
                Timestamp.from(command.createdBefore())));
        return new SearchAnalyticsCleanupResponse(
            command.createdBefore(),
            deleted);
    }
}
