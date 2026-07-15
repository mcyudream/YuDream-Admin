package online.yudream.base.infra.platform.wiki.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.wiki.dataobj.WikiSpaceDO;
import online.yudream.base.infra.platform.wiki.mapper.WikiInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WikiSpaceRepoImpl implements WikiSpaceRepo {
    private final MongoTemplate mongo;
    private final IdGenerator ids;

    public WikiSpace save(WikiSpace s) {
        WikiSpaceDO d = WikiInfraMapper.space(s);
        if (d.getId() == null) {
            d.setId(ids.nextId());
            d.setCreateTime(LocalDateTime.now());
        }
        d.setUpdateTime(LocalDateTime.now());
        return WikiInfraMapper.toDomain(mongo.save(d));
    }

    public Optional<WikiSpace> findById(Long id) {
        return Optional.ofNullable(WikiInfraMapper.toDomain(mongo.findById(id, WikiSpaceDO.class)));
    }

    public Optional<WikiSpace> findBySlug(String slug) {
        return Optional.ofNullable(WikiInfraMapper.toDomain(mongo.findOne(Query.query(Criteria.where("slug").is(slug)), WikiSpaceDO.class)));
    }

    public List<WikiSpace> findAll() {
        return mongo.findAll(WikiSpaceDO.class).stream().map(WikiInfraMapper::toDomain).toList();
    }

    public void deleteById(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), WikiSpaceDO.class);
    }
}
