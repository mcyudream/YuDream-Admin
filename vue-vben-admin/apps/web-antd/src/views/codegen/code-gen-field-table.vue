<script setup lang="ts">
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {Button, message} from "ant-design-vue";
import {defineModel} from "vue";
import type { VxeGridProps } from '#/adapter/vxe-table';
import type {FieldDefinition} from "#/types/codegen";
import {$t} from "@vben/locales";
let baseData = defineModel<FieldDefinition[]>({default: []})
const gridOptions: VxeGridProps<FieldDefinition> = {
  columns: [
    { editRender: {name: "input"}, field: "name", title: $t("form.codegen.field.title")},
    {
      editRender:
        {
          name: "select" ,
          options: [
            {
              label: "字符串(String)",
              value: "String"
            }, {
              label: "整数(Integer)",
              value: "Integer"
            }, {
              label: "布尔(Boolean)",
              value: "Boolean"
            }, {
              label: "字符串列表(List<String>)",
              value: "List<String>"
            }
          ],
          defaultValue: "String"
        },
      field: "type",
      title: $t("form.codegen.field.type")
    },
    {
      editRender:
        {
          name: "select" ,
          options: [
            {
              label: "否",
              value: "false"
            },
            {
              label: "是",
              value: "true"
            },
          ],
          defaultValue: "false"
        },
      field: "indexed",
      title: $t("form.codegen.field.indexed")
    },
    {
      slots: { default: 'action' },
      field: 'action',
      fixed: 'right',
      title: '操作',
    },
  ],
  editConfig: {
    mode: 'cell',
    trigger: 'click',
  },
  pagerConfig: {
    enabled: false,
  },
  sortConfig: {
    multiple: true,
  },
  data: baseData.value,
  showOverflow: true,
};

const [BaseTable, tableApi] = useVbenVxeGrid({gridOptions})

const addField = async () => {
  baseData.value.length = 0
  for (const field of tableApi.grid.data??[]) {
    baseData.value.push(field)
  }
  baseData.value.push({
    name: "fieldName",
    type: "String",
    indexed: "false"
  })
  await tableApi.grid.loadData(baseData.value)
}

function hasEditStatus(row: FieldDefinition) {
  return tableApi.grid?.isEditByRow(row);
}

function editRowEvent(row: FieldDefinition) {
  tableApi.grid?.setEditRow(row);
}

async function saveRowEvent(row: FieldDefinition) {
  await tableApi.grid?.clearEdit();

  tableApi.setLoading(true);
  setTimeout(() => {
    tableApi.setLoading(false);
    message.success({
      content: `保存成功！`,
    });
  }, 600);
}

const cancelRowEvent = (_row: FieldDefinition) => {
  tableApi.grid?.clearEdit();
};

const delField = async (row: FieldDefinition) => {
  baseData.value = tableApi.grid.data??[]
  baseData.value.splice(baseData.value.indexOf(row), 1)
  await tableApi.grid.loadData(baseData.value)
}
</script>

<template>
  <base-table >
    <template #toolbar-tools>
      <span class="mr-2">默认包含id, createTime, updateTime</span>
      <Button class="mr-2" @click="addField" type="primary">添加字段</Button>
    </template>
    <template #action="{ row }">
      <template v-if="hasEditStatus(row)">
        <Button type="link" @click="saveRowEvent(row)">保存</Button>
        <Button type="link" @click="cancelRowEvent(row)">取消</Button>
      </template>
      <template v-else>
        <Button type="link" @click="editRowEvent(row)">编辑</Button>
        <Button type="link" danger @click="()=>delField(row)" >删除</Button>

      </template>
    </template>
  </base-table>
</template>

<style scoped>

</style>
