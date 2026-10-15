# YdCode 代码展示

展示语法高亮代码，支持复制、行号和每行提示符前缀。

- `code`: 源码字符串
- `language`: highlight.js 语言名，默认 `plaintext`
- `title`: 标题；未提供时显示语言名
- `copyable`: 是否显示复制按钮，默认 `true`
- `lineNumbers`: 是否显示行号，默认 `false`
- `prefixes`: 可选的逐行前缀数组，例如 shell 提示符 `$ `、`> `

高亮与复制使用原始完整代码；前缀只用于展示。
