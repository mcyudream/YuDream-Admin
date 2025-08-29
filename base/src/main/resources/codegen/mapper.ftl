package ${packageRepository};

import org.springframework.data.mongodb.repository.MongoRepository;
import ${packageEntity}.${className};

public interface ${className}${repoSuffix} extends MongoRepository<${className}, String> {

}
