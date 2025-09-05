<script lang="ts" setup>
import type { VbenFormProps } from '#/adapter/form';
import type { VxeGridProps } from '#/adapter/vxe-table';
import {Button} from 'ant-design-vue';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getAllRolesPageApi} from "#/api/core/role";
import type {SearchPageParams} from "#/types/common";
import {$t} from "@vben/locales";
import type {Role} from "#/types/organizationalStructure/role";



const formOptions: VbenFormProps = {
  // 默认展开
  collapsed: false,
  schema: [
    {
      component: 'Input',
      componentProps: {
        placeholder: $t("form.role.table.search.id.placeholder"),
      },
      fieldName: 'id',
      label: $t("form.role.table.search.id.title")
    },{
      component: 'Input',
      componentProps: {
        placeholder: $t("form.role.table.search.name.placeholder"),
      },
      fieldName: 'name',
      label: $t("form.role.table.search.name.title")
    },{
      component: 'Input',
      componentProps: {
        placeholder: $t("form.role.table.search.description.placeholder"),
      },
      fieldName: 'description',
      label: $t("form.role.table.search.description.title")
    },
  ],
  // 控制表单是否显示折叠按钮
  showCollapseButton: true,
  submitButtonOptions: {
    content: $t('form.common.search'),
  },
  // 是否在字段值改变时提交表单
  submitOnChange: false,
  // 按下回车时是否提交表单
  submitOnEnter: false,
};

const gridOptions: VxeGridProps<Role> = {
  checkboxConfig: {
    highlight: true,
    labelField: 'name',
  },
  columns: [
    { field: 'id', title: $t("form.role.table.field.id") },
    { field: 'name', title: $t("form.role.table.field.name") },
    { field: 'description', title: $t("form.role.table.field.description") },
    { field: 'enable', title: $t("form.role.table.field.enable") },
    { field: 'level', title: $t("form.role.table.field.level") },
    {
      field: 'action',
      fixed: 'right',
      slots: { default: 'action' },
      title: $t('form.common.action.title'),
      width: 200,
    },

  ],
  keepSource: true,
  pagerConfig: {},
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        const pageSearch:SearchPageParams = {
          page: page.currentPage,
          size: page.pageSize,
          keywords: {
            ...formValues
          }
        }
        const data = await getAllRolesPageApi(pageSearch)
        selectedRow.value = data.content[0]??{}
        return data;
      },
    },
  },
  toolbarConfig: {
    // @ts-ignore
    search: true,
  },
};
const [Grid] = useVbenVxeGrid({ formOptions, gridOptions });
const selectedRow = defineModel('role', {
  default: {}
} )
const selectRow = (row: Role)=>{
  selectedRow.value = row;
}

</script>

<template>
  <div class="w-full">
    <Grid >
      <template #toolbar-tools>

        当前选择: {{selectedRow.name}}
      </template>
      <template #action="{ row }">
        <Button @click="selectRow(row)" type="link" class="mr-2">{{$t('form.common.select')}}</Button>
      </template>
    </Grid>

  </div>
</template>
