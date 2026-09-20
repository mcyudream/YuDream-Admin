package online.yudream.base.application.system.setting.cmd;

import lombok.Data;

/**
 * 系统集成配置保存命令：任一分区为 null 表示不修改该分区。
 */
@Data
public class IntegrationConfigSaveCmd {

    private MailConfigCmd mail;
    private StorageConfigCmd storage;
}
