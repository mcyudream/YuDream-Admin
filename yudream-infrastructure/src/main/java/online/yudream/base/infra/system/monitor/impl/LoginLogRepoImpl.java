package online.yudream.base.infra.system.monitor.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.monitor.dto.LoginLogDTO;
import online.yudream.base.domain.system.monitor.repo.LoginLogRepo;
import online.yudream.base.infra.system.monitor.dataobj.LoginLogDO;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class LoginLogRepoImpl implements LoginLogRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public void save(LoginLogDTO log) {
        LoginLogDO data = toDataObj(log);
        data.setId(idGenerator.nextId());
        data.setCreateTime(LocalDateTime.now());
        data.setUpdateTime(LocalDateTime.now());
        mongoTemplate.save(data);
    }

    @Override
    public PageResult<LoginLogDTO> page(String keyword, Boolean success, int page, int size) {
        Query query = buildQuery(keyword, success);
        long total = mongoTemplate.count(query, LoginLogDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip((long) (currentPage - 1) * pageSize)
                .limit(pageSize);
        List<LoginLogDTO> records = mongoTemplate.find(query, LoginLogDO.class).stream()
                .map(this::toDto)
                .toList();
        return new PageResult<>(records, total, currentPage, pageSize);
    }

    private Query buildQuery(String keyword, Boolean success) {
        Query query = new Query();
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("username").regex(pattern, "i"),
                    Criteria.where("ip").regex(pattern, "i"),
                    Criteria.where("message").regex(pattern, "i")
            ));
        }
        if (success != null) {
            query.addCriteria(Criteria.where("success").is(success));
        }
        return query;
    }

    private LoginLogDO toDataObj(LoginLogDTO dto) {
        LoginLogDO data = new LoginLogDO();
        data.setUsername(dto.getUsername());
        data.setUserId(dto.getUserId());
        data.setSuccess(dto.getSuccess());
        data.setMessage(dto.getMessage());
        data.setIp(dto.getIp());
        data.setUserAgent(dto.getUserAgent());
        data.setToken(dto.getToken());
        return data;
    }

    private LoginLogDTO toDto(LoginLogDO data) {
        return LoginLogDTO.builder()
                .id(data.getId())
                .username(data.getUsername())
                .userId(data.getUserId())
                .success(data.getSuccess())
                .message(data.getMessage())
                .ip(data.getIp())
                .userAgent(data.getUserAgent())
                .token(data.getToken())
                .createTime(data.getCreateTime())
                .build();
    }
}
