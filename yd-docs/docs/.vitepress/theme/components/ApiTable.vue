<script setup>
const props = defineProps({
  title: { type: String, default: '' },
  data: { type: Array, required: true },
  columns: { type: Array, default: () => ['参数', '类型', '默认值', '说明'] }
})
</script>

<template>
  <div class="yd-api-table">
    <div v-if="title" class="yd-api-table__title">{{ title }}</div>
    <table>
      <thead>
        <tr>
          <th v-for="c in columns" :key="c">{{ c }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, i) in data" :key="i">
          <td v-for="(cell, j) in row" :key="j" v-html="cell" />
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.yd-api-table {
  margin: 16px 0;
}
.yd-api-table__title {
  font-weight: 600;
  margin-bottom: 8px;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
th,
td {
  border: 1px solid var(--vp-c-divider);
  padding: 8px 12px;
  text-align: left;
  vertical-align: top;
}
th {
  background: var(--vp-c-bg-soft);
}
td :deep(code) {
  font-size: 12px;
}
</style>
