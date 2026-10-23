package online.yudream.base.infra.system.security.mapper;

import online.yudream.base.infra.system.security.dataobj.ApiSecurityPolicyDO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开放注册开关的持久化兼容：存量策略文档没有 registrationEnabled 字段（反序列化为 null），
 * 必须按「开放注册」处理，不能把升级后的存量数据当成已关闭。
 */
class ApiSecurityPolicyInfraMapperTest {

    /** 聚合对令牌 TTL 有非零校验，先按存量真实数据填好基础字段。 */
    private static ApiSecurityPolicyDO legacyPolicyDo() {
        ApiSecurityPolicyDO dataObj = new ApiSecurityPolicyDO();
        dataObj.setCode("default");
        dataObj.setAccessTokenTtlSeconds(7200);
        dataObj.setRefreshTokenTtlSeconds(604800);
        dataObj.setRefreshRotationEnabled(true);
        return dataObj;
    }

    @Test
    void legacyDocumentWithoutFieldDefaultsToOpenRegistration() {
        ApiSecurityPolicyDO dataObj = legacyPolicyDo();
        assertNull(dataObj.getRegistrationEnabled());
        assertTrue(ApiSecurityPolicyInfraMapper.toDomain(dataObj).isRegistrationEnabled());
    }

    @Test
    void explicitClosedFieldStaysClosed() {
        ApiSecurityPolicyDO dataObj = legacyPolicyDo();
        dataObj.setRegistrationEnabled(false);
        assertFalse(ApiSecurityPolicyInfraMapper.toDomain(dataObj).isRegistrationEnabled());

        dataObj.setRegistrationEnabled(true);
        assertTrue(ApiSecurityPolicyInfraMapper.toDomain(dataObj).isRegistrationEnabled());
    }

    @Test
    void domainValueRoundTripsToDataObj() {
        ApiSecurityPolicyDO dataObj = ApiSecurityPolicyInfraMapper.toDataObj(
                ApiSecurityPolicyInfraMapper.toDomain(legacyPolicyDo()));
        assertTrue(dataObj.getRegistrationEnabled());
    }
}
