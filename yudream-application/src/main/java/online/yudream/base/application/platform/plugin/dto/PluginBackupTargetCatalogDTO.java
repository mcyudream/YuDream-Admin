package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 插件前端备份目标目录项（仅启用中的异地目标，不含凭据）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginBackupTargetCatalogDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private String type;
}
