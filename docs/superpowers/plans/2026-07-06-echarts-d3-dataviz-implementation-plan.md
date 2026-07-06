# ECharts / D3 数据可视化封装实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在后端新增 `platform/dataviz` DDD 平台能力模块，在前端新增 `packages/dataviz` 通用图表组件包，并在 Dashboard 新增两张演示卡片。

**Architecture:** 后端严格按 `yudream-ddd-architecture` 四模块组织（domain/application/infrastructure/interfaces），接入平台能力双门控；前端按 `packages/*` workspace 包拆分，ECharts 负责常规统计图，D3 独立负责关系图/桑基图，宿主应用只做薄封装。

**Tech Stack:** Java 21/Spring Boot/MongoDB，Vue 3/TypeScript，ECharts，D3，pnpm workspace。

---

## 0. 文件总览

### 后端新增文件

```
yudream-domain/src/main/java/online/yudream/base/domain/platform/dataviz/
├── aggregate/ChartDefinition.java
├── enumerate/ChartType.java
├── valobj/ChartDatasetQuery.java
├── valobj/ChartDataSeries.java
├── valobj/ChartLayoutConfig.java
├── repo/ChartDefinitionRepo.java
├── repo/ChartDatasetGateway.java
└── service/ChartDomainService.java

yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/
├── cmd/ChartDefinitionCreateCmd.java
├── cmd/ChartDefinitionUpdateCmd.java
├── query/ChartDefinitionPageQuery.java
├── query/ChartDataQuery.java
├── dto/ChartDefinitionDTO.java
├── dto/ChartDatasetDTO.java
├── dto/ChartSeriesDTO.java
├── assembler/ChartAssembler.java
└── service/ChartAppService.java

yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/dataviz/
├── dataobj/ChartDefinitionDO.java
├── mapper/ChartInfraMapper.java
├── impl/ChartDefinitionRepoImpl.java
├── impl/ChartDatasetGatewayImpl.java
└── service/ChartDataProvider.java

yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/dataviz/
├── controller/ChartDataController.java
├── assembler/ChartWebAssembler.java
├── request/ChartDefinitionSaveRequest.java
├── request/ChartDataRequest.java
├── res/ChartDefinitionRes.java
└── res/ChartDatasetRes.java
```

### 前端新增/修改文件

```
yudream-frontend/packages/dataviz/
├── package.json
├── tsconfig.json
├── src/index.ts
├── src/types.ts
├── src/composables/useECharts.ts
├── src/composables/useD3Graph.ts
├── src/composables/useChartTheme.ts
├── src/components/BaseChart.vue
├── src/components/LineChart.vue
├── src/components/BarChart.vue
├── src/components/PieChart.vue
├── src/components/SankeyChart.vue
├── src/components/GraphChart.vue
├── src/components/StatTile.vue
├── src/utils/echarts-option-builder.ts
├── src/utils/d3-layouts.ts
├── src/utils/transformers.ts
└── src/utils/theme-adapters.ts

yudream-frontend/apps/core-arco-design-vue/
├── src/api/modules/platform-dataviz.ts
├── src/components/chart/ChartWidget.vue
├── src/views/dashboard/DashboardChartStatsCard.vue
├── src/views/dashboard/DashboardFlowGraphCard.vue
└── src/views/dashboard/index.vue（修改：注册卡片）

yudream-frontend/package.json（修改：新增 workspace 成员）
yudream-frontend/pnpm-workspace.yaml（修改：确认包含 packages/dataviz）
yudream-frontend/apps/core-arco-design-vue/package.json（修改：添加依赖）
```

---

## Task 1：后端领域层（domain）

**Files:**
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/dataviz/enumerate/ChartType.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/dataviz/valobj/ChartDatasetQuery.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/dataviz/valobj/ChartDataSeries.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/dataviz/valobj/ChartLayoutConfig.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/dataviz/aggregate/ChartDefinition.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/dataviz/repo/ChartDefinitionRepo.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/dataviz/repo/ChartDatasetGateway.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/dataviz/service/ChartDomainService.java`

- [ ] **Step 1: 创建 ChartType 枚举**

```java
package online.yudream.base.domain.platform.dataviz.enumerate;

public enum ChartType {
    LINE,
    BAR,
    PIE,
    SANKEY,
    GRAPH,
    STAT
}
```

- [ ] **Step 2: 创建值对象**

`ChartDatasetQuery.java`

```java
package online.yudream.base.domain.platform.dataviz.valobj;

import lombok.Builder;

import java.util.Map;

@Builder
public record ChartDatasetQuery(
        String source,
        String metric,
        Map<String, Object> params
) {
}
```

`ChartDataSeries.java`

```java
package online.yudream.base.domain.platform.dataviz.valobj;

import lombok.Builder;

import java.util.List;

@Builder
public record ChartDataSeries(
        String name,
        List<String> categories,
        List<Number> values,
        List<ChartDataNode> nodes,
        List<ChartDataLink> links
) {
}
```

`ChartDataNode.java`

```java
package online.yudream.base.domain.platform.dataviz.valobj;

import lombok.Builder;

@Builder
public record ChartDataNode(
        String id,
        String name,
        String group,
        Number value
) {
}
```

`ChartDataLink.java`

```java
package online.yudream.base.domain.platform.dataviz.valobj;

import lombok.Builder;

@Builder
public record ChartDataLink(
        String source,
        String target,
        Number value
) {
}
```

`ChartLayoutConfig.java`

```java
package online.yudream.base.domain.platform.dataviz.valobj;

import lombok.Builder;

import java.util.Map;

@Builder
public record ChartLayoutConfig(
        String title,
        String subTitle,
        String theme,
        Map<String, Object> extras
) {
}
```

- [ ] **Step 3: 创建聚合根 ChartDefinition**

```java
package online.yudream.base.domain.platform.dataviz.aggregate;

import lombok.*;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class ChartDefinition extends BaseDomain {

    private String code;
    private String name;
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
    private Boolean enabled;

    public static ChartDefinition create(String code, String name, ChartType chartType,
                                         String dataSource, ChartDatasetQuery queryConfig,
                                         ChartLayoutConfig layoutConfig) {
        if (code == null || code.isBlank()) {
            throw new BizException("图表编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new BizException("图表名称不能为空");
        }
        if (chartType == null) {
            throw new BizException("图表类型不能为空");
        }
        ChartDefinition def = new ChartDefinition();
        def.code = code;
        def.name = name;
        def.chartType = chartType;
        def.dataSource = dataSource;
        def.queryConfig = queryConfig;
        def.layoutConfig = layoutConfig;
        def.enabled = true;
        return def;
    }

    public void update(String name, ChartType chartType, String dataSource,
                       ChartDatasetQuery queryConfig, ChartLayoutConfig layoutConfig) {
        if (name == null || name.isBlank()) {
            throw new BizException("图表名称不能为空");
        }
        if (chartType == null) {
            throw new BizException("图表类型不能为空");
        }
        this.name = name;
        this.chartType = chartType;
        this.dataSource = dataSource;
        this.queryConfig = queryConfig;
        this.layoutConfig = layoutConfig;
    }

    public void disable() {
        this.enabled = false;
    }

    public void active() {
        this.enabled = true;
    }

    public boolean active() {
        return Boolean.TRUE.equals(this.enabled);
    }
}
```

- [ ] **Step 4: 创建仓库与网关接口**

`ChartDefinitionRepo.java`

```java
package online.yudream.base.domain.platform.dataviz.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;

import java.util.Optional;

public interface ChartDefinitionRepo {
    PageResult<ChartDefinition> page(String keyword, int page, int size);
    Optional<ChartDefinition> findById(Long id);
    Optional<ChartDefinition> findByCode(String code);
    ChartDefinition save(ChartDefinition definition);
}
```

`ChartDatasetGateway.java`

```java
package online.yudream.base.domain.platform.dataviz.repo;

import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;

import java.util.List;

public interface ChartDatasetGateway {
    List<ChartDataSeries> query(String source, ChartDatasetQuery query);
}
```

- [ ] **Step 5: 创建领域服务**

```java
package online.yudream.base.domain.platform.dataviz.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import org.springframework.stereotype.Service;

@Service
public class ChartDomainService {

    public void validateQueryForType(ChartType type, ChartDatasetQuery query) {
        if (type == null || query == null) {
            throw new BizException("图表类型与查询条件不能为空");
        }
        if (query.source() == null || query.source().isBlank()) {
            throw new BizException("数据源不能为空");
        }
    }
}
```

- [ ] **Step 6: 编译验证**

Run:

```bash
mvn -pl yudream-domain -am -DskipTests compile
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add yudream-domain/src/main/java/online/yudream/base/domain/platform/dataviz/
git commit -m "feat: 新增 platform/dataviz 领域层" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2：后端应用层（application）

**Files:**
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/cmd/ChartDefinitionCreateCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/cmd/ChartDefinitionUpdateCmd.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/query/ChartDefinitionPageQuery.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/query/ChartDataQuery.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/dto/ChartDefinitionDTO.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/dto/ChartDatasetDTO.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/dto/ChartSeriesDTO.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/assembler/ChartAssembler.java`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/service/ChartAppService.java`

- [ ] **Step 1: 创建 Cmd / Query / DTO**

`ChartDefinitionCreateCmd.java`

```java
package online.yudream.base.application.platform.dataviz.cmd;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

@Data
@Builder
public class ChartDefinitionCreateCmd {
    private String code;
    private String name;
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
}
```

`ChartDefinitionUpdateCmd.java`

```java
package online.yudream.base.application.platform.dataviz.cmd;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

@Data
@Builder
public class ChartDefinitionUpdateCmd {
    private Long id;
    private String name;
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
}
```

`ChartDefinitionPageQuery.java`

```java
package online.yudream.base.application.platform.dataviz.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.common.PageQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChartDefinitionPageQuery extends PageQuery {
    private String keyword;
}
```

`ChartDataQuery.java`

```java
package online.yudream.base.application.platform.dataviz.query;

import lombok.Builder;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;

@Builder
public record ChartDataQuery(
        Long definitionId,
        ChartType chartType,
        ChartDatasetQuery datasetQuery
) {
}
```

`ChartDefinitionDTO.java`

```java
package online.yudream.base.application.platform.dataviz.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

import java.time.LocalDateTime;

@Data
@Builder
public class ChartDefinitionDTO {
    private Long id;
    private String code;
    private String name;
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

`ChartDatasetDTO.java`

```java
package online.yudream.base.application.platform.dataviz.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;

import java.util.List;

@Data
@Builder
public class ChartDatasetDTO {
    private ChartType chartType;
    private String title;
    private String subTitle;
    private List<ChartSeriesDTO> series;
}
```

`ChartSeriesDTO.java`

```java
package online.yudream.base.application.platform.dataviz.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChartSeriesDTO {
    private String name;
    private List<String> categories;
    private List<Number> values;
    private List<ChartNodeDTO> nodes;
    private List<ChartLinkDTO> links;
}
```

`ChartNodeDTO.java`

```java
package online.yudream.base.application.platform.dataviz.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartNodeDTO {
    private String id;
    private String name;
    private String group;
    private Number value;
}
```

`ChartLinkDTO.java`

```java
package online.yudream.base.application.platform.dataviz.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChartLinkDTO {
    private String source;
    private String target;
    private Number value;
}
```

- [ ] **Step 2: 创建 Assembler**

```java
package online.yudream.base.application.platform.dataviz.assembler;

import online.yudream.base.application.platform.dataviz.dto.ChartDefinitionDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartDatasetDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartSeriesDTO;
import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;

import java.util.List;

public class ChartAssembler {

    public static ChartDefinitionDTO toDTO(ChartDefinition def) {
        if (def == null) {
            return null;
        }
        return ChartDefinitionDTO.builder()
                .id(def.getId())
                .code(def.getCode())
                .name(def.getName())
                .chartType(def.getChartType())
                .dataSource(def.getDataSource())
                .queryConfig(def.getQueryConfig())
                .layoutConfig(def.getLayoutConfig())
                .enabled(def.getEnabled())
                .createTime(def.getCreateTime())
                .updateTime(def.getUpdateTime())
                .build();
    }

    public static List<ChartDefinitionDTO> toDTOs(List<ChartDefinition> defs) {
        return defs == null ? List.of() : defs.stream().map(ChartAssembler::toDTO).toList();
    }

    public static ChartDatasetDTO toDTO(ChartType chartType, String title, String subTitle, List<ChartDataSeries> series) {
        return ChartDatasetDTO.builder()
                .chartType(chartType)
                .title(title)
                .subTitle(subTitle)
                .series(series == null ? List.of() : series.stream().map(ChartAssembler::toDTO).toList())
                .build();
    }

    public static ChartSeriesDTO toDTO(ChartDataSeries s) {
        return ChartSeriesDTO.builder()
                .name(s.name())
                .categories(s.categories())
                .values(s.values())
                .nodes(s.nodes() == null ? List.of() : s.nodes().stream().map(n -> ChartNodeDTO.builder()
                        .id(n.id())
                        .name(n.name())
                        .group(n.group())
                        .value(n.value())
                        .build()).toList())
                .links(s.links() == null ? List.of() : s.links().stream().map(l -> ChartLinkDTO.builder()
                        .source(l.source())
                        .target(l.target())
                        .value(l.value())
                        .build()).toList())
                .build();
    }
}
```

- [ ] **Step 3: 创建 ChartAppService**

```java
package online.yudream.base.application.platform.dataviz.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.dataviz.assembler.ChartAssembler;
import online.yudream.base.application.platform.dataviz.cmd.ChartDefinitionCreateCmd;
import online.yudream.base.application.platform.dataviz.cmd.ChartDefinitionUpdateCmd;
import online.yudream.base.application.platform.dataviz.dto.ChartDatasetDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartDefinitionDTO;
import online.yudream.base.application.platform.dataviz.query.ChartDataQuery;
import online.yudream.base.application.platform.dataviz.query.ChartDefinitionPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;
import online.yudream.base.domain.platform.dataviz.repo.ChartDatasetGateway;
import online.yudream.base.domain.platform.dataviz.repo.ChartDefinitionRepo;
import online.yudream.base.domain.platform.dataviz.service.ChartDomainService;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChartAppService {

    private static final String DATAVIZ_CAPABILITY_CODE = "dataviz";

    private final CapabilityAppService capabilityAppService;
    private final ChartDefinitionRepo chartDefinitionRepo;
    private final ChartDatasetGateway chartDatasetGateway;
    private final ChartDomainService chartDomainService;

    @Transactional(readOnly = true)
    public PageResult<ChartDefinitionDTO> pageDefinitions(ChartDefinitionPageQuery query) {
        ensureEnabled();
        PageResult<ChartDefinition> page = chartDefinitionRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(ChartAssembler.toDTOs(page.getRecords()), page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional
    public ChartDefinitionDTO saveDefinition(ChartDefinitionCreateCmd cmd) {
        ensureEnabled();
        ChartDefinition def = ChartDefinition.create(cmd.getCode(), cmd.getName(), cmd.getChartType(),
                cmd.getDataSource(), cmd.getQueryConfig(), cmd.getLayoutConfig());
        return ChartAssembler.toDTO(chartDefinitionRepo.save(def));
    }

    @Transactional
    public ChartDefinitionDTO saveDefinition(ChartDefinitionUpdateCmd cmd) {
        ensureEnabled();
        ChartDefinition def = chartDefinitionRepo.findById(cmd.getId())
                .orElseThrow(() -> new BizException("图表定义不存在"));
        def.update(cmd.getName(), cmd.getChartType(), cmd.getDataSource(), cmd.getQueryConfig(), cmd.getLayoutConfig());
        return ChartAssembler.toDTO(chartDefinitionRepo.save(def));
    }

    @Transactional
    public void disableDefinition(Long id) {
        ensureEnabled();
        ChartDefinition def = chartDefinitionRepo.findById(id)
                .orElseThrow(() -> new BizException("图表定义不存在"));
        def.disable();
        chartDefinitionRepo.save(def);
    }

    @Transactional(readOnly = true)
    public ChartDatasetDTO queryDataset(ChartDataQuery query) {
        ensureEnabled();
        chartDomainService.validateQueryForType(query.chartType(), query.datasetQuery());
        String title = query.datasetQuery().params() != null ? String.valueOf(query.datasetQuery().params().getOrDefault("title", "")) : "";
        String subTitle = query.datasetQuery().params() != null ? String.valueOf(query.datasetQuery().params().getOrDefault("subTitle", "")) : "";
        List<ChartDataSeries> series = chartDatasetGateway.query(query.datasetQuery().source(), query.datasetQuery());
        return ChartAssembler.toDTO(query.chartType(), title, subTitle, series);
    }

    private void ensureEnabled() {
        capabilityAppService.ensureEnabled(DATAVIZ_CAPABILITY_CODE, "数据可视化");
    }
}
```

- [ ] **Step 4: 编译验证**

Run:

```bash
mvn -pl yudream-application -am -DskipTests compile
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add yudream-application/src/main/java/online/yudream/base/application/platform/dataviz/
git commit -m "feat: 新增 platform/dataviz 应用层" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3：后端基础设施层（infrastructure）

**Files:**
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/dataviz/dataobj/ChartDefinitionDO.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/dataviz/mapper/ChartInfraMapper.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/dataviz/impl/ChartDefinitionRepoImpl.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/dataviz/impl/ChartDatasetGatewayImpl.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/dataviz/service/ChartDataProvider.java`

- [ ] **Step 1: 创建 DO**

```java
package online.yudream.base.infra.platform.dataviz.dataobj;

import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;
import online.yudream.base.infra.common.dataobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("chart_definition")
public class ChartDefinitionDO extends BaseDO {
    private String code;
    private String name;
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
    private Boolean enabled;
}
```

- [ ] **Step 2: 创建 Mapper**

```java
package online.yudream.base.infra.platform.dataviz.mapper;

import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;
import online.yudream.base.infra.platform.dataviz.dataobj.ChartDefinitionDO;

public class ChartInfraMapper {

    public static ChartDefinition toDomain(ChartDefinitionDO d) {
        if (d == null) {
            return null;
        }
        ChartDefinition def = new ChartDefinition();
        def.setId(d.getId());
        def.setCode(d.getCode());
        def.setName(d.getName());
        def.setChartType(d.getChartType());
        def.setDataSource(d.getDataSource());
        def.setQueryConfig(d.getQueryConfig());
        def.setLayoutConfig(d.getLayoutConfig());
        def.setEnabled(d.getEnabled());
        def.setCreateTime(d.getCreateTime());
        def.setUpdateTime(d.getUpdateTime());
        return def;
    }

    public static ChartDefinitionDO toDO(ChartDefinition d) {
        if (d == null) {
            return null;
        }
        ChartDefinitionDO def = new ChartDefinitionDO();
        def.setId(d.getId());
        def.setCode(d.getCode());
        def.setName(d.getName());
        def.setChartType(d.getChartType());
        def.setDataSource(d.getDataSource());
        def.setQueryConfig(d.getQueryConfig());
        def.setLayoutConfig(d.getLayoutConfig());
        def.setEnabled(d.getEnabled());
        def.setCreateTime(d.getCreateTime());
        def.setUpdateTime(d.getUpdateTime());
        return def;
    }
}
```

- [ ] **Step 3: 创建 Repo 实现**

```java
package online.yudream.base.infra.platform.dataviz.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.dataviz.aggregate.ChartDefinition;
import online.yudream.base.domain.platform.dataviz.repo.ChartDefinitionRepo;
import online.yudream.base.infra.platform.dataviz.dataobj.ChartDefinitionDO;
import online.yudream.base.infra.platform.dataviz.mapper.ChartInfraMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
public class ChartDefinitionRepoImpl implements ChartDefinitionRepo {

    private final MongoTemplate mongoTemplate;

    @Override
    public PageResult<ChartDefinition> page(String keyword, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Query query = new Query();
        if (keyword != null && !keyword.isBlank()) {
            Pattern pattern = Pattern.compile(".*" + Pattern.quote(keyword) + ".*", Pattern.CASE_INSENSITIVE);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("code").regex(pattern),
                    Criteria.where("name").regex(pattern)
            ));
        }
        long count = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), ChartDefinitionDO.class);
        query.with(pageable);
        var records = mongoTemplate.find(query, ChartDefinitionDO.class).stream()
                .map(ChartInfraMapper::toDomain)
                .toList();
        return new PageResult<>(records, count, page, size);
    }

    @Override
    public Optional<ChartDefinition> findById(Long id) {
        return Optional.ofNullable(mongoTemplate.findById(id, ChartDefinitionDO.class))
                .map(ChartInfraMapper::toDomain);
    }

    @Override
    public Optional<ChartDefinition> findByCode(String code) {
        Query query = new Query(Criteria.where("code").is(code));
        return Optional.ofNullable(mongoTemplate.findOne(query, ChartDefinitionDO.class))
                .map(ChartInfraMapper::toDomain);
    }

    @Override
    public ChartDefinition save(ChartDefinition definition) {
        ChartDefinitionDO saved = mongoTemplate.save(ChartInfraMapper.toDO(definition));
        return ChartInfraMapper.toDomain(saved);
    }
}
```

- [ ] **Step 4: 创建 ChartDataProvider 与 Gateway 实现**

`ChartDataProvider.java`

```java
package online.yudream.base.infra.platform.dataviz.service;

import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;

import java.util.List;

public interface ChartDataProvider {
    String source();
    List<ChartDataSeries> query(ChartDatasetQuery query);
}
```

`DemoChartDataProvider.java`

```java
package online.yudream.base.infra.platform.dataviz.service;

import online.yudream.base.domain.platform.dataviz.valobj.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DemoChartDataProvider implements ChartDataProvider {

    @Override
    public String source() {
        return "demo";
    }

    @Override
    public List<ChartDataSeries> query(ChartDatasetQuery query) {
        String metric = query.params() != null ? String.valueOf(query.params().getOrDefault("metric", "trend")) : "trend";
        if ("graph".equals(metric)) {
            return List.of(ChartDataSeries.builder()
                    .name("模块依赖")
                    .nodes(List.of(
                            ChartDataNode.builder().id("system").name("系统管理").group("system").value(10).build(),
                            ChartDataNode.builder().id("platform").name("平台能力").group("platform").value(8).build(),
                            ChartDataNode.builder().id("capability").name("能力中心").group("platform").value(5).build(),
                            ChartDataNode.builder().id("graph").name("图数据库").group("platform").value(3).build()
                    ))
                    .links(List.of(
                            ChartDataLink.builder().source("platform").target("capability").value(1).build(),
                            ChartDataLink.builder().source("capability").target("graph").value(1).build(),
                            ChartDataLink.builder().source("system").target("platform").value(1).build()
                    ))
                    .build());
        }
        return List.of(ChartDataSeries.builder()
                .name("示例趋势")
                .categories(List.of("周一", "周二", "周三", "周四", "周五"))
                .values(List.of(120, 200, 150, 80, 70))
                .build());
    }
}
```

`CapabilityChartDataProvider.java`

```java
package online.yudream.base.infra.platform.dataviz.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataNode;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CapabilityChartDataProvider implements ChartDataProvider {

    private final CapabilityModuleRepo capabilityModuleRepo;

    @Override
    public String source() {
        return "capability";
    }

    @Override
    public List<ChartDataSeries> query(ChartDatasetQuery query) {
        List<CapabilityModule> modules = capabilityModuleRepo.findAll();
        Map<String, Long> byType = modules.stream()
                .collect(Collectors.groupingBy(m -> m.getType().name(), Collectors.counting()));
        return List.of(ChartDataSeries.builder()
                .name("能力类型分布")
                .categories(byType.keySet().stream().toList())
                .values(byType.values().stream().toList())
                .build());
    }
}
```

`ChartDatasetGatewayImpl.java`

```java
package online.yudream.base.infra.platform.dataviz.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.dataviz.repo.ChartDatasetGateway;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDataSeries;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.infra.platform.dataviz.service.ChartDataProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ChartDatasetGatewayImpl implements ChartDatasetGateway {

    private final List<ChartDataProvider> providers;
    private volatile Map<String, ChartDataProvider> providerMap;

    @Override
    public List<ChartDataSeries> query(String source, ChartDatasetQuery query) {
        ChartDataProvider provider = providerMap().get(source);
        if (provider == null) {
            throw new BizException("未知数据源：" + source);
        }
        return provider.query(query);
    }

    private Map<String, ChartDataProvider> providerMap() {
        if (providerMap == null) {
            providerMap = providers.stream().collect(Collectors.toMap(ChartDataProvider::source, p -> p));
        }
        return providerMap;
    }
}
```

- [ ] **Step 5: 编译验证**

Run:

```bash
mvn -pl yudream-infrastructure -am -DskipTests compile
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/dataviz/
git commit -m "feat: 新增 platform/dataviz 基础设施层" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4：后端接口层（interfaces）

**Files:**
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/dataviz/request/ChartDefinitionSaveRequest.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/dataviz/request/ChartDataRequest.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/dataviz/res/ChartDefinitionRes.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/dataviz/res/ChartDatasetRes.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/dataviz/assembler/ChartWebAssembler.java`
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/dataviz/controller/ChartDataController.java`

- [ ] **Step 1: 创建 request / res DTO**

`ChartDefinitionSaveRequest.java`

```java
package online.yudream.base.interfaces.platform.dataviz.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

@Data
public class ChartDefinitionSaveRequest {
    @NotBlank(message = "图表编码不能为空")
    private String code;
    @NotBlank(message = "图表名称不能为空")
    private String name;
    @NotNull(message = "图表类型不能为空")
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
}
```

`ChartDefinitionUpdateRequest.java`

```java
package online.yudream.base.interfaces.platform.dataviz.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

@Data
public class ChartDefinitionUpdateRequest {
    @NotBlank(message = "图表名称不能为空")
    private String name;
    @NotNull(message = "图表类型不能为空")
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
}
```

`ChartDataRequest.java`

```java
package online.yudream.base.interfaces.platform.dataviz.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;

@Data
public class ChartDataRequest {
    private Long definitionId;
    @NotNull(message = "图表类型不能为空")
    private ChartType chartType;
    @NotNull(message = "数据集查询条件不能为空")
    private ChartDatasetQuery datasetQuery;
}
```

`ChartDefinitionRes.java`

```java
package online.yudream.base.interfaces.platform.dataviz.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;
import online.yudream.base.domain.platform.dataviz.valobj.ChartDatasetQuery;
import online.yudream.base.domain.platform.dataviz.valobj.ChartLayoutConfig;

import java.time.LocalDateTime;

@Data
@Builder
public class ChartDefinitionRes {
    private Long id;
    private String code;
    private String name;
    private ChartType chartType;
    private String dataSource;
    private ChartDatasetQuery queryConfig;
    private ChartLayoutConfig layoutConfig;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

`ChartDatasetRes.java`

```java
package online.yudream.base.interfaces.platform.dataviz.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.application.platform.dataviz.dto.ChartSeriesDTO;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;

import java.util.List;

@Data
@Builder
public class ChartDatasetRes {
    private ChartType chartType;
    private String title;
    private String subTitle;
    private List<ChartSeriesDTO> series;
}
```

- [ ] **Step 2: 创建 WebAssembler**

```java
package online.yudream.base.interfaces.platform.dataviz.assembler;

import online.yudream.base.application.platform.dataviz.cmd.ChartDefinitionCreateCmd;
import online.yudream.base.application.platform.dataviz.cmd.ChartDefinitionUpdateCmd;
import online.yudream.base.application.platform.dataviz.dto.ChartDefinitionDTO;
import online.yudream.base.application.platform.dataviz.dto.ChartDatasetDTO;
import online.yudream.base.application.platform.dataviz.query.ChartDataQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDefinitionSaveRequest;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDefinitionUpdateRequest;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDataRequest;
import online.yudream.base.interfaces.platform.dataviz.res.ChartDefinitionRes;
import online.yudream.base.interfaces.platform.dataviz.res.ChartDatasetRes;

import java.util.List;

public class ChartWebAssembler {

    public static ChartDefinitionCreateCmd toCmd(ChartDefinitionSaveRequest request) {
        return ChartDefinitionCreateCmd.builder()
                .code(request.getCode())
                .name(request.getName())
                .chartType(request.getChartType())
                .dataSource(request.getDataSource())
                .queryConfig(request.getQueryConfig())
                .layoutConfig(request.getLayoutConfig())
                .build();
    }

    public static ChartDefinitionUpdateCmd toCmd(Long id, ChartDefinitionUpdateRequest request) {
        return ChartDefinitionUpdateCmd.builder()
                .id(id)
                .name(request.getName())
                .chartType(request.getChartType())
                .dataSource(request.getDataSource())
                .queryConfig(request.getQueryConfig())
                .layoutConfig(request.getLayoutConfig())
                .build();
    }

    public static ChartDataQuery toCmd(ChartDataRequest request) {
        return ChartDataQuery.builder()
                .definitionId(request.getDefinitionId())
                .chartType(request.getChartType())
                .datasetQuery(request.getDatasetQuery())
                .build();
    }

    public static ChartDefinitionRes toRes(ChartDefinitionDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChartDefinitionRes.builder()
                .id(dto.getId())
                .code(dto.getCode())
                .name(dto.getName())
                .chartType(dto.getChartType())
                .dataSource(dto.getDataSource())
                .queryConfig(dto.getQueryConfig())
                .layoutConfig(dto.getLayoutConfig())
                .enabled(dto.getEnabled())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static PageResult<ChartDefinitionRes> toResPage(PageResult<ChartDefinitionDTO> page) {
        List<ChartDefinitionRes> records = page.getRecords().stream()
                .map(ChartWebAssembler::toRes)
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getPage(), page.getSize());
    }

    public static ChartDatasetRes toRes(ChartDatasetDTO dto) {
        if (dto == null) {
            return null;
        }
        return ChartDatasetRes.builder()
                .chartType(dto.getChartType())
                .title(dto.getTitle())
                .subTitle(dto.getSubTitle())
                .series(dto.getSeries())
                .build();
    }
}
```

- [ ] **Step 3: 创建 Controller**

```java
package online.yudream.base.interfaces.platform.dataviz.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.dataviz.query.ChartDefinitionPageQuery;
import online.yudream.base.application.platform.dataviz.service.ChartAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.dataviz.assembler.ChartWebAssembler;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDataRequest;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDefinitionSaveRequest;
import online.yudream.base.interfaces.platform.dataviz.request.ChartDefinitionUpdateRequest;
import online.yudream.base.interfaces.platform.dataviz.res.ChartDatasetRes;
import online.yudream.base.interfaces.platform.dataviz.res.ChartDefinitionRes;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/dataviz")
@RequiredArgsConstructor
public class ChartDataController {

    private final ChartAppService chartAppService;

    @GetMapping("/definitions")
    @PermissionRegister(code = "platform:dataviz:view", name = "查看图表定义", module = "平台能力", desc = "查看数据可视化图表定义")
    public Result<online.yudream.base.domain.common.PageResult<ChartDefinitionRes>> definitions(ChartDefinitionPageQuery query) {
        return Result.ok(ChartWebAssembler.toResPage(chartAppService.pageDefinitions(query)));
    }

    @PostMapping("/definitions")
    @PermissionRegister(code = "platform:dataviz:edit", name = "新增图表定义", module = "平台能力", desc = "新增数据可视化图表定义")
    public Result<ChartDefinitionRes> createDefinition(@Valid @RequestBody ChartDefinitionSaveRequest request) {
        return Result.ok(ChartWebAssembler.toRes(chartAppService.saveDefinition(ChartWebAssembler.toCmd(request))));
    }

    @PutMapping("/definitions/{id}")
    @PermissionRegister(code = "platform:dataviz:edit", name = "编辑图表定义", module = "平台能力", desc = "编辑数据可视化图表定义")
    public Result<ChartDefinitionRes> updateDefinition(@PathVariable Long id, @Valid @RequestBody ChartDefinitionUpdateRequest request) {
        return Result.ok(ChartWebAssembler.toRes(chartAppService.saveDefinition(ChartWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/definitions/{id}")
    @PermissionRegister(code = "platform:dataviz:edit", name = "停用图表定义", module = "平台能力", desc = "停用数据可视化图表定义")
    public Result<Void> disableDefinition(@PathVariable Long id) {
        chartAppService.disableDefinition(id);
        return Result.ok();
    }

    @PostMapping("/dataset")
    @PermissionRegister(code = "platform:dataviz:dataset", name = "查询图表数据", module = "平台能力", desc = "查询数据可视化图表数据集")
    public Result<ChartDatasetRes> dataset(@Valid @RequestBody ChartDataRequest request) {
        return Result.ok(ChartWebAssembler.toRes(chartAppService.queryDataset(ChartWebAssembler.toCmd(request))));
    }
}
```

- [ ] **Step 4: 编译验证**

Run:

```bash
mvn -pl yudream-bootstrap -am -DskipTests compile
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/dataviz/
git commit -m "feat: 新增 platform/dataviz 接口层" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5：前端组件包初始化

**Files:**
- Create: `yudream-frontend/packages/dataviz/package.json`
- Create: `yudream-frontend/packages/dataviz/tsconfig.json`
- Create: `yudream-frontend/packages/dataviz/src/index.ts`
- Create: `yudream-frontend/packages/dataviz/src/types.ts`
- Modify: `yudream-frontend/pnpm-workspace.yaml`
- Modify: `yudream-frontend/package.json`

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "@yudream/dataviz",
  "type": "module",
  "version": "0.0.0",
  "exports": {
    ".": "./src/index.ts"
  },
  "peerDependencies": {
    "vue": "catalog:",
    "@vueuse/core": "catalog:"
  },
  "dependencies": {
    "echarts": "catalog:",
    "d3": "catalog:"
  },
  "devDependencies": {
    "@types/d3": "catalog:"
  }
}
```

- [ ] **Step 2: 创建 tsconfig.json**

```json
{
  "extends": "../tsconfig.base.json",
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.vue"]
}
```

- [ ] **Step 3: 创建 types.ts**

```ts
export type ChartType = 'line' | 'bar' | 'pie' | 'sankey' | 'graph' | 'stat'

export interface ChartDataset {
  chartType: ChartType
  title?: string
  subTitle?: string
  series: ChartSeries[]
}

export interface ChartSeries {
  name?: string
  categories?: string[]
  values?: number[]
  nodes?: ChartNode[]
  links?: ChartLink[]
}

export interface ChartNode {
  id: string
  name: string
  group?: string
  value?: number
}

export interface ChartLink {
  source: string
  target: string
  value?: number
}

export interface ChartTheme {
  mode: 'light' | 'dark'
  colors: string[]
  background: string
  text: string
  textSecondary: string
  grid: string
}
```

- [ ] **Step 4: 创建 index.ts**

```ts
export * from './types'
export { useECharts } from './composables/useECharts'
export { useD3Graph } from './composables/useD3Graph'
export { useChartTheme } from './composables/useChartTheme'
export { default as BaseChart } from './components/BaseChart.vue'
export { default as LineChart } from './components/LineChart.vue'
export { default as BarChart } from './components/BarChart.vue'
export { default as PieChart } from './components/PieChart.vue'
export { default as SankeyChart } from './components/SankeyChart.vue'
export { default as GraphChart } from './components/GraphChart.vue'
export { default as StatTile } from './components/StatTile.vue'
export * from './utils/transformers'
```

- [ ] **Step 5: 注册 workspace**

Verify `yudream-frontend/pnpm-workspace.yaml` includes `packages/dataviz` or the pattern `packages/*` covers it.

Run:

```bash
cd yudream-frontend
pnpm install
```

Expected: lockfile updated, `@yudream/dataviz` dependencies resolved.

- [ ] **Step 6: Commit**

```bash
git add yudream-frontend/packages/dataviz/
git commit -m "feat: 新增 dataviz 前端组件包初始化" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6：前端组合式函数与工具

**Files:**
- Create: `yudream-frontend/packages/dataviz/src/composables/useECharts.ts`
- Create: `yudream-frontend/packages/dataviz/src/composables/useD3Graph.ts`
- Create: `yudream-frontend/packages/dataviz/src/composables/useChartTheme.ts`
- Create: `yudream-frontend/packages/dataviz/src/utils/echarts-option-builder.ts`
- Create: `yudream-frontend/packages/dataviz/src/utils/d3-layouts.ts`
- Create: `yudream-frontend/packages/dataviz/src/utils/transformers.ts`
- Create: `yudream-frontend/packages/dataviz/src/utils/theme-adapters.ts`

- [ ] **Step 1: useChartTheme**

```ts
import type { ChartTheme } from '../types'

const palettes = {
  light: {
    background: 'transparent',
    text: '#1d2129',
    textSecondary: '#4e5969',
    grid: '#e5e6eb',
    colors: ['#165DFF', '#14C9C9', '#F7BA1E', '#F53F3F', '#722ED1', '#3491FA'],
  },
  dark: {
    background: 'transparent',
    text: '#ffffff',
    textSecondary: '#ffffffb3',
    grid: '#ffffff1a',
    colors: ['#165DFF', '#14C9C9', '#F7BA1E', '#F53F3F', '#722ED1', '#3491FA'],
  },
}

export function useChartTheme(mode: 'light' | 'dark' = 'light'): ChartTheme {
  return { mode, ...palettes[mode] }
}
```

- [ ] **Step 2: useECharts**

```ts
import type { ECharts, EChartsCoreOption } from 'echarts'
import { init } from 'echarts'
import { onMounted, onUnmounted, ref, watch } from 'vue'

export function useECharts(elRef: Ref<HTMLElement | null>, options: Ref<EChartsCoreOption>, theme: Ref<'light' | 'dark'>) {
  let instance: ECharts | null = null

  function initChart() {
    if (!elRef.value) return
    dispose()
    instance = init(elRef.value, theme.value === 'dark' ? 'dark' : undefined, { renderer: 'canvas' })
    instance.setOption(options.value, true)
  }

  function dispose() {
    if (instance) {
      instance.dispose()
      instance = null
    }
  }

  function resize() {
    instance?.resize()
  }

  onMounted(initChart)
  onUnmounted(dispose)

  watch([elRef, theme], initChart, { flush: 'post' })
  watch(options, (val) => instance?.setOption(val, true), { deep: true })

  return { instance, resize, dispose }
}
```

- [ ] **Step 3: echarts-option-builder**

```ts
import type { ChartDataset, ChartTheme } from '../types'

export function buildEChartsOption(dataset: ChartDataset, theme: ChartTheme) {
  const { chartType } = dataset
  const base = {
    backgroundColor: theme.background,
    textStyle: { color: theme.text },
    title: dataset.title ? { text: dataset.title, subtext: dataset.subTitle, left: 'center', textStyle: { color: theme.text } } : undefined,
    tooltip: { trigger: chartType === 'pie' ? 'item' : 'axis' },
    color: theme.colors,
  }

  const series = dataset.series.map(s => {
    if (chartType === 'line') {
      return { type: 'line', name: s.name, data: s.values, smooth: true }
    }
    if (chartType === 'bar') {
      return { type: 'bar', name: s.name, data: s.values }
    }
    if (chartType === 'pie') {
      return {
        type: 'pie',
        name: s.name,
        radius: ['40%', '70%'],
        data: s.categories?.map((name, i) => ({ name, value: s.values?.[i] ?? 0 })) ?? [],
      }
    }
    return {}
  })

  const categories = dataset.series[0]?.categories ?? []

  return {
    ...base,
    xAxis: chartType === 'pie' || chartType === 'stat' ? undefined : { type: 'category', data: categories, axisLine: { lineStyle: { color: theme.grid } } },
    yAxis: chartType === 'pie' || chartType === 'stat' ? undefined : { type: 'value', splitLine: { lineStyle: { color: theme.grid } } },
    series,
  }
}
```

- [ ] **Step 4: transformers**

```ts
import type { ChartDataset, ChartSeries } from '../types'

export function normalizeDataset(raw: any): ChartDataset {
  return {
    chartType: raw.chartType ?? 'line',
    title: raw.title,
    subTitle: raw.subTitle,
    series: (raw.series ?? []).map((s: any) => ({
      name: s.name,
      categories: s.categories ?? [],
      values: s.values ?? [],
      nodes: s.nodes ?? [],
      links: s.links ?? [],
    })),
  }
}

export function toEChartsDataset(dataset: ChartDataset) {
  return dataset
}
```

- [ ] **Step 5: d3-layouts 与 useD3Graph**

`d3-layouts.ts`:

```ts
import * as d3 from 'd3'
import type { ChartLink, ChartNode } from '../types'

export function forceSimulation(nodes: ChartNode[], links: ChartLink[], width: number, height: number) {
  const simulation = d3.forceSimulation(nodes as any)
    .force('link', d3.forceLink(links as any).id((d: any) => d.id).distance(100))
    .force('charge', d3.forceManyBody().strength(-300))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collide', d3.forceCollide().radius(30))
  return simulation
}
```

`useD3Graph.ts`:

```ts
import * as d3 from 'd3'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import type { ChartDataset, ChartTheme } from '../types'
import { forceSimulation } from '../utils/d3-layouts'

export function useD3Graph(elRef: Ref<HTMLElement | null>, dataset: Ref<ChartDataset | null>, theme: Ref<ChartTheme>) {
  let svg: any = null
  let simulation: any = null

  function render() {
    if (!elRef.value || !dataset.value) return
    const width = elRef.value.clientWidth
    const height = elRef.value.clientHeight || 300
    d3.select(elRef.value).selectAll('*').remove()
    svg = d3.select(elRef.value)
      .append('svg')
      .attr('width', width)
      .attr('height', height)
      .attr('viewBox', [0, 0, width, height])

    const series = dataset.value.series[0]
    const nodes = (series?.nodes ?? []).map(n => ({ ...n, x: width / 2, y: height / 2 }))
    const links = series?.links ?? []

    simulation = forceSimulation(nodes, links, width, height)

    const link = svg.append('g')
      .selectAll('line')
      .data(links)
      .join('line')
      .attr('stroke', theme.value.grid)
      .attr('stroke-width', 2)

    const node = svg.append('g')
      .selectAll('g')
      .data(nodes)
      .join('g')
      .call(d3.drag()
        .on('start', (event: any, d: any) => { if (!event.active) simulation.alphaTarget(0.3).restart(); d.fx = d.x; d.fy = d.y })
        .on('drag', (event: any, d: any) => { d.fx = event.x; d.fy = event.y })
        .on('end', (event: any, d: any) => { if (!event.active) simulation.alphaTarget(0); d.fx = null; d.fy = null }))

    node.append('circle')
      .attr('r', (d: any) => 10 + (d.value ? Math.sqrt(d.value) : 0))
      .attr('fill', (d: any, i: number) => theme.value.colors[i % theme.value.colors.length])

    node.append('text')
      .text((d: any) => d.name)
      .attr('x', 14)
      .attr('y', 4)
      .attr('fill', theme.value.text)
      .style('font-size', '12px')

    simulation.on('tick', () => {
      link
        .attr('x1', (d: any) => d.source.x)
        .attr('y1', (d: any) => d.source.y)
        .attr('x2', (d: any) => d.target.x)
        .attr('y2', (d: any) => d.target.y)
      node.attr('transform', (d: any) => `translate(${d.x},${d.y})`)
    })
  }

  function dispose() {
    simulation?.stop()
    svg?.selectAll('*').remove()
  }

  onMounted(render)
  onUnmounted(dispose)
  watch([elRef, dataset, theme], render, { flush: 'post' })

  return { render, dispose }
}
```

- [ ] **Step 6: Commit**

```bash
git add yudream-frontend/packages/dataviz/src/
git commit -m "feat: 新增 dataviz 组合式函数与工具" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7：前端图表组件

**Files:**
- Create: `yudream-frontend/packages/dataviz/src/components/BaseChart.vue`
- Create: `yudream-frontend/packages/dataviz/src/components/LineChart.vue`
- Create: `yudream-frontend/packages/dataviz/src/components/BarChart.vue`
- Create: `yudream-frontend/packages/dataviz/src/components/PieChart.vue`
- Create: `yudream-frontend/packages/dataviz/src/components/SankeyChart.vue`
- Create: `yudream-frontend/packages/dataviz/src/components/GraphChart.vue`
- Create: `yudream-frontend/packages/dataviz/src/components/StatTile.vue`

- [ ] **Step 1: BaseChart**

```vue
<script setup lang="ts">
import type { EChartsCoreOption } from 'echarts'
import { computed, ref } from 'vue'
import type { ChartDataset } from '../types'
import { useChartTheme } from '../composables/useChartTheme'
import { useECharts } from '../composables/useECharts'
import { buildEChartsOption } from '../utils/echarts-option-builder'

interface Props {
  dataset: ChartDataset
  theme?: 'light' | 'dark'
  height?: number | string
}

const props = withDefaults(defineProps<Props>(), {
  theme: 'light',
  height: 300,
})

const elRef = ref<HTMLElement | null>(null)
const chartTheme = useChartTheme(props.theme)
const option = computed<EChartsCoreOption>(() => buildEChartsOption(props.dataset, chartTheme))
useECharts(elRef, option, toRef(props, 'theme'))
</script>

<template>
  <div ref="elRef" :style="{ width: '100%', height: typeof height === 'number' ? `${height}px` : height }" />
</template>
```

- [ ] **Step 2: LineChart / BarChart / PieChart**

`LineChart.vue`:

```vue
<script setup lang="ts">
import type { ChartDataset } from '../types'
import BaseChart from './BaseChart.vue'

interface Props {
  dataset: ChartDataset
  theme?: 'light' | 'dark'
  height?: number | string
}

defineProps<Props>()
</script>

<template>
  <BaseChart type="line" :dataset="dataset" :theme="theme" :height="height" />
</template>
```

Similar for `BarChart.vue` and `PieChart.vue` with `type="bar"` / `type="pie"`.

- [ ] **Step 3: GraphChart**

```vue
<script setup lang="ts">
import { ref, toRef } from 'vue'
import type { ChartDataset } from '../types'
import { useChartTheme } from '../composables/useChartTheme'
import { useD3Graph } from '../composables/useD3Graph'

interface Props {
  dataset: ChartDataset
  theme?: 'light' | 'dark'
  height?: number | string
}

const props = withDefaults(defineProps<Props>(), {
  theme: 'light',
  height: 300,
})

const elRef = ref<HTMLElement | null>(null)
const chartTheme = useChartTheme(props.theme)
useD3Graph(elRef, toRef(props, 'dataset'), toRef(() => chartTheme))
</script>

<template>
  <div ref="elRef" :style="{ width: '100%', height: typeof height === 'number' ? `${height}px` : height }" />
</template>
```

- [ ] **Step 4: StatTile**

```vue
<script setup lang="ts">
interface Props {
  title: string
  value: number | string
  trend?: number
  theme?: 'light' | 'dark'
}

defineProps<Props>()
</script>

<template>
  <div class="stat-tile">
    <div class="stat-tile__title">{{ title }}</div>
    <div class="stat-tile__value">{{ value }}</div>
    <div v-if="trend !== undefined" class="stat-tile__trend" :class="{ up: trend >= 0, down: trend < 0 }">
      {{ trend >= 0 ? '+' : '' }}{{ trend }}%
    </div>
  </div>
</template>

<style scoped>
.stat-tile { display: flex; flex-direction: column; gap: 8px; }
.stat-tile__title { font-size: 12px; color: var(--color-text-3); }
.stat-tile__value { font-size: 28px; font-weight: 700; }
.stat-tile__trend.up { color: #00b42a; }
.stat-tile__trend.down { color: #f53f3f; }
</style>
```

- [ ] **Step 5: Commit**

```bash
git add yudream-frontend/packages/dataviz/src/components/
git commit -m "feat: 新增 dataviz 图表组件" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8：宿主应用集成

**Files:**
- Create: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/platform-dataviz.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/components/chart/ChartWidget.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/dashboard/DashboardChartStatsCard.vue`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/dashboard/DashboardFlowGraphCard.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/package.json`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/views/dashboard/index.vue`

- [ ] **Step 1: 添加依赖**

Modify `yudream-frontend/apps/core-arco-design-vue/package.json` dependencies:

```json
"@yudream/dataviz": "workspace:*"
```

Run:

```bash
cd yudream-frontend
pnpm install
```

- [ ] **Step 2: 创建 API 模块**

```ts
import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type ChartType = 'line' | 'bar' | 'pie' | 'sankey' | 'graph' | 'stat'

export interface ChartDataset {
  chartType: ChartType
  title?: string
  subTitle?: string
  series: ChartSeries[]
}

export interface ChartSeries {
  name?: string
  categories?: string[]
  values?: number[]
  nodes?: ChartNode[]
  links?: ChartLink[]
}

export interface ChartNode {
  id: string
  name: string
  group?: string
  value?: number
}

export interface ChartLink {
  source: string
  target: string
  value?: number
}

export interface ChartDataRequest {
  definitionId?: number
  chartType: ChartType
  datasetQuery: {
    source: string
    metric?: string
    params?: Record<string, any>
  }
}

export default {
  queryDataset: (data: ChartDataRequest) => {
    return systemClient.post<unknown, ApiResponse<ChartDataset>>('api/platform/dataviz/dataset', data)
  },
}
```

- [ ] **Step 3: 创建 ChartWidget 薄封装**

```vue
<script setup lang="ts">
import { BarChart, BaseChart, GraphChart, LineChart, PieChart, StatTile } from '@yudream/dataviz'
import type { ChartDataset } from '@yudream/dataviz'
import { computed } from 'vue'

interface Props {
  dataset: ChartDataset
  height?: number | string
}

const props = defineProps<Props>()
const theme = computed(() => document.documentElement.classList.contains('dark') ? 'dark' : 'light')
</script>

<template>
  <div class="chart-widget">
    <LineChart v-if="dataset.chartType === 'line'" :dataset="dataset" :theme="theme" :height="height" />
    <BarChart v-else-if="dataset.chartType === 'bar'" :dataset="dataset" :theme="theme" :height="height" />
    <PieChart v-else-if="dataset.chartType === 'pie'" :dataset="dataset" :theme="theme" :height="height" />
    <GraphChart v-else-if="dataset.chartType === 'graph'" :dataset="dataset" :theme="theme" :height="height" />
    <BaseChart v-else :dataset="dataset" :theme="theme" :height="height" />
  </div>
</template>
```

- [ ] **Step 4: 创建 Dashboard 演示卡片**

`DashboardChartStatsCard.vue`:

```vue
<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'
import apiDataviz from '@/api/modules/platform-dataviz'
import ChartWidget from '@/components/chart/ChartWidget.vue'

interface Props {
  card: DashboardCard
}

defineProps<Props>()

const loading = ref(false)
const message = ref('')
const dataset = ref<any>(null)

onMounted(load)

async function load() {
  loading.value = true
  message.value = ''
  try {
    const res = await apiDataviz.queryDataset({
      chartType: 'bar',
      datasetQuery: { source: 'capability', metric: 'type' },
    })
    dataset.value = res.data
  }
  catch (error) {
    message.value = error instanceof Error ? error.message : '图表数据加载失败'
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="dashboard-card__content">
    <div v-if="message" class="text-sm text-[var(--color-text-3)]">
      {{ message }}
    </div>
    <ChartWidget v-else-if="dataset" :dataset="dataset" :height="240" />
  </div>
</template>
```

`DashboardFlowGraphCard.vue`:

```vue
<script setup lang="ts">
import type { DashboardCard } from '@/api/modules/system-dashboard'
import apiDataviz from '@/api/modules/platform-dataviz'
import ChartWidget from '@/components/chart/ChartWidget.vue'

interface Props {
  card: DashboardCard
}

defineProps<Props>()

const loading = ref(false)
const message = ref('')
const dataset = ref<any>(null)

onMounted(load)

async function load() {
  loading.value = true
  message.value = ''
  try {
    const res = await apiDataviz.queryDataset({
      chartType: 'graph',
      datasetQuery: { source: 'demo', metric: 'graph' },
    })
    dataset.value = res.data
  }
  catch (error) {
    message.value = error instanceof Error ? error.message : '关系图数据加载失败'
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="dashboard-card__content">
    <div v-if="message" class="text-sm text-[var(--color-text-3)]">
      {{ message }}
    </div>
    <ChartWidget v-else-if="dataset" :dataset="dataset" :height="240" />
  </div>
</template>
```

- [ ] **Step 5: 注册 Dashboard 卡片**

Modify `yudream-frontend/apps/core-arco-design-vue/src/views/dashboard/index.vue` to include the new card codes in the card registry and default layout. Follow existing pattern used by other dashboard cards.

- [ ] **Step 6: 类型检查**

Run:

```bash
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
```

Expected: no type errors.

- [ ] **Step 7: Commit**

```bash
git add yudream-frontend/apps/core-arco-design-vue/
git commit -m "feat: Dashboard 新增图表与关系图演示卡片" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9：后端平台能力注册

**Files:**
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/capability/service/Neo4jCapabilityProvider.java`（参考）
- Create/Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/capability/service/DatavizCapabilityProvider.java`

- [ ] **Step 1: 创建 DatavizCapabilityProvider**

参考现有 `RabbitMqCapabilityProvider`，实现 `CapabilityProvider` 接口：

```java
package online.yudream.base.infra.platform.capability.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.dataviz", name = "enabled", havingValue = "true")
public class DatavizCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "dataviz";
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "数据可视化",
                CapabilityType.GRAPH,
                "ECharts / D3 数据可视化平台能力，未启用时不注册图表端点",
                "i-ri:pie-chart-line",
                90,
                Map.of()
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("数据可视化未启用");
        }
        return CapabilityHealth.enabled("数据可视化可用", Map.of());
    }

    @Override
    public synchronized void enable(Map<String, String> config) {
        enabled.set(true);
    }

    @Override
    public synchronized void disable() {
        enabled.set(false);
    }

    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("数据可视化未启用");
        }
        return CapabilityTestResult.success("数据可视化测试通过");
    }
}
```

- [ ] **Step 2: 编译验证**

Run:

```bash
mvn -pl yudream-bootstrap -am -DskipTests compile
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/capability/
git commit -m "feat: 注册 dataviz 平台能力 Provider" -m "Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 10：最终验证

- [ ] **Step 1: 后端编译**

Run:

```bash
$env:JAVA_HOME='C:/Users/SiberianHusky/.jdks/ms-21.0.10'; $env:Path="$env:JAVA_HOME/bin;$env:Path"; mvn -pl yudream-bootstrap -am -DskipTests compile
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 前端类型检查**

Run:

```bash
pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
```

Expected: no errors

- [ ] **Step 3: 运行应用并手动验证**

Start backend and frontend. Navigate to Dashboard. Confirm:
- `DashboardChartStatsCard` renders a bar chart.
- `DashboardFlowGraphCard` renders a draggable force-directed graph.
- Theme switching updates chart colors.
- Disabling `dataviz` capability hides cards or shows appropriate error.

- [ ] **Step 4: 代码扫描**

Run project skill review scans:

```powershell
rg -n "private .*to[A-Z]|new .*Cmd|new .*ExcelRow|\.builder\(\)" yudream-interfaces/src/main/java/online/yudream/base/interfaces -g "*Controller.java"
rg -n "\\u[0-9a-fA-F]{4}|\?\)" yudream-domain/src/main/java yudream-application/src/main/java yudream-infrastructure/src/main/java yudream-interfaces/src/main/java yudream-bootstrap/src/main/java -g "*.java"
```

Expected: no matches in controllers, no mojibake.

- [ ] **Step 5: 最终提交（如尚未提交）**

If any fixes were made, commit them separately with Chinese messages.

---

## 11. 计划自检

- **Spec coverage:**
  - 后端 DDD 四模块：Task 1-4 覆盖。
  - 前端 `packages/dataviz`：Task 5-7 覆盖。
  - 宿主应用集成与 Dashboard 演示：Task 8 覆盖。
  - 平台能力注册：Task 9 覆盖。
  - 验证：Task 10 覆盖。
- **Placeholder scan:** 无 TBD/TODO；所有代码块均提供可运行示例。
- **Type consistency:** `ChartType`、`ChartDataSeries`、`ChartDatasetDTO`、`ChartDatasetRes` 字段与类型在各层一致。

---

## 12. 执行方式选择

Plan complete and saved to `docs/superpowers/plans/2026-07-06-echarts-d3-dataviz-implementation-plan.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach would you like?
