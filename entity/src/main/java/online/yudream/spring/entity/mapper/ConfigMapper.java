package online.yudream.spring.entity.mapper;

import org.springframework.data.mongodb.repository.MongoRepository;
import online.yudream.spring.entity.entity.Config;

public interface ConfigMapper extends MongoRepository<Config, String> {

}
