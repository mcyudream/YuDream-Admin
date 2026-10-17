package online.yudream.base.infra.platform.ai.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.ai.aggregate.AiToolSettings;
import online.yudream.base.domain.platform.ai.repo.AiToolSettingsRepo;
import online.yudream.base.infra.platform.ai.dataobj.AiToolSettingsDO;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AiToolSettingsRepoImpl implements AiToolSettingsRepo {

    private final MongoTemplate mongoTemplate;

    @Override
    public AiToolSettings load() {
        AiToolSettingsDO data = mongoTemplate.findById(AiToolSettings.GLOBAL_KEY, AiToolSettingsDO.class);
        if (data == null) {
            return AiToolSettings.defaults();
        }
        return AiToolSettings.of(data.getSystemEnabled() == null || data.getSystemEnabled(), data.getModuleStates());
    }

    @Override
    public AiToolSettings save(AiToolSettings settings) {
        // 复用已存在文档（保留 @Version），否则第二次保存会因版本不匹配退化为插入而触发 E11000 主键冲突。
        AiToolSettingsDO data = mongoTemplate.findById(AiToolSettings.GLOBAL_KEY, AiToolSettingsDO.class);
        if (data == null) {
            data = new AiToolSettingsDO();
            data.setKey(AiToolSettings.GLOBAL_KEY);
        }
        data.setSystemEnabled(settings.isSystemEnabled());
        data.setModuleStates(settings.getModuleStates());
        data.setUpdateTime(LocalDateTime.now());
        mongoTemplate.save(data);
        return load();
    }
}
