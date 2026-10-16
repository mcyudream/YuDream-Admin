package online.yudream.base.interfaces.common.controller;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.common.search.SearchConfig;
import online.yudream.base.application.common.search.SearchConfigExtractor;
import online.yudream.base.interfaces.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索配置控制器
 * <p>
 * 提供查询类搜索字段配置的获取接口,供前端动态生成筛选表单使用
 */
@Slf4j
@RestController
@RequestMapping("/api/search-config")
public class SearchConfigController {

    /**
     * 获取指定查询类的搜索字段配置
     *
     * @param queryClass 查询类的全限定名,例如: online.yudream.base.application.system.user.query.UserPageQuery
     * @return 搜索配置,包含所有标记了 @Searchable 注解的字段信息
     */
    @GetMapping
    public Result<SearchConfig> getSearchConfig(@RequestParam String queryClass) {
        try {
            // 加载查询类
            Class<?> clazz = Class.forName(queryClass);

            // 提取搜索配置
            SearchConfig config = SearchConfigExtractor.extractFrom(clazz);

            log.debug("提取搜索配置成功: {} 包含 {} 个字段", queryClass, config.getFields().size());

            return Result.ok(config);
        } catch (ClassNotFoundException e) {
            log.error("查询类不存在: {}", queryClass, e);
            return Result.fail("查询类不存在: " + queryClass);
        } catch (Exception e) {
            log.error("提取搜索配置失败: {}", queryClass, e);
            return Result.fail("提取搜索配置失败: " + e.getMessage());
        }
    }
}
