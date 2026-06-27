package online.yudream.base.infra.common.baseobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.*;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class BaseDO {
    @Id
    private Long id;

    @Version
    private int version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
