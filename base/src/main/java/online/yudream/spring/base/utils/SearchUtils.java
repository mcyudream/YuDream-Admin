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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Component
public class SearchUtils {
    @Resource
    private MongoTemplate mongoTemplate;

    private String getRegex(String keyword) {
        return ".*?" + keyword + ".*?";
    }

    public Criteria searchCriteria(SearchPageDto searchPageDto) {
        // 创建一个列表来存储所有的 $or 条件
        List<Criteria> andCriteriaList = new ArrayList<>();

        // 遍历关键词
        for (Map.Entry<String, String> entry : searchPageDto.keywords().entrySet()) {
            if (StringUtils.isNotBlank(entry.getValue())) {
                andCriteriaList.add(Criteria.where(entry.getKey()).regex(getRegex(entry.getValue())));
            }
        }

        // 如果没有关键词，返回一个空的 Criteria
        if (andCriteriaList.isEmpty()) {
            return new Criteria();
        }

        // 将所有的 $or 条件合并到一个 Criteria 对象中
        return new Criteria().andOperator(andCriteriaList.toArray(new Criteria[0]));
    }


    public <T> List<T> find(Class<T> entityClass, SearchPageDto searchPageDto, Criteria criteria) {
        Query query = new Query(criteria);
        return mongoTemplate.find(query, entityClass);

    }

    public <T> Page<T> findPage(Class<T> entityClass, SearchPageDto searchPageDto, Criteria criteria) {
        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, entityClass);
        Pageable pageable =  PageRequest.of(searchPageDto.page()-1, searchPageDto.size());
        List<T> results = mongoTemplate.find(query.with(pageable), entityClass);
        return new PageImpl<>(results, pageable, total);
    }


}
