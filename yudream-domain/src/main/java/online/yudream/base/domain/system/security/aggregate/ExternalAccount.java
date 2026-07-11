package online.yudream.base.domain.system.security.aggregate;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ExternalAccount extends BaseDomain {
    private Long userId;
    private String providerCode;
    private String platformType;
    private String socialUid;
    private String nickname;
    private String avatarUrl;
    private String gender;
    private String location;

    public void refresh(String nickname, String avatarUrl, String gender, String location) {
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.gender = gender;
        this.location = location;
    }
}
