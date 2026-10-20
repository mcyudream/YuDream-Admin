package online.yudream.base.interfaces.platform.cms.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StudioBlockTemplateSaveRequest(
        @NotBlank @Size(max = 60) String name,
        @Size(max = 20) String category,
        @Size(max = 200) String description,
        @Size(max = 10) List<@Size(max = 20) String> keywords,
        @Size(max = 262144) String nodesJson,
        @Pattern(regexp = "[1-9][0-9]{0,18}") String expectedRevision) {}
