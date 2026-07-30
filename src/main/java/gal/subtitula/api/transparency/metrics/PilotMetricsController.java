package gal.subtitula.api.transparency.metrics;

import gal.subtitula.api.auth.AuthPrincipal;
import gal.subtitula.api.transparency.metrics.dto.PilotMetricsResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/pilot-metrics")
public class PilotMetricsController {

    private final PilotMetricsService metrics;

    public PilotMetricsController(PilotMetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping
    public PilotMetricsResponse get(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID projectId) {
        return metrics.get(projectId, principal.userId());
    }
}
