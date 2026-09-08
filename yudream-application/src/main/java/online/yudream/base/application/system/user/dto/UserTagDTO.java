package online.yudream.base.application.system.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTagDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String namespace;
    private String code;
    private String label;
}
