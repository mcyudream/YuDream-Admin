package ${packageEntity};

import lombok.*;
import org.springframework.data.mongodb.core.mapping.MongoId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
<#if hasIndexed?? && hasIndexed>
    import org.springframework.data.mongodb.core.index.Indexed;
</#if>
<#if importBigDecimal?? && importBigDecimal>
    import java.math.BigDecimal;
</#if>
<#if importInstant?? && importInstant>
    import java.time.Instant;
</#if>
<#if importList?? && importList>
    import java.util.List;
</#if>
<#--<#list packages as p>-->
<#--    import p;-->
<#--</#list>-->

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("${collectionName}")
public class ${className} {

    @MongoId
    private String id;
<#list fields as f>
    <#if f.indexed?? && f.indexed>
    @Indexed
    </#if>
    private ${f.type} ${f.name};
</#list>
    @CreatedDate
    private Instant createTime;
    @LastModifiedDate
    private Instant updatedTime;
}
