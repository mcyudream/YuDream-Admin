import type { RouteRecordMainRaw } from '@fantastic-admin/types'
import type { RouteRecordRaw } from 'vue-router'
import settingsDefault from '@/settings'
import ArcoDesignVueExample from './modules/arco.design.vue.example'
import MultilevelMenuExample from './modules/multilevel.menu.example'

// 固定路由（默认路由）
const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login.vue'),
    meta: {
      title: '登录',
    },
  },
  {
    path: '/setup',
    name: 'setup',
    component: () => import('@/views/setup.vue'),
    meta: {
      title: '系统初始化',
      public: true,
    },
  },
  {
    path: '/verify-email',
    name: 'verifyEmail',
    component: () => import('@/views/verify-email.vue'),
    meta: {
      title: '邮箱验证',
      public: true,
    },
  },
  {
    path: '/reset-password',
    name: 'resetPassword',
    component: () => import('@/views/reset-password.vue'),
    meta: {
      title: '重置密码',
      public: true,
    },
  },
  {
    path: '/external-login/callback',
    name: 'external-login-callback',
    component: () => import('@/views/external-login-callback.vue'),
    meta: { title: '第三方登录', public: true, constant: true },
  },
  {
    path: '/pay/result',
    name: 'payResult',
    component: () => import('@/views/pay-result.vue'),
    meta: {
      title: '支付结果',
      public: true,
    },
  },
  {
    path: '/site',
    name: 'publicSiteHome',
    component: () => import('@/views/site/index.vue'),
    meta: {
      title: '站点首页',
      public: true,
    },
  },
  {
    path: '/site/:slug(.*)*',
    name: 'publicSitePage',
    component: () => import('@/views/site/index.vue'),
    meta: {
      title: '内容页面',
      public: true,
    },
  },
  {
    path: '/forms/:code',
    name: 'publicDynamicForm',
    component: () => import('@/views/forms/public.vue'),
    meta: {
      title: '表单填写',
      public: true,
    },
  },
  {
    path: '/wiki',
    name: 'publicWikiHome',
    component: () => import('@/views/wiki/home.vue'),
    meta: {
      title: '知识库',
      public: true,
    },
  },
  {
    path: '/wiki/:spaceSlug/:nodePath(.*)*',
    name: 'publicWiki',
    component: () => import('@/views/wiki/index.vue'),
    meta: {
      title: '知识库',
      public: true,
    },
  },
  {
    path: '/:all(.*)*',
    name: 'notFound',
    component: () => import('@/views/[...all].vue'),
    meta: {
      title: '找不到页面',
    },
  },
]

// 系统路由
const systemRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    component: () => import('@/layouts/index.vue'),
    meta: {
      breadcrumb: false,
    },
    children: [
      {
        path: '',
        component: () => import('@/views/index.vue'),
        meta: {
          title: settingsDefault.app.home.title,
          icon: 'i-ant-design:home-twotone',
          breadcrumb: false,
        },
      },
      {
        path: 'reload',
        name: 'reload',
        component: () => import('@/views/reload.vue'),
        meta: {
          title: '重新加载中...',
          breadcrumb: false,
        },
      },
    ],
  },
]

// 动态路由（异步路由、导航菜单路由）
const asyncRoutes: RouteRecordMainRaw[] = [
  {
    meta: {
      title: '演示',
      icon: 'i-ri:function-ai-line',
    },
    children: [
      MultilevelMenuExample,
    ],
  },
  {
    meta: {
      title: 'UI',
      icon: 'i-whh:jqueryui',
    },
    children: [
      ArcoDesignVueExample,
    ],
  },
]

export {
  asyncRoutes,
  constantRoutes,
  systemRoutes,
}
