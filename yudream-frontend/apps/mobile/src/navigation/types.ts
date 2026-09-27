export type RootStackParamList = {
  Splash: undefined;
  Welcome: undefined;
  DomainAdd: undefined;
  Login: undefined;
  Main: undefined;
  /** 应用容器：route 为应用内路由（home 卡片声明），透传给插件模块 */
  PluginHost: { code: string; title: string; route?: string };
  /** 域管理（管理员）：域列表 + 应用注册内容/可见性统一管理 */
  DomainManage: undefined;
};

export type MainTabParamList = {
  首页: undefined;
  应用: undefined;
  我的: undefined;
};
