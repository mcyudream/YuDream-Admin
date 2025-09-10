package online.yudream.spring.entity.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Document("sys_tb_permission")
public class Permission {
    @Id
    @MongoId(value = FieldType.STRING)
    private String id;
    private String name;
    private String description;
}
