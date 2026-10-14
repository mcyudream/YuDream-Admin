package online.yudream.base.infra.platform.ai.mapper;

import online.yudream.base.domain.platform.ai.valobj.PublicAiRunGrant;
import online.yudream.base.infra.platform.ai.dataobj.PublicAiRunGrantDO;

public final class PublicAiRunGrantInfraMapper {
    private PublicAiRunGrantInfraMapper() { }
    public static PublicAiRunGrantDO toData(PublicAiRunGrant grant) {
        var data = new PublicAiRunGrantDO();
        data.setRunId(grant.runId()); data.setTokenHash(grant.tokenHash());
        data.setSessionId(grant.sessionId()); data.setSpaceSlug(grant.spaceSlug()); data.setExpiresAt(grant.expiresAt());
        return data;
    }
    public static PublicAiRunGrant toDomain(PublicAiRunGrantDO data) {
        return new PublicAiRunGrant(data.getRunId(), data.getTokenHash(), data.getSessionId(), data.getSpaceSlug(), data.getExpiresAt());
    }
}
