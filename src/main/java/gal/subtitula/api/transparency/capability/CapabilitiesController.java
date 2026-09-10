package gal.subtitula.api.transparency.capability;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CapabilitiesController {

    private final TransparencyCapabilities capabilities;

    public CapabilitiesController(TransparencyCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    @GetMapping("/capabilities")
    public CapabilitiesResponse get() {
        return CapabilitiesResponse.from(capabilities);
    }
}
