package online.yudream.base.domain.system.integration.service;

import online.yudream.base.domain.system.integration.valobj.IntegrationProbeResult;
import online.yudream.base.domain.system.integration.valobj.MailServerConfig;
import online.yudream.base.domain.system.integration.valobj.ObjectStorageConfig;

/**
 * 集成配置探测端口：对「候选配置」（尚未保存）做真实连通性验证。
 * 实现必须使用短连接，探测完即释放，不得产生长驻资源。
 */
public interface IntegrationProbe {

    /**
     * 用候选 SMTP 配置向指定地址发送测试邮件。
     */
    IntegrationProbeResult sendTestMail(MailServerConfig config, String to);

    /**
     * 用候选对象存储配置验证 bucket 可达；autoCreate 为真且 bucket 不存在时尝试创建。
     */
    IntegrationProbeResult checkStorage(ObjectStorageConfig config, boolean autoCreate);
}
