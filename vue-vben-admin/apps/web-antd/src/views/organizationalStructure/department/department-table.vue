<script lang="ts" setup>
import type {VxeGridProps} from '#/adapter/vxe-table';
import {useVbenVxeGrid} from '#/adapter/vxe-table';

import {Button, message} from 'ant-design-vue';
import { confirm } from '@vben/common-ui';
import {type VbenFormProps} from "#/adapter/form";
import {deleteDepartmentApi, getAllDepartments} from "#/api/core/department";
import type {SearchPageParams} from "#/types/common";
import type {Department} from "#/types/organizationalStructure/department";
import {$t} from "@vben/locales";
import CreateDepartmentModal
  from "#/views/organizationalStructure/department/create-department-modal.vue";
import { useVbenModal } from '@vben/common-ui';
import { ref} from "vue";
import EditDepartmentModal
  from "#/views/organizationalStructure/department/edit-department-modal.vue";


const departmentData = ref<Department[]>([]);




const formOptions: VbenFormProps = {
  // 默认展开
  collapsed: false,
  schema: [
    {
      component: 'Input',
      componentProps: {
        placeholder: '请输入ID',
      },
      fieldName: 'id',
      label: $t('form.department.table.search.id.title'),
    },
    {
      component:'Input',
      componentProps: {
        placeholder: $t('form.department.table.search.name.placeholder'),
      },
      fieldName: 'name',
      label: $t('form.department.table.search.name.title'),
    },
    {
      component:'Input',
      componentProps: {
        placeholder: $t('form.department.table.search.description.placeholder'),
      },
      fieldName: 'description',
      label: $t('form.department.table.search.description.title'),
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

const gridOptions: VxeGridProps<Department> = {
  columns: [
    {field: 'id',minWidth:250 , title: $t('form.department.table.field.id')},
    {field: 'parentId',minWidth:250 , title: $t('form.department.table.field.parentId'), treeNode: true},
    {field: 'name',minWidth:120 ,title: $t('form.department.table.field.name')},
    { field: 'description', minWidth:200, title: $t('form.department.table.field.description')},
    { field: 'createdTime', minWidth: 300 , formatter: 'formatDateTime', title: $t('form.department.table.field.createdTime')},
    { field: 'updatedTime', minWidth: 300 ,formatter: 'formatDateTime', title: $t('form.department.table.field.updatedTime') },
    {
      field: 'action',
      fixed: 'right',
      slots: { default: 'action' },
      title: $t('form.common.action.title'),
      width: 200,
    },

  ],
  proxyConfig:{
    ajax: {
      query: async ({},formValues) => {
        const searchParams: SearchPageParams = {
          keywords: formValues
        }
        let res  = await getAllDepartments(searchParams)
        departmentData.value = res
        return {
          content: res
        }
      }
    }
  },

  pagerConfig: {
    enabled: false
  },
  editConfig: {
    mode: 'cell',
    trigger: 'click',
  },
  treeConfig: {
    parentField: 'parentId',
    rowField: 'id',

    transform: true,
  },
  toolbarConfig: {
    // 是否显示搜索表单控制按钮
    // @ts-ignore 正式环境时有完整的类型声明
    search: true,
  },
};

const [Grid, gridApi] = useVbenVxeGrid({ formOptions,gridOptions });

const emit = defineEmits(['onCreate'])
const [CreateModal, modalApi] = useVbenModal({
  connectedComponent: CreateDepartmentModal
})

const [EditModal, editModalApi] = useVbenModal({
  connectedComponent: EditDepartmentModal
})



const onCreate = (parentId=null) => {
  emit('onCreate',parentId);
  modalApi.open()
}

const onDelete = (row: Department) => {
  confirm(`删除不可回溯，确认删除部门${row.name}(${row.id})?`).then(async () => {
    const res = await deleteDepartmentApi(row)
    message.success(`删除成功! ${res.message}`)
    await gridApi.query()
  })
}

const currentRow = ref<Department>({})

function editRowEvent(row: Department) {
  currentRow.value = row
  editModalApi.open()
}
</script>

<template>
  <div class="w-full">
    <Grid>
      <template #toolbar-tools>
        <Button class="mr-2"  type="primary" @click="onCreate()">{{$t('page.organizationalStructure.department.create')}}</Button>
      </template>
      <template #action="{ row }">
        <Button type="link" class="mr-2"  @click="editRowEvent(row)">{{$t('form.common.edit')}}</Button>
        <Button danger type="link" class="mr-2" @click="onDelete(row)">{{$t('form.common.delete')}}</Button>
      </template>
    </Grid>
    <CreateModal @on-submit="gridApi.query()" v-model:department="departmentData"/>
    <EditModal :initial-value="currentRow" @on-submit="gridApi.query()" v-model:department="departmentData"/>
  </div>
</template>
