package online.yudream.base.application.system.menu.cmd;

import lombok.Data;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

import java.io.Serial;
import java.io.Serializable;

/**
 * 数据模板导入的菜单受控写入命令（应用层内部契约，不进入 interfaces）。
 * <p>
 * sourceId 仅为匹配提示（导出时的原记录内部 id）：优先按 sourceId 定位，
 * 且仅当命中记录的 code 与本命令一致时才采纳；否则回退按 code 匹配。
 * sourceId 绝不会作为新记录主键写入。
 */
@Data
public class MenuImportCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 源记录内部 id 匹配提示；可空。 */
    private Long sourceId;

    private String code;

    private String name;

    private MenuNodeType type;

    private String parentCode;

    private String module;

    private String icon;

    private String path;

    private String component;

    private String link;

    private Integer sort;

    private Boolean visible;

    private String permission;

    private MenuStatus status;
}
