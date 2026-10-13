# YD 表单与基础装配契约

`YdForm`、`YdFormItem`、`YdInputTag`、`YdRate`、`YdAction` 与 `YdUiProvider` 是共享组件的公开入口；根导出、resolver 与插件 SDK 宿主桥同步。业务不用引入底层 Arco / Reka 类型。

## 表单

`YdForm` 使用 `model` 对象，并接受 `rules`、`layout`（默认 vertical）、`disabled`、`size`、标签/内容列宽与样式、`autoLabelWidth`、`scrollToFirstError`。字段通过 `YdFormItem field="path"` 注册，支持嵌套路径。提交事件为 `submit({ values, errors }, event)`；成功 `submitSuccess(values,event)`、失败 `submitFailed({values,errors},event)`。成功时 errors 是 undefined；校验不会通过抛异常表示普通字段错误。

实例类型 `YdFormInstance` 提供 `validate(callback?)`、`validateField(field,callback?)`，返回 `Promise<YdFormErrors | undefined>`；并提供 `resetFields(field?)`、`clearValidate(field?)`、`setFields(data)`、`scrollToField(field)`。重置恢复字段挂载时初始值；setFields 用于服务端错误映射。表单尚未挂载时 validate/validateField 拒绝 Promise，不能当成校验成功。

`YdFormRule` 暴露 required/type/长度/数值/正则等规则及 `validator(value, callback)`；异步校验完成后调用 callback，失败传入中文错误信息。自定义 validator 不返回布尔值替代回调。

`YdFormItem` 提供 label/help/extra/default 插槽，支持 required、rules、validateTrigger（change/input/focus/blur）、validateStatus、disabled、noStyle、标签布局。直接放置 YD 控件时自动关联标签、disabled、aria-invalid 与模型修改校验。复杂嵌套布局通过默认作用域槽将 `fieldProps` 绑定到实际控件：

```vue
<YdForm :model="form">
  <YdFormItem field="name" label="名称" :rules="[{ required: true, message: '请输入名称' }]">
    <YdInput v-model="form.name" />
  </YdFormItem>
  <YdFormItem field="description" label="描述" #default="{ fieldProps }">
    <div class="grid gap-2">
      <YdTextarea v-bind="fieldProps" v-model="form.description" />
    </div>
  </YdFormItem>
</YdForm>
```

一个字段承载一个主要控件；多值模型作为数组字段校验。复合控件需维护自身模型，不把数个不关联的字段塞进同一 FormItem。需要显式控件 ID 时设置 `controlId`，并绑定 fieldProps；页面不自行引用内部引擎 context。

## 标签与评分

`YdInputTag` 的默认模型为 `YdInputTagValue[]`，元素为字符串、数字或 `{ value, label?, closable? }`；需要长 ID 时只能用字符串。`v-model:input-value` 控制待输入文本。默认 uniqueValue=true，allowClear=true。支持 placeholder/disabled/readonly/error/size/maxTagCount/retainInputValue/tagNowrap/formatTag；模型、输入、Enter、remove、clear、focus、blur 事件与 focus/blur 实例方法均保留。prefix/suffix/tag 插槽用于内容。它是创建标签的输入框，没有远程候选查询或多选下拉能力；后者需独立完整契约。

`YdRate` 使用 number 模型（默认 0），默认 count=5，支持 allowHalf、allowClear、readonly、disabled、ariaLabel。鼠标可选半星；键盘通过 native range 语义使用方向键/Home/End，allowClear 时 Delete/Backspace 清空。只读保持可读且阻止键盘修改；disabled 继承表单。提供 change/hoverChange/focus/blur 与 focus/blur 方法。颜色由 YD 主题控制，没有业务自定固定 color 属性。

## 自定动作与根 Provider

`YdAction` 是低视觉干预的语义按钮：默认 type=button，支持 disabled、属性和原生事件透传、focus/blur/element 实例能力。只用来承载导航项或自定卡片布局；普通保存、删除、确认继续用 `YdButton`。使用 class 编排布局、中性色与内容；页面不绘制私有品牌选中态，图标动作仍需 aria-label。

`YdUiProvider` 统一内部 Arco / Reka provider，接受 locale（zh-CN/en-US）、size、dir、updateAtScroll、scrollToClose、exchangeTime。它不切换主色/明暗，也不重建 FA 主题引擎；宿主与预览根节点仍加载共享 appearance.css 并由主题运行时写入变量。插件继承宿主配置，不重复包 provider 重置系统选项。

可交互预览见组件预览「迁移基础能力」小节，覆盖提交失败、自动校验、重置、禁用、只读、数组标签与半星键盘评分。
