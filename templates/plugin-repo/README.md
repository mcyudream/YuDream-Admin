# 插件仓模板

这个目录用于初始化未来的独立插件仓，例如：

- `yudream-admin-plugins`

模板包含：

- `.gitlab-ci.yml.example`
- `.npmrc.example`
- `settings.xml.example`

使用时建议：

1. 复制模板到新仓根目录
2. 将 `CORE_PROJECT_ID` 和 `gitlab.example.com` 替换成实际值
3. 在新仓 GitLab CI variables 中配置读取私有包仓库所需的令牌
