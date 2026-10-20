package online.yudream.base.interfaces.platform.cms.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record StudioRevisionRequest(
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = StudioRevisionDeserializer.class)
        @NotBlank @Pattern(regexp = "0|[1-9][0-9]{0,18}") String expectedRevision) {}
