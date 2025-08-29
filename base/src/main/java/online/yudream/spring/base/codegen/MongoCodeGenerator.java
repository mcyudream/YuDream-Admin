package online.yudream.spring.base.codegen;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.Version;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 本生成器仅开发阶段可用
 */
@Component
@Slf4j
public class MongoCodeGenerator {

    private final Configuration cfg;

    public MongoCodeGenerator() throws IOException {
        cfg = new Configuration(new Version("2.3.32"));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setClassLoaderForTemplateLoading(
                MongoCodeGenerator.class.getClassLoader(), "/codegen");
    }

    public void generateAll(EntityDef def) throws Exception {
        Map<String, Object> model = buildModel(def);

        // 1) Entity
        writeFromTemplate("entity.ftl", model,
                toFile(def.moduleEntity(),def.outputDir(), def.packageEntity(), def.className(), ".java"));

        // 2) Repository / Mapper
        writeFromTemplate("mapper.ftl", model,
                toFile(def.moduleRepository(),def.outputDir(), def.packageRepository(), def.className() + def.repoSuffix(), ".java"));

        // 3) Service
        writeFromTemplate("service.ftl", model,
                toFile(def.moduleService(),def.outputDir(), def.packageService(), def.className() + "Service", ".java"));

        // 4) ServiceImpl
        writeFromTemplate("serviceImpl.ftl", model,
                toFile(def.moduleServiceImpl(), def.outputDir(), def.packageServiceImpl(), def.className() + "ServiceImpl", ".java"));
    }

    private Map<String, Object> buildModel(EntityDef def) {
        Map<String, Object> m = new HashMap<>();
        m.put("className", def.className());
        m.put("collectionName", def.collectionName() == null || def.collectionName().isBlank()
                ? lowerCamel(def.className())
                : def.collectionName());
        m.put("namePrefix", lowerCamel(def.className()));
        m.put("packageEntity", def.packageEntity());
        m.put("packageRepository", def.packageRepository());
        m.put("packageService", def.packageService());
        m.put("packageServiceImpl", def.packageServiceImpl());
        m.put("repoSuffix", def.repoSuffix());

        // 字段与 import 判定
        boolean importBigDecimal = false, importInstant = true, importList = false, hasIndexed = false;
        List<Map<String, Object>> fields = new ArrayList<>();
        for (FieldDef f : def.fields()) {
            Map<String, Object> fm = new HashMap<>();
            fm.put("name", f.name());
            fm.put("type", f.type());
            fm.put("indexed", f.indexed());
            fields.add(fm);

            if ("BigDecimal".equals(f.type())) importBigDecimal = true;
            if (f.type().startsWith("List<")) importList = true;
            if (f.indexed()) hasIndexed = true;
        }
        // createdAt/updatedAt 用 Instant
        importInstant = true;

        m.put("fields", fields);
        m.put("importBigDecimal", importBigDecimal);
        m.put("importInstant", importInstant);
        m.put("importList", importList);
        m.put("hasIndexed", hasIndexed);

        m.put("generatedAt", LocalDateTime.now().toString());
        return m;
    }

    private void writeFromTemplate(String tpl, Map<String, Object> model, Path outFile) throws Exception {
        Files.createDirectories(outFile.getParent());
        Template template = cfg.getTemplate(tpl);
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(outFile,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), StandardCharsets.UTF_8)) {
            template.process(model, w);
        }
        log.info("✓ generated: {}", outFile);
    }

    private Path toFile(String module,String outputDir, String pkg, String cls, String ext) {
        String pkgPath = pkg.replace('.', File.separatorChar);
        return Paths.get(module,outputDir, pkgPath, cls + ext);
    }

    private static String lowerCamel(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

}
