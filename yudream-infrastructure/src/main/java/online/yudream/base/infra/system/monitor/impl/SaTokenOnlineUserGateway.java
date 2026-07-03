package online.yudream.base.infra.system.monitor.impl;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;
import online.yudream.base.domain.system.monitor.service.OnlineUserGateway;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.infra.system.monitor.dataobj.LoginLogDO;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SaTokenOnlineUserGateway implements OnlineUserGateway {

    private final UserRepo userRepo;
    private final MongoTemplate mongoTemplate;

    @Override
    public List<OnlineUserDTO> list(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String match = StringUtils.hasText(keyword) ? keyword.trim() : "";
        Set<String> tokens = new LinkedHashSet<>(StpUtil.searchTokenValue("", 0, safeLimit, false));
        String currentToken = StpUtil.getTokenValue();
        if (StringUtils.hasText(currentToken)) {
            tokens.add(currentToken);
        }
        tokens.addAll(recentLoginTokens(safeLimit));
        return tokens.stream()
                .map(this::toOnlineUser)
                .filter(Objects::nonNull)
                .filter(item -> matches(item, match))
                .toList();
    }

    @Override
    public void kickout(String token) {
        if (StringUtils.hasText(token)) {
            StpUtil.kickoutByTokenValue(token);
        }
    }

    private OnlineUserDTO toOnlineUser(String token) {
        try {
            Object loginId = StpUtil.getLoginIdByTokenNotThinkFreeze(token);
            if (loginId == null && token.equals(StpUtil.getTokenValue())) {
                loginId = StpUtil.getLoginIdDefaultNull();
            }
            if (loginId == null) {
                return null;
            }
            long timeout = StpUtil.getTokenTimeout(token);
            if (timeout == -2) {
                return null;
            }
            Long userId = Long.valueOf(String.valueOf(loginId));
            User user = userRepo.findById(userId).orElse(null);
            return OnlineUserDTO.builder()
                    .token(token)
                    .userId(userId)
                    .username(user == null ? null : user.getUsername())
                    .nickname(user == null ? null : user.getNickname())
                    .email(user == null || user.getEmail() == null ? null : user.getEmail().getValue())
                    .timeout(timeout)
                    .activeTimeout(StpUtil.getTokenActiveTimeout())
                    .device(StpUtil.getLoginDeviceByToken(token))
                    .build();
        }
        catch (Exception e) {
            return null;
        }
    }

    private List<String> recentLoginTokens(int limit) {
        Query query = Query.query(Criteria.where("success").is(true).and("token").ne(null));
        query.with(Sort.by(Sort.Direction.DESC, "createTime")).limit(limit);
        return mongoTemplate.find(query, LoginLogDO.class).stream()
                .map(LoginLogDO::getToken)
                .filter(StringUtils::hasText)
                .toList();
    }

    private boolean matches(OnlineUserDTO item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String lower = keyword.toLowerCase();
        return contains(item.getToken(), lower)
                || contains(item.getUsername(), lower)
                || contains(item.getNickname(), lower)
                || contains(item.getEmail(), lower)
                || contains(item.getUserId() == null ? null : String.valueOf(item.getUserId()), lower);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
}
