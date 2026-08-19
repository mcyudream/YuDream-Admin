package online.yudream.base.infra.platform.wiki.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.wiki.dataobj.WikiSourceDO;
import online.yudream.base.infra.platform.wiki.mapper.WikiKnowledgeInfraMapper;
import online.yudream.base.infra.platform.wiki.service.WikiSearchTokenizer;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WikiSourceRepoImpl implements WikiSourceRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public WikiSource save(WikiSource source) {
        WikiSourceDO dataObj = WikiKnowledgeInfraMapper.source(source);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        WikiSourceDO saved = mongo.save(dataObj);
        source.setVersion(saved.getVersion());
        return WikiKnowledgeInfraMapper.source(saved);
    }

    @Override
    public Optional<WikiSource> findById(Long id) {
        return Optional.ofNullable(WikiKnowledgeInfraMapper.source(mongo.findById(id, WikiSourceDO.class)));
    }

    @Override
    public List<WikiSource> findBySpaceId(Long spaceId) {
        return mongo.find(Query.query(Criteria.where("spaceId").is(spaceId)).with(Sort.by("sort", "createTime")),
                WikiSourceDO.class).stream().map(WikiKnowledgeInfraMapper::source).toList();
    }

    @Override
    public List<WikiSource> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return mongo.find(Query.query(Criteria.where("id").in(ids)), WikiSourceDO.class).stream()
                .map(WikiKnowledgeInfraMapper::source).toList();
    }

    @Override
    public List<WikiSource> findByImageFileObjectIds(Long spaceId, List<Long> fileObjectIds) {
        if (spaceId == null || fileObjectIds == null || fileObjectIds.isEmpty()) {
            return List.of();
        }
        return mongo.find(Query.query(Criteria.where("spaceId").is(spaceId)
                        .and("images.fileObjectId").in(fileObjectIds)), WikiSourceDO.class).stream()
                .map(WikiKnowledgeInfraMapper::source)
                .toList();
    }

    @Override
    public Optional<WikiSource> findByContentHash(Long spaceId, String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(WikiKnowledgeInfraMapper.source(mongo.findOne(
                Query.query(Criteria.where("spaceId").is(spaceId).and("contentHash").is(contentHash)),
                WikiSourceDO.class)));
    }

    @Override
    public List<WikiSource> searchByKeyword(Long spaceId, String keyword, int limit) {
        List<String> terms = WikiSearchTokenizer.tokenize(keyword);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<Criteria> fieldCriteria = new ArrayList<>();
        for (String term : terms) {
            String regex = java.util.regex.Pattern.quote(term);
            fieldCriteria.add(Criteria.where("title").regex(regex, "i"));
            fieldCriteria.add(Criteria.where("fileName").regex(regex, "i"));
            fieldCriteria.add(Criteria.where("extractedText").regex(regex, "i"));
            fieldCriteria.add(Criteria.where("images.caption").regex(regex, "i"));
        }
        Criteria criteria = Criteria.where("spaceId").is(spaceId).orOperator(fieldCriteria.toArray(new Criteria[0]));
        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "createTime")).limit(Math.max(limit, 1));
        return mongo.find(query, WikiSourceDO.class).stream().map(WikiKnowledgeInfraMapper::source).toList();
    }

    @Override
    public void deleteById(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), WikiSourceDO.class);
    }

    @Override
    public long countBySpaceId(Long spaceId) {
        return mongo.count(Query.query(Criteria.where("spaceId").is(spaceId)), WikiSourceDO.class);
    }
}
