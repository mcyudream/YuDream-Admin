package online.yudream.base.interfaces.platform.form.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FormFieldStatRes {
    private String field;
    private long filled;
    private long empty;
    private List<FormValueCountRes> topValues;
}
