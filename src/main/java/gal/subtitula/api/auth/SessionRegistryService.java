package gal.subtitula.api.auth;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class SessionRegistryService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRegistryService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** Deletes every persisted session whose principal name matches the given key. */
    public void invalidateAllForPrincipal(String principalName) {
        Map<String, ? extends Session> found =
            sessions.findByPrincipalName(principalName);
        found.keySet().forEach(sessions::deleteById);
    }
}
