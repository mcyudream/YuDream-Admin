package online.yudream.spring.entity.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("tb_department")
public class Department {
    @MongoId
    private String id;

    private String name;

    @DocumentReference(lazy = true)
    private Department parent;

    private String description;
}
