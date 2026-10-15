name: {{CODE}}
displayName: "{{DISPLAY_NAME}}"
description: "{{DESCRIPTION}}"
icon: "i-ri:puzzle-2-line"
main: {{MAIN_CLASS}}
version: {{VERSION}}
menu:
  # 必须声明语义分类，宿主据此把插件挂到共享分类下（不提供「其他」兜底）。
  # 内置分类：mc（MC）、social（社交）、play（娱乐）、todo（待办）、content（内容）、
  # campus（校园）、client（客户端）、trade（交易）、tools（工具）；也可用 {code,name,icon,sort} 自定义。
  category: tools
{{DEPEND_YAML}}{{SOFTDEPEND_YAML}}
