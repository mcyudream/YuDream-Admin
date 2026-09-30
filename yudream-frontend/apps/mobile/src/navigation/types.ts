export type RootStackParamList = {
  Splash: undefined;
  Welcome: undefined;
  /** 接入站点；scanned 为扫码回传的站点地址（merge 回传） */
  DomainAdd: { scanned?: string } | undefined;
  /** 相机扫一扫：解析二维码后 merge 回传给 DomainAdd */
  Scan: undefined;
  Login: undefined;
  Main: undefined;
  /** 应用容器：route 为应用内路由（home 卡片声明），透传给插件模块 */
  PluginHost: { code: string; title: string; route?: string };
  /** 域管理（管理员）：域列表 + 应用注册内容/可见性统一管理 */
  DomainManage: undefined;
  /** 关于软件：logo/版本/手动检查更新/完整更新日志 */
  About: undefined;
};

export type MainTabParamList = {
  首页: undefined;
  应用: undefined;
  我的: undefined;
};
