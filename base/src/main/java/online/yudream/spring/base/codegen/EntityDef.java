package online.yudream.spring.base.codegen;

import java.util.List;

public record EntityDef(String className,
                        String collectionName,
                        List<FieldDef> fields,
                        String moduleEntity,
                        String moduleRepository,
                        String moduleService,
                        String moduleServiceImpl,
                        String packageEntity,
                        String packageRepository,
                        String packageService,
                        String packageServiceImpl,
                        String outputDir,
                        String repoSuffix) {
}
