package online.yudream.base.application.platform.integration.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class RuntimeExecuteCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long scriptId;
    private String stdin;
}
