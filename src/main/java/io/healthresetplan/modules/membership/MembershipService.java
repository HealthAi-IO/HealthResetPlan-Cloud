package io.healthresetplan.modules.membership;

import org.springframework.stereotype.Service;

/**
 * Compatibility boundary for features that require an authenticated account.
 * All logged-in users have access; this class deliberately contains no billing state.
 */
@Service
public class MembershipService {

    public boolean hasFeature(String userId, String feature) {
        return userId != null && !userId.isBlank();
    }
}
