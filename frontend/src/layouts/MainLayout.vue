<script setup>
import Sidebar from '@/components/Sidebar.vue'
</script>

<template>
  <div class="layout">
    <Sidebar />
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: #f1f5f9;
}
.main-content {
  margin-left: 220px;
  flex: 1;
  padding: 24px;
}
</style>
