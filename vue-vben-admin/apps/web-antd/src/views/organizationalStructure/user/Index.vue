<script setup lang="ts">
import {Row,Col} from 'ant-design-vue'
import UserTable from "#/views/organizationalStructure/user/user-table.vue";
import DepartmentTree from "#/views/organizationalStructure/user/department-tree.vue";
import {ref} from "vue";
const departmentId = ref<string>('all');
</script>

<template>
  <div class="p-5">
    <Row :gutter="20">
      <Col :span="6">
        <div class="card-box p-2 space-y-4">
          <span class="font-bold ">部门选择</span>
          <department-tree v-model:current-id="departmentId"/>
        </div>
      </Col>
      <Col :span="18">
        <div class="card-box p-2">
          <user-table :department-id="departmentId" />
        </div>
      </Col>
    </Row>
  </div>
</template>

<style scoped>

</style>
