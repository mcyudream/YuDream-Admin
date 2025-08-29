import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:square-dashed-bottom-code',
      order: 999,
      title: $t('page.codegen.title'),
      affixTab: true,
    },
    name: 'Codegen',
    path: '/codegen',
    component: ()=> import("#/views/codegen/Index.vue")
  },
];

export default routes;
