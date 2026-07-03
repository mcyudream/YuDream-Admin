package online.yudream.base.infra.system.monitor.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.domain.system.monitor.repo.ApiLogRepo;
import online.yudream.base.infra.system.monitor.dataobj.ApiLogDO;
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
public class ApiLogRepoImpl implements ApiLogRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public void save(ApiLogDTO log) {
        ApiLogDO data = toDataObj(log);
        data.setId(idGenerator.nextId());
        data.setCreateTime(LocalDateTime.now());
        data.setUpdateTime(LocalDateTime.now());
        mongoTemplate.save(data);
    }

    @Override
    public PageResult<ApiLogDTO> page(String keyword, Boolean success, int page, int size) {
        Query query = buildQuery(keyword, success);
        long total = mongoTemplate.count(query, ApiLogDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip((long) (currentPage - 1) * pageSize)
                .limit(pageSize);
        List<ApiLogDTO> records = mongoTemplate.find(query, ApiLogDO.class).stream()
                .map(this::toDto)
                .toList();
        return new PageResult<>(records, total, currentPage, pageSize);
    }

    private Query buildQuery(String keyword, Boolean success) {
        Query query = new Query();
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("path").regex(pattern, "i"),
                    Criteria.where("method").regex(pattern, "i"),
                    Criteria.where("ip").regex(pattern, "i"),
                    Criteria.where("errorMessage").regex(pattern, "i")
            ));
        }
        if (success != null) {
            query.addCriteria(Criteria.where("success").is(success));
        }
        return query;
    }

    private ApiLogDO toDataObj(ApiLogDTO dto) {
        ApiLogDO data = new ApiLogDO();
        data.setMethod(dto.getMethod());
        data.setPath(dto.getPath());
        data.setQuery(dto.getQuery());
        data.setRequestBody(dto.getRequestBody());
        data.setStatus(dto.getStatus());
        data.setCostMs(dto.getCostMs());
        data.setSuccess(dto.getSuccess());
        data.setLoginId(dto.getLoginId());
        data.setIp(dto.getIp());
        data.setUserAgent(dto.getUserAgent());
        data.setErrorMessage(dto.getErrorMessage());
        return data;
    }

    private ApiLogDTO toDto(ApiLogDO data) {
        return ApiLogDTO.builder()
                .id(data.getId())
                .method(data.getMethod())
                .path(data.getPath())
                .query(data.getQuery())
                .requestBody(data.getRequestBody())
                .status(data.getStatus())
                .costMs(data.getCostMs())
                .success(data.getSuccess())
                .loginId(data.getLoginId())
                .ip(data.getIp())
                .userAgent(data.getUserAgent())
                .errorMessage(data.getErrorMessage())
                .createTime(data.getCreateTime())
                .build();
    }
}
