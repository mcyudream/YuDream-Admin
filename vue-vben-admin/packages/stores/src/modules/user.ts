import { acceptHMRUpdate, defineStore } from 'pinia';

 export interface BasicUserInfo {
  [key: string]: any;

  /**
   * 用户名
   */
  id: string;
  /**
   * 头像
   */
  avatar: string;
  /**
   * 用户昵称
   */
  nickname: string;

  /**
   * 用户角色
   */
  roles?: Role[];
   /**
    *
    */
  email?: string;
   status: UserStatus;
   createTime: Date;
   updateTime: Date;

 }

 export interface UserStatus{
   code: number;
   description: string;
 }

export interface Role {
  id: string;
  name: string;
  level: number;
  description: string;
  enabled: boolean;
}

interface AccessState {
  /**
   * 用户信息
   */
  userInfo: BasicUserInfo | null;
  /**
   * 用户角色
   */
  userRoles: Role[];
}

/**
 * @zh_CN 用户信息相关
 */
export const useUserStore = defineStore('core-user', {
  actions: {
    setUserInfo(userInfo: BasicUserInfo | null) {
      // 设置用户信息
      this.userInfo = userInfo;
      // 设置角色信息
      const roles = userInfo?.roles ?? [];
      this.setUserRoles(roles);
    },
    setUserRoles(roles: Role[]) {
      this.userRoles = roles;
    },
  },
  state: (): AccessState => ({
    userInfo: null,
    userRoles: [],
  }),
});

// 解决热更新问题
const hot = import.meta.hot;
if (hot) {
  hot.accept(acceptHMRUpdate(useUserStore, hot));
}


