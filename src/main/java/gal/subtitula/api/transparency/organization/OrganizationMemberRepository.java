package gal.subtitula.api.transparency.organization;

import gal.subtitula.api.transparency.model.OrganizationMemberState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {
    Optional<OrganizationMember> findByOrganizationIdAndUserIdAndState(
        UUID organizationId,
        UUID userId,
        OrganizationMemberState state);
}
