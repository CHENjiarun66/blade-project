<template>
  <v-app>
    <v-app-bar color="primary" density="compact">
      <v-app-bar-title>{{ route.meta.title }}</v-app-bar-title>
      <template #append>
        <v-btn icon @click="handleLogout" variant="text">
          <v-icon>mdi-logout</v-icon>
        </v-btn>
      </template>
    </v-app-bar>

    <v-main>
      <router-view />
    </v-main>

    <v-bottom-navigation grow color="primary">
      <v-btn to="/orders">
        <v-icon>mdi-clipboard-list</v-icon>
        <span>订单</span>
      </v-btn>
      <v-btn to="/inventory">
        <v-icon>mdi-warehouse</v-icon>
        <span>库存</span>
      </v-btn>
      <v-btn to="/products">
        <v-icon>mdi-tshirt-crew</v-icon>
        <span>商品</span>
      </v-btn>
      <v-btn to="/dashboard">
        <v-icon>mdi-chart-line</v-icon>
        <span>看板</span>
      </v-btn>
    </v-bottom-navigation>
  </v-app>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

async function handleLogout() {
  await authStore.logoutAction()
  router.push({ name: 'Login' })
}
</script>
