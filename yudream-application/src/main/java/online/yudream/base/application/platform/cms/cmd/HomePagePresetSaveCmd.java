package online.yudream.base.application.platform.cms.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 把当前首页定制存为方案的命令。
 */
@Data
public class HomePagePresetSaveCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private String description;
}
