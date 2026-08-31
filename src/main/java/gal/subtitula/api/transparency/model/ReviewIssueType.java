package gal.subtitula.api.transparency.model;

public enum ReviewIssueType {
    UNKNOWN_SPEAKER,
    SPEAKER_CHANGE,
    LOW_CONFIDENCE_SPAN,
    PROBABLE_PROPER_NAME,
    GLOSSARY_MISMATCH,
    OVERLAP_OR_NOISE,
    MISSING_TIMING,
    INVALID_SEGMENT
}
