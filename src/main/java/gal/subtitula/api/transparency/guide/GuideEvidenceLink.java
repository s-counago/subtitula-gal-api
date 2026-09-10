package gal.subtitula.api.transparency.guide;

import gal.subtitula.api.transparency.model.GuideEvidenceSubjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guide_evidence_links")
public class GuideEvidenceLink {

    @Id
    private UUID id;

    @Column(name = "guide_id", nullable = false, updatable = false)
    private UUID guideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, updatable = false)
    private GuideEvidenceSubjectType subjectType;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "evidence_segment_id", nullable = false, updatable = false)
    private UUID evidenceSegmentId;

    @Column(name = "link_purpose", nullable = false, updatable = false)
    private String purpose;

    @Column(nullable = false, updatable = false)
    private int ordinal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GuideEvidenceLink() {
    }

    public static GuideEvidenceLink create(
            UUID guideId,
            GuideEvidenceSubjectType subjectType,
            UUID subjectId,
            UUID segmentId,
            String purpose,
            int ordinal) {
        GuideEvidenceLink link = new GuideEvidenceLink();
        link.id = UUID.randomUUID();
        link.guideId = guideId;
        link.subjectType = subjectType;
        link.subjectId = subjectId;
        link.evidenceSegmentId = segmentId;
        link.purpose = purpose;
        link.ordinal = ordinal;
        link.createdAt = Instant.now();
        return link;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuideId() {
        return guideId;
    }

    public GuideEvidenceSubjectType getSubjectType() {
        return subjectType;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getEvidenceSegmentId() {
        return evidenceSegmentId;
    }

    public String getPurpose() {
        return purpose;
    }

    public int getOrdinal() {
        return ordinal;
    }
}
