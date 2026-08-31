package gal.subtitula.api.transparency.capability;

/** Returned as 404 so disabled rollout slices are not reachable as dark APIs. */
public final class CapabilityDisabledException extends RuntimeException {

    private final String capability;

    public CapabilityDisabledException(String capability) {
        super("Capability is disabled: " + capability);
        this.capability = capability;
    }

    public String capability() {
        return capability;
    }
}
