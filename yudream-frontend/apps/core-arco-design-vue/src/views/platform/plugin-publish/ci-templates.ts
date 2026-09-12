import gitlabCiYml from './ci-templates/gitlab-ci.yml?raw'
import pluginJarSelectionSh from './ci-templates/plugin-jar-selection.sh?raw'
import pluginsTxt from './ci-templates/plugins.txt?raw'
import publishToMarketSh from './ci-templates/publish-to-market.sh?raw'

export interface CiTemplateFile {
  id: string
  label: string
  filename: string
  description: string
  content: string
}

const MARKET_JOB_SNIPPET = `# 把本次选择的最终 JAR 发布到自托管 YuDream 插件市场。
# CI 变量（受保护并掩码）：
#   YUDREAM_MARKET_URL      宿主根地址，例如 __MARKET_ORIGIN__
#   YUDREAM_MARKET_API_KEY  勾选 platform:plugin-market-source:upload 的 API Key
# 可选：YUDREAM_MARKET_RELEASE_NOTES（分类/标签写在各插件 store.json，不要配仓库级 CI 变量）
# 需同时放入：
#   ci/publish-to-market.sh
#   ci/lib/plugin-jar-selection.sh
#   release/plugins.txt
publish:market:
  image: swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library/python:3.12-alpine
  stage: publish-plugin
  timeout: 20 minutes
  resource_group: yudream-plugin-market
  needs:
    - job: package:plugins
      artifacts: true
  before_script:
    - sed -i 's#https\\?://dl-cdn.alpinelinux.org/alpine#https://mirrors.aliyun.com/alpine#g' /etc/apk/repositories
    - apk add --no-cache curl unzip
  script:
    - export PLUGIN_RELEASE_ONLY=1
    - sh ci/publish-to-market.sh
  rules:
    - if: '$CI_COMMIT_TAG =~ /^v/ && $CI_COMMIT_REF_PROTECTED == "true" && $YUDREAM_MARKET_URL'
`

export function buildCiTemplateFiles(marketOrigin: string): CiTemplateFile[] {
  const origin = marketOrigin.replace(/\/+$/, '') || 'https://yudream.example.com'
  return [
    {
      id: 'market-job',
      label: 'publish:market 片段',
      filename: 'publish-market.gitlab-ci.yml',
      description: '可贴进现有插件仓 .gitlab-ci.yml。YUDREAM_MARKET_URL 填宿主根地址，不要带 /api/public/plugin-market。',
      content: MARKET_JOB_SNIPPET.replaceAll('__MARKET_ORIGIN__', origin),
    },
    {
      id: 'gitlab-ci',
      label: '.gitlab-ci.yml',
      filename: '.gitlab-ci.yml',
      description: '插件仓完整流水线模板（含校验、打包、Nexus 发布与市场发布）。复制为仓库根 .gitlab-ci.yml。',
      content: gitlabCiYml,
    },
    {
      id: 'publish-sh',
      label: 'publish-to-market.sh',
      filename: 'ci/publish-to-market.sh',
      description: '市场上传脚本。放到 ci/publish-to-market.sh，由 publish:market 调用。',
      content: publishToMarketSh,
    },
    {
      id: 'selection-sh',
      label: 'plugin-jar-selection.sh',
      filename: 'ci/lib/plugin-jar-selection.sh',
      description: '按 release/plugins.txt 选择最终 JAR。放到 ci/lib/plugin-jar-selection.sh。',
      content: pluginJarSelectionSh,
    },
    {
      id: 'plugins-txt',
      label: 'plugins.txt',
      filename: 'release/plugins.txt',
      description: '本次要发布的插件模块清单。放到 release/plugins.txt，每行一个模块 ID。',
      content: pluginsTxt,
    },
  ]
}
