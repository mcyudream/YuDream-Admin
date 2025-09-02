import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:building-2',
      order: 1,
      title: $t('page.organizationalStructure.title'),
    },
    name: 'OrganizationalStructure',
    path: '/OrganizationalStructure',
    children: [
      {
        name: 'Department',
        path: '/department',
        component: () => import('#/views/organizationalStructure/department/index.vue'),
        meta: {
          icon: 'lucide:building',
          title: $t('page.organizationalStructure.department.title'),
        },
      },
    ],
  },
];

export default routes;
