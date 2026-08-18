package online.yudream.base.interfaces.platform.chat.res;

import lombok.Builder;

@Builder
public record ChatQuotaConfigRes(
        long dailyTokenLimit
) {
}
