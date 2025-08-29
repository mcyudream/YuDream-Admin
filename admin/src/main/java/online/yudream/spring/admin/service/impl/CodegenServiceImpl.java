package online.yudream.spring.admin.service.impl;

import jakarta.annotation.Resource;
import online.yudream.spring.admin.service.CodegenService;
import online.yudream.spring.base.codegen.EntityDef;
import online.yudream.spring.base.codegen.MongoCodeGenerator;
import online.yudream.spring.base.exception.BaseException;
import online.yudream.spring.entity.dto.GencodeDto;
import org.springframework.stereotype.Service;


@Service
public class CodegenServiceImpl implements CodegenService {
    @Resource
    private MongoCodeGenerator mongoCodeGenerator;

    @Override
    public void gencode(GencodeDto gencodeDto) {
        String base = gencodeDto.packageName();
        String outDir = gencodeDto.outputDir();
        EntityDef test = new EntityDef(gencodeDto.className(), gencodeDto.collectionName(), gencodeDto.fields(), gencodeDto.moduleEntity(), gencodeDto.moduleRepository(), gencodeDto.moduleService(), gencodeDto.moduleServiceImpl(), base + gencodeDto.packageEntity(), base + gencodeDto.packageRepository(), base + gencodeDto.packageService(), base + gencodeDto.packageServiceImpl(), outDir, "Mapper");
        try {
            this.mongoCodeGenerator.generateAll(test);
        } catch (Exception e) {
            throw new BaseException("exception.codegen.generateAll");
        }
    }
}
