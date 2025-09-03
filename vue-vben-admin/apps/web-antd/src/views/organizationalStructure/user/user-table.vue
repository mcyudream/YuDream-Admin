<script lang="ts" setup>
import type { VbenFormProps } from '#/adapter/form';
import type { VxeGridProps } from '#/adapter/vxe-table';
import {Button, message} from 'ant-design-vue';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import type {SearchPageParams} from "#/types/common";
import {$t} from "@vben/locales";
import { useVbenDrawer } from '@vben/common-ui';
import {ref} from "vue";
import { confirm } from '@vben/common-ui';
import {deleteUserApi, getUsersPageApi} from "#/api";
import type {User} from "#/types/user";
import EditUserDrawer from "#/views/organizationalStructure/user/edit-user-drawer.vue";


const formOptions: VbenFormProps = {
  // 默认展开
  collapsed: false,
  schema: [
    {
      component: 'Input',
      componentProps: {
        placeholder: $t("form.user.table.search.id.placeholder"),
      },
      fieldName: 'id',
      label: $t("form.user.table.search.id.title")
    },{
      component: 'Input',
      componentProps: {
        placeholder: $t("form.user.table.search.nickname.placeholder"),
      },
      fieldName: 'nickname',
      label: $t("form.user.table.search.nickname.title")
    },{
      component: 'Input',
      componentProps: {
        placeholder: $t("form.user.table.search.email.placeholder"),
      },
      fieldName: 'email',
      label: $t("form.user.table.search.email.title")
    },{
      component: 'Input',
      componentProps: {
        placeholder: $t("form.user.table.search.phone.placeholder"),
      },
      fieldName: 'phone',
      label: $t("form.user.table.search.phone.title")
    },{
      component: 'Select',
      componentProps: {
        placeholder: $t("form.user.table.search.status.placeholder"),
        options: [
          {
            label: "正常",
            value: "NORMAL",
          },{
            label: "封禁",
            value: "BANNED",
          }
        ]
      },

      fieldName: 'status',
      label: $t("form.user.table.search.status.title")
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

const gridOptions: VxeGridProps<User> = {
  checkboxConfig: {
    highlight: true,
    labelField: 'name',
  },
  columns: [
    { field: 'id', minWidth:120, title: $t("form.user.table.field.id") },
    { field: 'nickname', minWidth: 100, title: $t("form.user.table.field.nickname") },
    { field: 'email', minWidth: 200, title: $t("form.user.table.field.email") },
    { field: 'phone', minWidth:160, title: $t("form.user.table.field.phone") },
    { field: 'status', minWidth:80, title: $t("form.user.table.field.status") },
    { field: 'createTime', minWidth:140,formatter:"formatDateTime" , title: $t("form.user.table.field.createTime") },
    { field: 'updateTime', minWidth:140, formatter:"formatDateTime" , title: $t("form.user.table.field.updateTime") },
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
        return await getUsersPageApi(pageSearch);
      },
    },
  },
  toolbarConfig: {
    // @ts-ignore
    search: true,
  },
};

const [EditModal, editModalApi] = useVbenDrawer({
  connectedComponent: EditUserDrawer
})
const [Grid, gridApi] = useVbenVxeGrid({ formOptions, gridOptions });

const currentRole = ref<User>({});

const editRowEvent = (row:User) => {
  currentRole.value = row;
  editModalApi.open()
}

const onDelete = (row:User) => {
  confirm(`删除不可回溯，确认删除用户${row.nickname}(${row.id})?`).then(async () => {
    const res = await deleteUserApi(row)
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
      </template>
      <template #action="{ row }">
        <Button type="link" class="mr-2"  @click="editRowEvent(row)">{{$t('form.common.edit')}}</Button>
        <Button danger type="link" class="mr-2" @click="onDelete(row)">{{$t('form.common.delete')}}</Button>
      </template>
    </Grid>
    <edit-modal :initial-value="currentRole" @on-submit="gridApi.query()"/>
  </div>
</template>
