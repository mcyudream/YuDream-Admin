package online.yudream.spring.admin;

import jakarta.annotation.Resource;
import online.yudream.spring.base.codegen.EntityDef;
import online.yudream.spring.base.codegen.FieldDef;
import online.yudream.spring.base.codegen.MongoCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class MongoCodeGeneratorTest {

    @Resource
    private MongoCodeGenerator mongoCodeGenerator;

    @Test
    public void generateCode() throws Exception {
        String base = "online.yudream.spring";
        String outDir = "src/main/java"; // 生成到源码目录
        EntityDef test = new EntityDef(
                "Test",
                "test", // 集合名；为空则默认 user 的 lowerCamel
                List.of(
                        new FieldDef("username", "String", true),
                        new FieldDef("password", "String", false),
                        new FieldDef("age", "Integer", false),
                        new FieldDef("roles", "List<String>", false),
                        new FieldDef("balance", "BigDecimal", false),
                        new FieldDef("enabled", "Boolean", false)
                ),
                "entity",
                "entity",
                "admin",
                "admin",
                base + ".entity.entity",
                base + ".entity.mapper",
                base + ".admin.service",
                base + ".admin.service.impl",
                outDir,
                "Mapper" // 若想生成 *Mapper，把这里改成 "Mapper"
        );
        mongoCodeGenerator.generateAll(test);
    }

}
