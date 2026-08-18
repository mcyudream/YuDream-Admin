package online.yudream.base.infra.platform.wiki.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.wiki.dataobj.WikiPageVersionDO;
import online.yudream.base.infra.platform.wiki.mapper.WikiInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WikiPageVersionRepoImpl implements WikiPageVersionRepo {
    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public WikiPageVersion save(WikiPageVersion version) {
        WikiPageVersionDO data = WikiInfraMapper.version(version);
        if (data.getId() == null) {
            data.setId(ids.nextId());
            data.setCreateTime(LocalDateTime.now());
        }
        data.setUpdateTime(LocalDateTime.now());
        return WikiInfraMapper.version(mongo.save(data));
    }

    @Override
    public Optional<WikiPageVersion> findById(Long id) {
        return Optional.ofNullable(WikiInfraMapper.version(mongo.findById(id, WikiPageVersionDO.class)));
    }

    @Override
    public List<WikiPageVersion> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("id").in(ids));
        return mongo.find(query, WikiPageVersionDO.class).stream().map(WikiInfraMapper::version).toList();
    }

    @Override
    public Optional<WikiPageVersion> findLatest(Long nodeId) {
        Query query = Query.query(Criteria.where("nodeId").is(nodeId))
                .with(Sort.by(Sort.Direction.DESC, "revision"));
        return Optional.ofNullable(WikiInfraMapper.version(mongo.findOne(query, WikiPageVersionDO.class)));
    }
}
