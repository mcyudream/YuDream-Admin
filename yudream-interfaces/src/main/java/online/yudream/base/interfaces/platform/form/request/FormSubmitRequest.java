package online.yudream.base.interfaces.platform.form.request;

import lombok.Data;

import java.util.Map;

@Data
public class FormSubmitRequest {
    private Map<String, Object> data;
}
