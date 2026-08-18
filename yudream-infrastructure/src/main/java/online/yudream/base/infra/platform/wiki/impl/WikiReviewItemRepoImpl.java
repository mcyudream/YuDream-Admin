package online.yudream.base.infra.platform.wiki.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.wiki.aggregate.WikiReviewItem;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewStatus;
import online.yudream.base.domain.platform.wiki.repo.WikiReviewItemRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.wiki.dataobj.WikiReviewItemDO;
import online.yudream.base.infra.platform.wiki.mapper.WikiKnowledgeInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WikiReviewItemRepoImpl implements WikiReviewItemRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public WikiReviewItem save(WikiReviewItem item) {
        WikiReviewItemDO dataObj = WikiKnowledgeInfraMapper.reviewItem(item);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        WikiReviewItemDO saved = mongo.save(dataObj);
        item.setVersion(saved.getVersion());
        return WikiKnowledgeInfraMapper.reviewItem(saved);
    }

    @Override
    public Optional<WikiReviewItem> findById(Long id) {
        return Optional.ofNullable(WikiKnowledgeInfraMapper.reviewItem(mongo.findById(id, WikiReviewItemDO.class)));
    }

    @Override
    public List<WikiReviewItem> findBySpaceId(Long spaceId) {
        return mongo.find(Query.query(Criteria.where("spaceId").is(spaceId))
                        .with(Sort.by(Sort.Direction.DESC, "createTime")),
                WikiReviewItemDO.class).stream().map(WikiKnowledgeInfraMapper::reviewItem).toList();
    }

    @Override
    public List<WikiReviewItem> findBySpaceIdAndStatus(Long spaceId, WikiReviewStatus status) {
        return mongo.find(Query.query(Criteria.where("spaceId").is(spaceId).and("status").is(status))
                        .with(Sort.by(Sort.Direction.DESC, "createTime")),
                WikiReviewItemDO.class).stream().map(WikiKnowledgeInfraMapper::reviewItem).toList();
    }

    @Override
    public void deleteById(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), WikiReviewItemDO.class);
    }
}
