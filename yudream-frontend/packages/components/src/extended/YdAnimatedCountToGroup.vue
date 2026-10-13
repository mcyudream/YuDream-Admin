<template>
  <span class="yd-count-group"><slot /></span>
</template>

<style scoped>
.yd-count-group {
  display: inline-flex;
  flex-wrap: wrap;
  gap: var(--yd-content-gap, 16px);
  align-items: baseline;
}
</style>
