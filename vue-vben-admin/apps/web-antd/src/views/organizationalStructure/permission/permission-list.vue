<script setup lang="ts">
import type {Role} from "#/types/organizationalStructure/role";
import {Transfer, Table, message} from "ant-design-vue";
import { ref, watch, onMounted } from 'vue';
import type {Permission} from "#/types/organizationalStructure/permission";
import {
  getAllPermissionsApi,
  getPermissionByRoleApi,
  setPermissionApi
} from "#/api/core/permission";
import {Button} from "ant-design-vue";
import {useVbenModal} from '@vben/common-ui';
import CreatePermissionModal
  from "#/views/organizationalStructure/permission/create-permission-modal.vue";

const props = defineProps({
  role: {
    type: Object as () => Role
  }
})

const [CreateModal, createModalApi] = useVbenModal({
    connectedComponent: CreatePermissionModal
  })

const selectedPermissions = ref<string[]>([])
const permissions = ref<Permission[]>([])

const getAllPermissions = async () => {
  permissions.value = await getAllPermissionsApi()
}



const getPermissionList = async () => {
  if (!props.role || props.role.id === undefined){
    return
  }
  selectedPermissions.value = await getPermissionByRoleApi(props.role)??[]
}

onMounted(() => {
  getAllPermissions()
  getPermissionList()
})
watch(()=>props.role, () => {
  getPermissionList()
})


type tableColumn = Record<string, string>;



const leftTableColumns = [
  {
    dataIndex: 'id',
    title: 'ID',
  },
  {
    dataIndex: 'name',
    title: 'Name',
  },
];

const setPermission = async (permission: string[]) => {
  const res = await setPermissionApi(props.role??{}, permission);
  // @ts-ignore
  message.success(res.message)
}

const leftColumns = ref<tableColumn[]>(leftTableColumns);

const onChange = (nextTargetKeys: string[]) => {
  console.log('nextTargetKeys', nextTargetKeys);
  setPermission(nextTargetKeys);
};

const getRowSelection = ({
                           disabled,
                           selectedKeys,
                           onItemSelectAll,
                           onItemSelect,
                         }: Record<string, any>) => {
  return {
    getCheckboxProps: (item: Record<string, string | boolean>) => ({
      disabled: disabled || item.disabled,
    }),
    onSelectAll(selected: boolean, selectedRows: Record<string, string | boolean>[]) {
      const treeSelectedKeys = selectedRows.filter(item => !item.disabled).map(({ key }) => key);
      onItemSelectAll(treeSelectedKeys, selected);
    },
    onSelect({ key }: Record<string, string>, selected: boolean) {
      onItemSelect(key, selected);
    },
    selectedRowKeys: selectedKeys,
  };
};

</script>

<template>
  <div class="card-box p-2 space-y-2">
    <div class="toolbar-tools flex-row">
      <Button type="primary" @click="createModalApi.open()" >新增权限</Button>
    </div>
    <Transfer
      v-model:target-keys="selectedPermissions"
      :data-source="permissions"
      :show-search="true"
      :rowKey="record => record.id"
      @change="onChange"
    >
      <template
        #children="{
          filteredItems,
          selectedKeys,
          disabled: listDisabled,
          onItemSelectAll,
          onItemSelect,
        }"
      >
        <Table
          :row-selection="
            getRowSelection({
              disabled: listDisabled,
              selectedKeys,
              onItemSelectAll,
              onItemSelect,
            })
          "
          :columns="leftColumns"
          :data-source="filteredItems"
          size="small"

          :style="{ pointerEvents: listDisabled ? 'none' : null }"
          :custom-row="
            ({ key, disabled: itemDisabled }) => ({
              onClick: () => {
                if (itemDisabled || listDisabled) return;
                console.log(key)
                onItemSelect(key, !selectedKeys.includes(key));
              },
            })
          "
        />
      </template>
    </Transfer>
    <CreateModal @on-submit="getAllPermissions"/>
  </div>
</template>

<style scoped>

</style>
