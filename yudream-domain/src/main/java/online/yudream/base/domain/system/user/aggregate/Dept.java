package online.yudream.base.domain.system.user.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.domain.system.user.valobj.UserID;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class Dept extends BaseDomain {

    private String name;
    private String description;
    private UserID leader;
    private Phone phone;
}
