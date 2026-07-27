package gal.subtitula.api.project;

/** An approved session's content is fixed; changing it needs a new version. */
public class ProjectApprovedException extends RuntimeException {
    public ProjectApprovedException() { super("project already approved"); }
}
