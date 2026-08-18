package online.yudream.base.infra.platform.chat.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformChatQuota")
@CompoundIndex(name = "userDate", def = "{'userId': 1, 'usageDate': 1}", unique = true)
public class UserChatQuotaDO extends BaseDO {
    private Long userId;
    private LocalDate usageDate;
    private long usedTokens;
    private long limitTokens;
}
