import { setSettings } from '@fantastic-admin/settings'

export default setSettings({
  // 请在此处编写或粘贴应用配置
  app: {
    routeMode: 'html5',
    routeBaseOn: 'backend',
    account: {
      auth: true,
    },
  },
  menu: {
    mainMenuClickMode: 'smart',
  },
})
