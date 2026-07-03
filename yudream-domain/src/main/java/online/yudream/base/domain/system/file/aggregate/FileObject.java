package online.yudream.base.domain.system.file.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FileObject extends BaseDomain {

    private String bucket;

    private String objectKey;

    private String originalName;

    private String contentType;

    private Long size;

    private String module;

    private Long uploaderId;

    private Boolean publicAccess;

    private Boolean deleted;

    public void markDeleted() {
        this.deleted = true;
    }

    public boolean isDeleted() {
        return Boolean.TRUE.equals(deleted);
    }
}
