<script lang="ts" setup>
import type { VbenFormProps } from '#/adapter/form';
import type { VxeGridProps } from '#/adapter/vxe-table';
import {Button, message} from 'ant-design-vue';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {deleteRoleApi, getAllRolesPageApi} from "#/api/core/role";
import type {SearchPageParams} from "#/types/common";
import {$t} from "@vben/locales";
import { useVbenModal } from '@vben/common-ui';
import CreateRoleModal from "#/views/organizationalStructure/role/create-role-modal.vue";
import type {Role} from "#/types/organizationalStructure/role";
import EditRoleModal from "#/views/organizationalStructure/role/edit-role-modal.vue";
import {ref} from "vue";
import { confirm } from '@vben/common-ui';


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
        return await getAllRolesPageApi(pageSearch);
      },
    },
  },
  toolbarConfig: {
    // @ts-ignore
    search: true,
  },
};
const [CreateModal, createModalApi] = useVbenModal({
  connectedComponent: CreateRoleModal
})
const [EditModal, editModalApi] = useVbenModal({
  connectedComponent: EditRoleModal
})
const [Grid, gridApi] = useVbenVxeGrid({ formOptions, gridOptions });

const currentRole = ref<Role>({});

const editRowEvent = (row:Role) => {
  currentRole.value = row;
  editModalApi.open()
}

const onDelete = (row:Role) => {
  confirm(`删除不可回溯，确认删除角色${row.name}(${row.id})?`).then(async () => {
    const res = await deleteRoleApi(row)
    // @ts-ignore
    message.success(`删除成功! ${res.message}`)
    await gridApi.query()
  })
}
</script>

<template>
  <div class="w-full">
    <Grid >
      <template #toolbar-tools>
        <Button type="primary" class="flex items-center space-x-1" @click="createModalApi.open()">
          <span class="icon-[mdi--add-box] size-4" ></span>创建角色</Button>
      </template>
      <template #action="{ row }">
        <Button type="link" class="mr-2"  @click="editRowEvent(row)">{{$t('form.common.edit')}}</Button>
        <Button danger type="link" class="mr-2" @click="onDelete(row)">{{$t('form.common.delete')}}</Button>
      </template>
    </Grid>
    <create-modal @on-submit="gridApi.query()"/>
    <edit-modal :initial-value="currentRole" @on-submit="gridApi.query()"/>
  </div>
</template>
