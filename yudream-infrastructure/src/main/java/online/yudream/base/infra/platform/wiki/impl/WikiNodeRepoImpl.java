package online.yudream.base.infra.platform.wiki.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.wiki.dataobj.WikiNodeDO;
import online.yudream.base.infra.platform.wiki.mapper.WikiInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import online.yudream.base.infra.platform.wiki.service.WikiSearchTokenizer;

@Service
@RequiredArgsConstructor
public class WikiNodeRepoImpl implements WikiNodeRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public WikiNode save(WikiNode node) {
        WikiNodeDO dataObj = WikiInfraMapper.node(node);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return WikiInfraMapper.node(mongo.save(dataObj));
    }

    @Override
    public Optional<WikiNode> findById(Long id) {
        return Optional.ofNullable(WikiInfraMapper.node(mongo.findById(id, WikiNodeDO.class)));
    }

    @Override
    public List<WikiNode> findBySpaceId(Long spaceId) {
        return mongo.find(Query.query(Criteria.where("spaceId").is(spaceId))
                        .with(Sort.by("sort", "createTime")), WikiNodeDO.class)
                .stream().map(WikiInfraMapper::node).toList();
    }

    @Override
    public List<WikiNode> searchByKeyword(Long spaceId, String keyword, int limit) {
        List<String> terms = WikiSearchTokenizer.tokenize(keyword);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<Criteria> fieldCriteria = new ArrayList<>();
        for (String term : terms) {
            String regex = java.util.regex.Pattern.quote(term);
            fieldCriteria.add(Criteria.where("title").regex(regex, "i"));
            fieldCriteria.add(Criteria.where("markdownDraft").regex(regex, "i"));
            fieldCriteria.add(Criteria.where("summary").regex(regex, "i"));
        }
        Criteria criteria = Criteria.where("spaceId").is(spaceId).orOperator(fieldCriteria.toArray(new Criteria[0]));
        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "updateTime")).limit(Math.max(limit, 1));
        return mongo.find(query, WikiNodeDO.class).stream().map(WikiInfraMapper::node).toList();
    }

    @Override
    public Optional<WikiNode> findBySlug(Long spaceId, String slug) {
        return Optional.ofNullable(WikiInfraMapper.node(mongo.findOne(
                Query.query(Criteria.where("spaceId").is(spaceId).and("slug").is(slug)), WikiNodeDO.class)));
    }

    @Override
    public List<WikiNode> findByType(Long spaceId, online.yudream.base.domain.platform.wiki.enumerate.WikiPageType pageType) {
        return mongo.find(Query.query(Criteria.where("spaceId").is(spaceId).and("pageType").is(pageType))
                        .with(Sort.by("sort", "createTime")), WikiNodeDO.class)
                .stream().map(WikiInfraMapper::node).toList();
    }

    @Override
    public void deleteById(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), WikiNodeDO.class);
    }
}
