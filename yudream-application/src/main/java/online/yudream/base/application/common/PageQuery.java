package online.yudream.base.application.common;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PageQuery implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int page = 1;
    private int size = 10;
}
