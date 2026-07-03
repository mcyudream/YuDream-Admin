package online.yudream.base.application.system.menu.query;

import lombok.Data;
import online.yudream.base.domain.system.menu.enumerate.MenuNodeType;
import online.yudream.base.domain.system.menu.enumerate.MenuStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
public class MenuTreeQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String keyword;
    private MenuNodeType type;
    private MenuStatus status;
}
