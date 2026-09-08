package online.yudream.base.infra.system.monitor.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.monitor.dto.ResourceMetricPointDTO;
import online.yudream.base.domain.system.monitor.repo.ResourceMetricRepo;
import online.yudream.base.infra.system.monitor.dataobj.ResourceMetricDO;
import online.yudream.base.infra.system.monitor.mapper.ResourceMetricInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceMetricRepoImpl implements ResourceMetricRepo {

    private static final Duration RETENTION = Duration.ofDays(7);

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @PostConstruct
    void ensureIndexes() {
        try {
            mongoTemplate.indexOps(ResourceMetricDO.class).ensureIndex(
                    new Index().on("sampledAt", Sort.Direction.ASC).expire(RETENTION)
            );
        } catch (RuntimeException e) {
            log.warn("系统资源指标 TTL 索引创建失败：{}", e.getMessage());
        }
    }

    @Override
    public void save(ResourceMetricPointDTO point) {
        ResourceMetricDO data = ResourceMetricInfraMapper.toDataObj(point);
        data.setId(idGenerator.nextId());
        LocalDateTime now = LocalDateTime.now();
        if (data.getSampledAt() == null) {
            data.setSampledAt(now);
        }
        data.setCreateTime(now);
        data.setUpdateTime(now);
        mongoTemplate.save(data);
    }

    @Override
    public List<ResourceMetricPointDTO> findSince(LocalDateTime from) {
        Query query = new Query(Criteria.where("sampledAt").gte(from))
                .with(Sort.by(Sort.Direction.ASC, "sampledAt"));
        return mongoTemplate.find(query, ResourceMetricDO.class).stream()
                .map(ResourceMetricInfraMapper::toDto)
                .toList();
    }
}
