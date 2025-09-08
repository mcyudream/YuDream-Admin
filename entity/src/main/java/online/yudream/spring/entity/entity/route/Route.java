package online.yudream.spring.entity.entity.route;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("sys_tb_route")
@CompoundIndexes({
        @CompoundIndex(name = "parent_order_idx", def = "{'parentId':1,'order':1}"),
        @CompoundIndex(name = "enabled_type_idx", def = "{'enabled':1,'type':1}")
})
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Route {
    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String name;

    @Indexed(unique = true, sparse = true)
    private String path;

    private String component;
    private String redirect;
    private String permission;
    private RouteType type;
    private String parentId;
    private Integer order;
    private Boolean enabled;

    private RouteMeta meta;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
