package online.yudream.base.application.system.setting.assembler;

import online.yudream.base.application.system.setting.cmd.MailTestCmd;
import online.yudream.base.application.system.setting.cmd.StorageTestCmd;
import online.yudream.base.domain.system.integration.valobj.MailServerConfig;
import online.yudream.base.domain.system.integration.valobj.ObjectStorageConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 系统集成配置应用装配：候选测试命令 -> 配置值对象。
 */
@Component
public class IntegrationConfigAssembler {

    public MailServerConfig toMailServerConfig(MailTestCmd cmd, String effectivePassword) {
        return new MailServerConfig(
                cmd.getHost(),
                cmd.getPort() == null ? 465 : cmd.getPort(),
                cmd.getUsername(),
                StringUtils.hasText(cmd.getPassword()) ? cmd.getPassword() : effectivePassword,
                cmd.getFrom(),
                Boolean.TRUE.equals(cmd.getSsl()),
                Boolean.TRUE.equals(cmd.getStarttls())
        );
    }

    public ObjectStorageConfig toObjectStorageConfig(StorageTestCmd cmd, String effectiveSecretKey) {
        return new ObjectStorageConfig(
                cmd.getEndpoint(),
                cmd.getAccessKey(),
                StringUtils.hasText(cmd.getSecretKey()) ? cmd.getSecretKey() : effectiveSecretKey,
                cmd.getBucket(),
                cmd.getRegion(),
                !Boolean.FALSE.equals(cmd.getPathStyle())
        );
    }
}
