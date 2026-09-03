package online.yudream.base.interfaces.system.user.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserVerificationMethodRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String displayName;
    private String description;
    private String icon;
    private Integer sort;
}
