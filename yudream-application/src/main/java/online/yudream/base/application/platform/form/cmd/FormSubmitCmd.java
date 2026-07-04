package online.yudream.base.application.platform.form.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
public class FormSubmitCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private Map<String, Object> data;
    private Long submitterId;
    private String submitterIp;
}
