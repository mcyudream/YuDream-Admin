package online.yudream.base.infra.system.file.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sysFileObject")
public class FileObjectDO extends BaseDO {

    private String bucket;
    private String objectKey;
    private String originalName;
    private String contentType;
    private Long size;
    private String module;
    private Long uploaderId;
    private Boolean publicAccess;
    private Boolean deleted;
}
