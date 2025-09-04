import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:monitor-cog',
      order: 999,
      title: $t('page.system.title'),
    },
    name: 'System',
    path: '/system',
    component: ()=> import("#/views/system/Index.vue")
  },
];

export default routes;
