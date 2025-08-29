package online.yudream.spring.base.utils;

import io.micrometer.common.util.StringUtils;
import jakarta.annotation.Resource;
import online.yudream.spring.base.common.SearchPageDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class SearchUtils {
    @Resource
    private MongoTemplate mongoTemplate;

    private String getRegex(String keyword) {
        return ".*?" + keyword + ".*?";
    }

    public Criteria searchCriteria(SearchPageDto searchPageDto, String[] fields) {
        Criteria criteriaChain = new Criteria();
        // 关键词查询
        if (StringUtils.isNotBlank(searchPageDto.keyword())) {
            for (String field : fields) {
                criteriaChain.orOperator(Criteria.where(field).regex(getRegex(searchPageDto.keyword())));
            }
        }
        return criteriaChain;
    }


    public <T> Page<T> findPage(Class<T> entityClass, SearchPageDto searchPageDto, Criteria criteria) {
        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, entityClass);
        Pageable pageable =  PageRequest.of(searchPageDto.page()-1, searchPageDto.size());
        List<T> results = mongoTemplate.find(query.with(pageable), entityClass);
        return new PageImpl<>(results, pageable, total);
    }

}
