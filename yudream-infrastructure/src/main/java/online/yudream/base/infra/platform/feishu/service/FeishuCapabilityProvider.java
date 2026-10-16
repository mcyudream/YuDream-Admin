package online.yudream.base.infra.platform.feishu.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 飞书开放平台能力提供器。
 * <p>
 * 项目闸门：{@code yudream.platform.capabilities.feishu.enabled}；应用闸门由应用层每用例检查持久化状态。
 * enable 只保存配置、解密 App Secret 并重配客户端（不发网络请求）；连接与 tenant_access_token
 * 只在真实业务调用或显式能力测试时建立；disable 关闭客户端并清空令牌缓存。
 */
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.feishu", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FeishuCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "feishu";

    private final FeishuOpenApiClient client;
    private final CapabilityCredentialCipher credentialCipher;

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private volatile String credentialError;

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "飞书开放平台",
                CapabilityType.INTEGRATION,
                "飞书应用凭证（tenant_access_token）与云文档接口：云空间文件、知识库空间、新版文档读取与块级编辑；按用户分钟/日额度限流",
                "i-ri:cloud-line",
                62,
                Map.of(
                        FeishuOpenApiClient.BASE_URL_CONFIG_KEY, FeishuOpenApiClient.DEFAULT_BASE_URL,
                        FeishuOpenApiClient.APP_ID_CONFIG_KEY, "",
                        FeishuOpenApiClient.APP_SECRET_CONFIG_KEY, "",
                        FeishuOpenApiClient.CONNECT_TIMEOUT_CONFIG_KEY, "5000",
                        FeishuOpenApiClient.READ_TIMEOUT_CONFIG_KEY, "15000",
                        "defaultPerMinuteLimit", "20",
                        "defaultDailyLimit", "500"
                )
        );
    }

    @Override
    public CapabilityHealth health() {
        if (credentialError != null) {
            return CapabilityHealth.error(credentialError);
        }
        if (!enabled.get()) {
            return CapabilityHealth.disabled("飞书开放平台能力未启用");
        }
        return CapabilityHealth.enabled("飞书开放平台能力已启用", Map.of(
                "token", "lazy",
                "modules", "cloud-doc"));
    }

    @Override
    public void enable(Map<String, String> config) {
        enabled.set(false);
        Map<String, String> runtime = config == null ? Map.of() : new HashMap<>(config);
        String secret = runtime.get(FeishuOpenApiClient.APP_SECRET_CONFIG_KEY);
        if (credentialCipher.encrypted(secret)) {
            try {
                runtime.put(FeishuOpenApiClient.APP_SECRET_CONFIG_KEY,
                        credentialCipher.decryptSecret(CODE, FeishuOpenApiClient.APP_SECRET_CONFIG_KEY, secret));
            }
            catch (RuntimeException ignored) {
                failCredentialRestore();
            }
        }
        else if (StringUtils.hasText(secret) && !credentialCipher.canDecrypt()) {
            failCredentialRestore();
        }
        credentialError = null;
        client.reconfigure(runtime);
        enabled.set(true);
    }

    private void failCredentialRestore() {
        credentialError = "飞书 App Secret 无法解密，请配置 YUDREAM_CREDENTIAL_KEY 后重新保存或启用";
        throw new BizException(credentialError);
    }

    @Override
    public void disable() {
        enabled.set(false);
        client.close();
    }

    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("飞书开放平台能力未启用");
        }
        if (!client.configured()) {
            return CapabilityTestResult.failure("飞书应用凭证未配置，请先填写 App ID 与 App Secret");
        }
        try {
            return CapabilityTestResult.success("飞书云文档连接可用：" + client.probe());
        }
        catch (RuntimeException exception) {
            return CapabilityTestResult.failure("飞书连接测试失败：" + exception.getMessage());
        }
    }
}
