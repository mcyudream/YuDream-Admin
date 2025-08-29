package online.yudream.spring.entity.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document("sys_tb_role")
public class Role {
    @MongoId
    private String id;
    private String name;
    private String description;
    private Boolean enabled;
    private int level;
    private List<String> permissionId;
}
