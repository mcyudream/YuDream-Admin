# @Searchable 注解详解

## 概述

`@Searchable` 是用于后端查询类字段的注解,用于声明该字段为可搜索字段。前端将根据此注解自动生成筛选表单控件,实现注解驱动的动态搜索表单。

## 位置

```
yudream-application/src/main/java/online/yudream/base/application/common/search/Searchable.java
```

## 基本用法

```java
@EqualsAndHashCode(callSuper = true)
@Data
public class UserPageQuery extends PageQuery {
    @Searchable(label = "用户名", placeholder = "请输入用户名")
    private String username;

    @Searchable(label = "状态", controlType = ControlType.SELECT)
    private UserStatus status;
}
```

## 注解属性

### label

**类型**: `String`  
**默认值**: 字段名的驼峰转空格形式  
**说明**: 控件的显示标签

**示例**:
```java
@Searchable(label = "用户名")
private String username;

// 不设置label时,自动从字段名推断
@Searchable()
private String userName;  // 前端显示为 "user Name"
```

### placeholder

**类型**: `String`  
**默认值**: `""`  
**说明**: 输入框占位符文本

**示例**:
```java
@Searchable(
    label = "关键词",
    placeholder = "用户名/昵称/邮箱/手机号"
)
private String keyword;
```

### controlType

**类型**: `ControlType` 枚举  
**默认值**: `ControlType.AUTO`  
**说明**: 控件类型,AUTO表示根据字段类型自动推断

**可选值**:
| 值 | 说明 | 前端渲染组件 |
|----|------|-------------|
| `AUTO` | 自动推断 | 根据字段类型自动选择 |
| `TEXT` | 文本框 | `YdInput` |
| `SELECT` | 下拉选择 | `YdSelect` |
| `DATE_RANGE` | 日期范围 | `YdRangePicker` 范围选择器 |
| `NUMBER_RANGE` | 数值范围 | `YdNumberRange` 数值范围输入 |
| `CHECKBOX` | 复选框组 | `YdCheckboxGroup` |
| `RADIO` | 单选按钮组 | `YdRadioGroup` |

**AUTO 推断规则**:
```java
String -> TEXT
Enum -> SELECT
LocalDate/LocalDateTime/Date -> DATE_RANGE
Boolean -> SELECT (是/否)
Number类型 + 无optionsSource -> NUMBER_RANGE
Number类型 + 有optionsSource -> SELECT (外键ID)
```

**示例**:
```java
// 自动推断为 TEXT
@Searchable(label = "用户名")
private String username;

// 自动推断为 SELECT
@Searchable(label = "状态")
private UserStatus status;

// 显式指定为 SELECT
@Searchable(
    label = "部门",
    controlType = ControlType.SELECT,
    optionsSource = "deptOptions"
)
private Long deptId;

// 自动推断为 DATE_RANGE
@Searchable(label = "创建时间")
private LocalDate createTime;
```

### order

**类型**: `int`  
**默认值**: `0`  
**说明**: 字段在筛选表单中的显示顺序,数值越小越靠前

**示例**:
```java
@Searchable(label = "关键词", order = 1)  // 第一个显示
private String keyword;

@Searchable(label = "部门", order = 2)    // 第二个显示
private Long deptId;

@Searchable(label = "状态", order = 3)    // 第三个显示
private UserStatus status;
```

### showWhenFolded

**类型**: `boolean`  
**默认值**: `false`  
**说明**: 搜索区折叠时是否仍然显示该字段

**使用建议**:
- 只将最常用的1-2个字段(如关键词搜索)设为 `true`
- 其他字段保持默认 `false`,折叠时隐藏

**示例**:
```java
// 折叠时仍显示
@Searchable(
    label = "关键词",
    showWhenFolded = true,
    order = 1
)
private String keyword;

// 折叠时隐藏
@Searchable(
    label = "部门",
    order = 2
)
private Long deptId;
```

**效果**:
- 折叠状态: 只显示 `keyword` 搜索框
- 展开状态: 显示 `keyword` 和 `deptId` 两个字段

### width

**类型**: `String`  
**默认值**: `"200px"`  
**说明**: 控件的最小宽度(CSS值)

**示例**:
```java
@Searchable(
    label = "关键词",
    width = "240px"  // 更宽的搜索框
)
private String keyword;

@Searchable(
    label = "部门",
    width = "auto"   // 自适应宽度
)
private Long deptId;
```

### group

**类型**: `String`  
**默认值**: `""`  
**说明**: 字段分组名称,相同分组的字段会在UI上进行视觉分组

**示例**:
```java
// 基本信息组
@Searchable(label = "用户名", group = "基本信息")
private String username;

@Searchable(label = "昵称", group = "基本信息")
private String nickname;

// 状态组
@Searchable(label = "账号状态", group = "状态")
private UserStatus status;

@Searchable(label = "邮箱验证", group = "状态")
private Boolean emailVerified;
```

**前端渲染效果**:
```
基本信息
├── 用户名 [      ]
└── 昵称   [      ]

状态
├── 账号状态   [▼]
└── 邮箱验证   [▼]
```

### optionsSource

**类型**: `String`  
**默认值**: `""`  
**说明**: 选项数据源标识,用于 SELECT/CHECKBOX/RADIO 类型的字段

**使用流程**:
1. 后端在 `@Searchable` 中指定 `optionsSource`
2. 前端定义对应的 `SearchOptionsSource` 数据源
3. 组件自动加载并渲染选项

**后端示例**:
```java
@Searchable(
    label = "部门",
    controlType = ControlType.SELECT,
    optionsSource = "deptOptions"  // 标识
)
private Long deptId;

@Searchable(
    label = "角色",
    controlType = ControlType.SELECT,
    optionsSource = "roleOptions"  // 标识
)
private Long roleId;
```

**前端对应**:
```ts
const optionsSources: SearchOptionsSource[] = [
  {
    id: 'deptOptions',  // 与后端optionsSource对应
    loader: async () => {
      const res = await apiDept.list()
      return res.data.map(dept => ({
        label: dept.name,
        value: dept.id,
      }))
    },
  },
  {
    id: 'roleOptions',
    loader: async () => {
      const res = await apiRole.list()
      return res.data.map(role => ({
        label: role.name,
        value: role.id,
      }))
    },
  },
]
```

### hint

**类型**: `String`  
**默认值**: `""`  
**说明**: 控件旁边显示的提示信息

**示例**:
```java
@Searchable(
    label = "关键词",
    hint = "支持模糊匹配用户名、昵称、邮箱、手机号"
)
private String keyword;

@Searchable(
    label = "创建时间",
    controlType = ControlType.DATE_RANGE,
    hint = "选择起止日期范围"
)
private LocalDate createTime;
```

**前端渲染效果**:
```
关键词
[                    ]
支持模糊匹配用户名、昵称、邮箱、手机号
```

## 完整示例

### 用户查询类

```java
package online.yudream.base.application.system.user.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.application.common.PageQuery;
import online.yudream.base.application.common.search.ControlType;
import online.yudream.base.application.common.search.Searchable;
import online.yudream.base.domain.system.user.enumerate.UserStatus;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserPageQuery extends PageQuery {
    /**
     * 关键词搜索:折叠时仍显示,支持模糊匹配
     */
    @Searchable(
        label = "关键词",
        placeholder = "用户名/昵称/邮箱/手机号",
        showWhenFolded = true,
        order = 1,
        width = "240px",
        hint = "支持模糊匹配"
    )
    private String keyword;

    /**
     * 部门筛选:下拉选择,选项从deptOptions加载
     */
    @Searchable(
        label = "部门",
        controlType = ControlType.SELECT,
        optionsSource = "deptOptions",
        order = 2,
        group = "组织架构"
    )
    private Long deptId;

    /**
     * 角色筛选:下拉选择,选项从roleOptions加载
     */
    @Searchable(
        label = "角色",
        controlType = ControlType.SELECT,
        optionsSource = "roleOptions",
        order = 3,
        group = "组织架构"
    )
    private Long roleId;

    /**
     * 邮箱验证状态:自动推断为SELECT(是/否)
     */
    @Searchable(
        label = "邮箱验证",
        order = 4,
        group = "状态"
    )
    private Boolean emailVerified;

    /**
     * 账号状态:枚举自动推断为SELECT
     */
    @Searchable(
        label = "账号状态",
        order = 5,
        group = "状态"
    )
    private UserStatus status;

    /**
     * 创建时间范围:自动推断为DATE_RANGE
     */
    @Searchable(
        label = "创建时间",
        order = 6,
        hint = "选择起止日期"
    )
    private LocalDate createTime;
}
```

### 部门查询类

```java
@EqualsAndHashCode(callSuper = true)
@Data
public class DeptPageQuery extends PageQuery {
    @Searchable(
        label = "部门名称/描述",
        placeholder = "请输入部门名称或描述",
        showWhenFolded = true,
        order = 1,
        width = "280px"
    )
    private String keyword;

    @Searchable(
        label = "上级部门",
        controlType = ControlType.SELECT,
        optionsSource = "parentDeptOptions",
        order = 2
    )
    private Long parentId;

    @Searchable(
        label = "部门状态",
        controlType = ControlType.SELECT,
        order = 3
    )
    private DeptStatus status;
}
```

### 订单查询类

```java
@EqualsAndHashCode(callSuper = true)
@Data
public class OrderPageQuery extends PageQuery {
    @Searchable(
        label = "订单号",
        placeholder = "请输入订单号",
        showWhenFolded = true,
        order = 1
    )
    private String orderNo;

    @Searchable(
        label = "下单时间",
        controlType = ControlType.DATE_RANGE,
        order = 2
    )
    private LocalDate createTime;

    @Searchable(
        label = "订单金额",
        controlType = ControlType.NUMBER_RANGE,
        order = 3,
        hint = "输入最小值和最大值"
    )
    private BigDecimal amount;

    @Searchable(
        label = "订单状态",
        controlType = ControlType.CHECKBOX,  // 多选
        optionsSource = "orderStatusOptions",
        order = 4
    )
    private List<OrderStatus> statuses;
}
```

## 最佳实践

### 1. 合理设置 order

按字段的重要性和使用频率设置顺序:
- 关键词搜索: `order = 1`
- 常用筛选(部门/状态等): `order = 2-5`
- 不常用筛选(创建时间等): `order = 6+`

### 2. 谨慎使用 showWhenFolded

只将最常用的1-2个字段设为 `showWhenFolded = true`:
```java
// ✅ 好:只有关键词搜索折叠时显示
@Searchable(label = "关键词", showWhenFolded = true, order = 1)
private String keyword;

// ❌ 差:过多字段折叠时显示
@Searchable(label = "部门", showWhenFolded = true, order = 2)
private Long deptId;

@Searchable(label = "角色", showWhenFolded = true, order = 3)
private Long roleId;
```

### 3. 外键ID字段必须指定 optionsSource

Long类型的外键ID如果不指定 `optionsSource`,会被推断为 `NUMBER_RANGE`(数值范围输入):
```java
// ❌ 错误:会渲染成数值范围输入框
@Searchable(label = "部门")
private Long deptId;

// ✅ 正确:指定optionsSource后渲染为下拉选择
@Searchable(
    label = "部门",
    optionsSource = "deptOptions"
)
private Long deptId;
```

### 4. 枚举字段利用自动推断

枚举字段会自动推断为 `SELECT`,枚举常量会自动作为选项:
```java
// ✅ 简洁:利用AUTO自动推断
@Searchable(label = "状态")
private UserStatus status;

// 等价于
@Searchable(
    label = "状态",
    controlType = ControlType.SELECT
    // 枚举常量自动作为选项,无需optionsSource
)
private UserStatus status;
```

### 5. 使用分组组织复杂表单

字段较多(>5个)时,使用 `group` 进行分组:
```java
// 基本信息组
@Searchable(label = "用户名", group = "基本信息", order = 1)
private String username;

@Searchable(label = "邮箱", group = "基本信息", order = 2)
private String email;

// 组织架构组
@Searchable(label = "部门", group = "组织架构", order = 3)
private Long deptId;

@Searchable(label = "角色", group = "组织架构", order = 4)
private Long roleId;

// 状态组
@Searchable(label = "账号状态", group = "状态", order = 5)
private UserStatus status;

@Searchable(label = "邮箱验证", group = "状态", order = 6)
private Boolean emailVerified;
```

## 常见问题

### Q: 如何让 Boolean 字段显示为单选按钮?

A: 显式指定 `controlType = ControlType.RADIO`:
```java
@Searchable(
    label = "邮箱验证",
    controlType = ControlType.RADIO  // 而不是默认的SELECT
)
private Boolean emailVerified;
```

### Q: 如何实现多选下拉?

A: 使用 `CHECKBOX` 类型并用 `List` 接收:
```java
@Searchable(
    label = "标签",
    controlType = ControlType.CHECKBOX,
    optionsSource = "tagOptions"
)
private List<Long> tagIds;
```

### Q: DATE_RANGE 类型如何在后端接收?

A: 前端会将日期范围拆分为 `${fieldName}Start` 和 `${fieldName}End` 两个字段传递,后端Query类需要定义对应字段:
```java
@Searchable(
    label = "创建时间",
    controlType = ControlType.DATE_RANGE
)
private LocalDate createTime;  // 前端仅用于显示

// 实际接收的字段
private LocalDate createTimeStart;
private LocalDate createTimeEnd;
```

### Q: NUMBER_RANGE 类型如何在后端接收?

A: 类似日期范围,拆分为 `${fieldName}Min` 和 `${fieldName}Max`:
```java
@Searchable(
    label = "订单金额",
    controlType = ControlType.NUMBER_RANGE
)
private BigDecimal amount;  // 仅用于显示

// 实际接收
private BigDecimal amountMin;
private BigDecimal amountMax;
```

### Q: 如何处理可选的外键字段(可能为null)?

A: 使用包装类型 `Long` 而不是 `long`,并确保optionsSource提供了"全部"/"不限"选项:
```java
@Searchable(
    label = "部门",
    optionsSource = "deptOptions"
)
private Long deptId;  // ✅ 可以为null
```

前端optionsSource:
```ts
{
  id: 'deptOptions',
  loader: async () => {
    const res = await apiDept.list()
    return [
      { label: '全部部门', value: null },  // null选项
      ...res.data.map(dept => ({ label: dept.name, value: dept.id }))
    ]
  }
}
```

## 相关文档

- [YdTablePageLayout使用指南](./yd-table-page-layout.md)
- [前端开发规范](./dev/frontend.md)
- [后端架构规范](./dev/backend-architecture.md)
