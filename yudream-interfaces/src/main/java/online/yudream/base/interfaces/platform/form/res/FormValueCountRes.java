package online.yudream.base.interfaces.platform.form.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FormValueCountRes {
    private String value;
    private long count;
}
