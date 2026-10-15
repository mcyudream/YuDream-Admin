<script setup lang="ts">
import YdList from '../YdList.vue'
import YdListRow from '../YdListRow.vue'
</script>

<template>
  <YdList divided>
    <YdListRow grow><span>♪</span><span>歌曲标题与简介</span><span>03:42</span></YdListRow>
    <YdListRow grow><span>♫</span><span>另一首歌曲</span><span>04:10</span></YdListRow>
  </YdList>
</template>
