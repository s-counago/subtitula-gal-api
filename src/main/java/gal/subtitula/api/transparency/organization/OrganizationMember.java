package gal.subtitula.api.transparency.organization;

import gal.subtitula.api.transparency.model.OrganizationMemberState;
import gal.subtitula.api.transparency.model.OrganizationRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_members")
public class OrganizationMember {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_state", nullable = false)
    private OrganizationMemberState state;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected OrganizationMember() {
    }

    public static OrganizationMember create(
            UUID organizationId,
            UUID userId,
            OrganizationRole role) {
        var member = new OrganizationMember();
        member.id = UUID.randomUUID();
        member.organizationId = organizationId;
        member.userId = userId;
        member.role = role;
        member.state = OrganizationMemberState.ACTIVE;
        member.createdAt = Instant.now();
        member.updatedAt = member.createdAt;
        return member;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrganizationRole getRole() {
        return role;
    }

    public OrganizationMemberState getState() {
        return state;
    }

    public long getVersion() {
        return version;
    }
}
