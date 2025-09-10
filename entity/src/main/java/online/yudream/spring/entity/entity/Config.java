package online.yudream.spring.entity.entity;

import lombok.*;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
    import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("sys_tb_config")
public class Config {

    @MongoId(value = FieldType.STRING)
    private String id;
    private String value;
    private String fieldName;
    @CreatedDate
    private Instant createTime;
    @LastModifiedDate
    private Instant updatedTime;
}
