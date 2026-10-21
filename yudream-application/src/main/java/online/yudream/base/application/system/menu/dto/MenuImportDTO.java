package online.yudream.base.application.system.menu.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 数据模板导入的菜单写入结果：仅回执业务标识与写入方式，
 * 不回传任何作为记录主键的源 id。
 */
@Data
@Builder
public class MenuImportDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String code;

    /** 是否本批新建（新菜单固定为 SYSTEM 来源）。 */
    private Boolean created;

    /** 是否命中既有记录并按受控字段更新。 */
    private Boolean matched;
}
