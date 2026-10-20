package online.yudream.base.interfaces.platform.cms.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StudioDraftSaveRequest(
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StudioRevisionDeserializer.class)
        @NotBlank @Pattern(regexp = "0|[1-9][0-9]{0,18}") String expectedRevision,
        @NotBlank @Size(max = 2097152) String documentJson) {}
