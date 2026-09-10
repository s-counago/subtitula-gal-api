package gal.subtitula.api.transparency.internal;

import gal.subtitula.api.transparency.internal.dto.SearchAnalyticsCleanupCommand;
import gal.subtitula.api.transparency.internal.dto.SearchAnalyticsCleanupResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/processing/cleanup")
public class InternalMaintenanceController {

    private final InternalMaintenanceService maintenance;

    public InternalMaintenanceController(InternalMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @PostMapping("/search-analytics")
    public SearchAnalyticsCleanupResponse cleanupSearchAnalytics(
            @RequestBody SearchAnalyticsCleanupCommand command) {
        return maintenance.cleanupSearchAnalytics(command);
    }
}
