# YdMockupBrowser 浏览器外框

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `url` (`v-model:url`) | `string` | `https://example.com` | 当前演示地址；不会发起请求 |

地址栏编辑后提交会更新 `v-model:url` 并发出 `navigate(url)`；后退、前进与刷新分别发出 `back`、`forward`、`refresh`。组件仅维护演示用本地历史，不加载或请求该 URL。默认插槽可接收当前 `url` 和 `navigate`。
