package online.yudream.spring.entity.dto;

import online.yudream.spring.base.codegen.FieldDef;

import java.util.List;

public record GencodeDto(
        String className,
        List<FieldDef> fields,
        String collectionName,
        String packageName,
        String moduleEntity,
        String moduleRepository,
        String moduleService,
        String moduleServiceImpl,
        String packageEntity,
        String packageRepository,
        String packageService,
        String packageServiceImpl,
        String outputDir
) {
}
