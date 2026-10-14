package online.yudream.base.domain.platform.ai.repo;

import online.yudream.base.domain.platform.ai.valobj.PublicAiRunGrant;
import java.time.Instant;
import java.util.Optional;

public interface PublicAiRunGrantRepo {
    void save(PublicAiRunGrant grant);
    Optional<PublicAiRunGrant> find(String runId, String tokenHash, String spaceSlug, Instant now);
    Optional<PublicAiRunGrant> conversation(String tokenHash, String spaceSlug, Instant now);
}
