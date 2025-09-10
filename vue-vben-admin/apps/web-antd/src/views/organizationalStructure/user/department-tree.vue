<script setup lang="ts">
import {Tree,Button} from 'ant-design-vue'
import type {TreeVo} from "#/types/tree";
import {onMounted, ref} from "vue";
import {getDepartmentTreeApi} from "#/api/core/department";
import type {Key} from "ant-design-vue/lib/_util/type";

const currentId = defineModel<string>("currentId",{default:'all'});
const departmentTree = ref<TreeVo[]>([])
const expandedKeys = ref<string[]>();
const selectedKeys = ref<string[]>([]);

const getTree = async () => {
  departmentTree.value = await getDepartmentTreeApi()
}
onMounted(() => {
  getTree()
})

const onSelect = (selectedKeys: Key[],{}) => {
  currentId.value = selectedKeys[0] as string
}

const props = defineProps({
  showAll: {
    type: Boolean,
    default: true
  }
})

</script>

<template>
  <div class="flex flex-col space-y-2">

    <Tree show-line @select="onSelect" v-model:expanded-keys="expandedKeys" v-model:selected-keys="selectedKeys" :tree-data="departmentTree">

    </Tree>
    <Button v-if="showAll" type="primary" @click="currentId='all'">
      查看全部
    </Button>
  </div>
</template>

<style scoped>

</style>
