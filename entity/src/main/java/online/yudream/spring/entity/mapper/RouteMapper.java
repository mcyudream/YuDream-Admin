package online.yudream.spring.entity.mapper;

import online.yudream.spring.entity.entity.route.Route;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RouteMapper extends MongoRepository<Route, String> {
}
