package online.yudream.base.interfaces.common.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PageBaseRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Min(value = 1, message = "页码必须大于0")
    private int page = 1;

    @Min(value = 1, message = "每页条数必须大于0")
    @Max(value = 100, message = "每条页数不能大于100")
    private int size = 10;
}
