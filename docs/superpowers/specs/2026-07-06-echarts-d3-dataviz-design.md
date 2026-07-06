# ECharts / D3 数据可视化封装设计文档

- **日期**：2026-07-06
- **目标**：在后端与前端加入 ECharts / D3 数据流（数据可视化）封装，严格遵循项目 DDD 架构、前端 skills 与现有设计模式。
- **方案**：方案 B（DDD 平台能力方案）

---

## 1. 背景与范围

### 1.1 需求摘要

- **前端**：封装一套可复用的图表组件，覆盖常规统计图（ECharts）与数据流/关系图（D3）。
- **后端**：按 DDD 平台能力规范，提供图表定义管理与通用数据集查询接口。
- **演示**：在现有 Dashboard 中新增两张卡片，分别展示 ECharts 统计图与 D3 关系图。

### 1.2 不碰的范围

- 不修改现有 `platform/graph`（Neo4j）核心逻辑，仅在需要时复用其数据格式作为演示数据源之一。
- 不替换 Dashboard 全部现有卡片。
- 不引入新的包管理器或构建工具。

---

## 2. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3)                          │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  apps/core-arco-design-vue                            │  │
│  │  - views/dashboard/DashboardChartStatsCard.vue        │  │
│  │  - views/dashboard/DashboardFlowGraphCard.vue         │  │
│  │  - components/chart/ChartWidget.vue（薄封装）          │  │
│  └──────────────────┬────────────────────────────────────┘  │
│                     │ workspace 依赖                          │
│  ┌──────────────────▼────────────────────────────────────┐  │
│  │  packages/dataviz                                      │  │
│  │  - composables/useECharts.ts                           │  │
│  │  - composables/useD3Graph.ts                           │  │
│  │  - composables/useChartTheme.ts                        │  │
│  │  - components/{BaseChart,LineChart,BarChart,...}.vue   │  │
│  │  - utils/echarts-option-builder.ts                     │  │
│  │  - utils/d3-layouts.ts                                 │  │
│  │  - utils/transformers.ts                               │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │ API 调用
┌─────────────────────────────────────────────────────────────┐
│                        后端 (Spring Boot)                    │
│  yudream-interfaces   yudream-application   yudream-domain  │
│  - ChartDataController   - ChartAppService   - ChartDefinition│
│  - ChartWebAssembler     - ChartAssembler    - ChartType enum │
│  - request/res           - cmd/query/dto     - repo/gateway   │
│                                                             │
│  yudream-infrastructure                                      │
│  - ChartDefinitionRepoImpl                                   │
│  - ChartDatasetGatewayImpl                                   │
│  - ChartDataProvider（可扩展数据源）                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 后端 DDD 设计

### 3.1 领域层（yudream-domain）

包路径：`online.yudream.base.domain.platform.dataviz`

| 包/文件 | 职责 |
|---|---|
| `aggregate/ChartDefinition.java` | 图表定义聚合根，包含 code、name、chartType、dataSource、queryConfig、layoutConfig、status 等字段 |
| `enumerate/ChartType.java` | `LINE`, `BAR`, `PIE`, `SANKEY`, `GRAPH`, `STAT` |
| `valobj/ChartDatasetQuery.java` | 数据集查询条件值对象 |
| `valobj/ChartDataSeries.java` | 单个数据系列 |
| `valobj/ChartLayoutConfig.java` | 布局与主题配置 |
| `repo/ChartDefinitionRepo.java` | 图表定义持久化接口 |
| `repo/ChartDatasetGateway.java` | 数据集查询网关接口 |
| `service/ChartDomainService.java` | 跨聚合校验、默认数据集解析规则 |

聚合根核心方法：
- `ChartDefinition.create(...)`
- `update(name, chartType, dataSource, queryConfig, layoutConfig)`
- `disable()`
- `active()`

### 3.2 应用层（yudream-application）

包路径：`online.yudream.base.application.platform.dataviz`

| 包/文件 | 职责 |
|---|---|
| `cmd/ChartDefinitionCreateCmd.java` | 创建图表定义命令 |
| `cmd/ChartDefinitionUpdateCmd.java` | 更新图表定义命令 |
| `query/ChartDefinitionPageQuery.java` | 图表定义分页查询 |
| `query/ChartDataQuery.java` | 数据集查询 |
| `dto/ChartDefinitionDTO.java` | 图表定义输出 |
| `dto/ChartDatasetDTO.java` | 数据集输出 |
| `dto/ChartSeriesDTO.java` | 系列输出 |
| `assembler/ChartAssembler.java` | domain ↔ dto 转换 |
| `service/ChartAppService.java` | 用例编排 |

核心用例：
- `PageResult<ChartDefinitionDTO> pageDefinitions(ChartDefinitionPageQuery query)`
- `ChartDefinitionDTO saveDefinition(ChartDefinitionCreateCmd | ChartDefinitionUpdateCmd cmd)`
- `void disableDefinition(Long id)`
- `ChartDatasetDTO queryDataset(ChartDataQuery query)`

### 3.3 基础设施层（yudream-infrastructure）

包路径：`online.yudream.base.infra.platform.dataviz`

| 包/文件 | 职责 |
|---|---|
| `dataobj/ChartDefinitionDO.java` | Mongo 持久化对象 |
| `mapper/ChartInfraMapper.java` | DO ↔ domain 转换 |
| `impl/ChartDefinitionRepoImpl.java` | repo 实现 |
| `impl/ChartDatasetGatewayImpl.java` | 默认数据集网关实现 |
| `service/ChartDataProvider.java` | 按 `source` 注册的数据提供者 SPI |

默认提供两类数据源：
- `demo`：返回内置示例数据，用于 Dashboard 演示卡片。
- `capability`：从现有 `CapabilityModule` 聚合平台能力统计。

### 3.4 接口层（yudream-interfaces）

包路径：`online.yudream.base.interfaces.platform.dataviz`

| 包/文件 | 职责 |
|---|---|
| `controller/ChartDataController.java` | HTTP 路由、权限注解、参数校验 |
| `assembler/ChartWebAssembler.java` | request ↔ cmd，dto ↔ res |
| `request/ChartDefinitionSaveRequest.java` | 保存请求体 |
| `request/ChartDataRequest.java` | 数据集查询请求体 |
| `res/ChartDefinitionRes.java` | 图表定义响应 |
| `res/ChartDatasetRes.java` | 数据集响应 |

端点：
- `GET /api/platform/dataviz/definitions`
- `POST /api/platform/dataviz/definitions`
- `PUT /api/platform/dataviz/definitions/{id}`
- `DELETE /api/platform/dataviz/definitions/{id}`
- `POST /api/platform/dataviz/dataset`

权限：
- `platform:dataviz:view`
- `platform:dataviz:edit`
- `platform:dataviz:dataset`

### 3.5 平台能力集成

按 `yudream-ddd-architecture` 平台能力规范：
- 项目门控：`yudream.platform.capabilities.dataviz.enabled=true`（默认 `false`）。
- 应用门控：`ChartAppService` 在每个用例入口调用 `capabilityAppService.ensureEnabled("dataviz", "数据可视化")`。
- 禁用时：不初始化 ChartDataProvider，不注册菜单/权限，不创建外部连接。

---

## 4. 前端组件包设计

### 4.1 包位置

`yudream-frontend/packages/dataviz`

### 4.2 目录结构

```
packages/dataviz
├── package.json
├── tsconfig.json
├── src
│   ├── index.ts
│   ├── types.ts
│   ├── composables
│   │   ├── useECharts.ts
│   │   ├── useD3Graph.ts
│   │   └── useChartTheme.ts
│   ├── components
│   │   ├── BaseChart.vue
│   │   ├── LineChart.vue
│   │   ├── BarChart.vue
│   │   ├── PieChart.vue
│   │   ├── SankeyChart.vue
│   │   ├── GraphChart.vue
│   │   └── StatTile.vue
│   ├── utils
│   │   ├── echarts-option-builder.ts
│   │   ├── d3-layouts.ts
│   │   ├── transformers.ts
│   │   └── theme-adapters.ts
│   └── styles
│       └── chart.css
```

### 4.3 依赖

- `echarts`
- `d3`
- `vue`、`@vueuse/core`（catalog）
- 不依赖 `@arco-design/web-vue`，保持通用。

### 4.4 组件接口

```ts
// BaseChart
interface BaseChartProps {
  type: 'line' | 'bar' | 'pie'
  dataset: ChartDataset
  theme?: 'light' | 'dark'
  loading?: boolean
  height?: number | string
}

// GraphChart / SankeyChart
interface D3ChartProps {
  type: 'sankey' | 'graph'
  dataset: ChartDataset
  theme?: 'light' | 'dark'
  nodeDraggable?: boolean
  height?: number | string
}

// StatTile
interface StatTileProps {
  title: string
  value: number | string
  trend?: number
  theme?: 'light' | 'dark'
}
```

### 4.5 宿主应用薄封装

`apps/core-arco-design-vue/src/components/chart/ChartWidget.vue`：
- 自动读取当前 Arco 主题，推导 `theme`。
- 统一错误提示：`useFaToast`。
- 权限控制：`v-auth`。

### 4.6 数据流

```
Backend ChartDatasetRes
       ↓
api/modules/platform-dataviz.ts
       ↓
packages/dataviz utils/transformers.ts
       ↓
echarts option / d3 datum
       ↓
BaseChart / GraphChart render
```

---

## 5. 演示集成

### 5.1 Dashboard 新增卡片

1. **`DashboardChartStatsCard.vue`**
   - 使用 `LineChart` / `BarChart`
   - 数据源：`/api/platform/dataviz/dataset`，source=`capability`，返回最近 7 天平台能力启用/禁用趋势。

2. **`DashboardFlowGraphCard.vue`**
   - 使用 `GraphChart`（D3 力导向）
   - 数据源：`/api/platform/dataviz/dataset`，source=`demo`，返回模块与能力之间的依赖关系图。

### 5.2 注册方式

通过现有 `DashboardLayout` 的 `cardCode` 机制注册，不破坏现有布局。

---

## 6. 错误处理

- **后端**：非法 `chartType`、不存在的数据源、查询超时均抛 `BizException`，消息为中文。
- **前端**：`useECharts` / `useD3Graph` 捕获初始化与渲染异常，组件进入错误状态，通过 `useFaToast` 提示用户，避免白屏。

---

## 7. 验证计划

- **后端编译**：`mvn -pl yudream-bootstrap -am -DskipTests compile`
- **前端类型检查**：`pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0`
- **手动验证**：启动应用后访问 Dashboard，确认两张新卡片渲染正常、主题切换生效、错误提示可用。

---

## 8. Git 提交计划

按模块独立提交，使用中文提交信息：

1. `feat: 新增 platform/dataviz 领域层`
2. `feat: 新增 platform/dataviz 应用层与接口层`
3. `feat: 新增 platform/dataviz 基础设施层`
4. `feat: 新增 dataviz 前端组件包`
5. `feat: Dashboard 新增图表与关系图演示卡片`

---

## 9. 待实现后确认项

- `echarts` 与 `d3` 是否统一使用 catalog 版本，还是单独指定版本。
- 是否需要为图表组件补充单元测试或 Storybook 示例。
- 是否需要把 `dataviz` 也暴露给前端插件使用（通过 `@yudream/plugin-sdk`）。
