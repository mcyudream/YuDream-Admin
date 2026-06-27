package online.yudream.base.domain.common.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class BaseDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private int version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
