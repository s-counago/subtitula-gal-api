package gal.subtitula.api.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update VerificationToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsedIfUnused(@Param("id") UUID id, @Param("now") Instant now);
}
