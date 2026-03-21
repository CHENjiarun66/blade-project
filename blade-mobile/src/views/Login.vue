<template>
  <v-container class="fill-height" fluid>
    <v-row align="center" justify="center">
      <v-col cols="12" sm="8" md="4">
        <v-card class="pa-4">
          <v-card-title class="text-h5 text-center mb-4">
            <v-icon size="48" color="primary" class="mb-2">mdi-tshirt-crew</v-icon>
            <div>Blade 服装订单</div>
          </v-card-title>

          <v-card-text>
            <v-form ref="formRef" @submit.prevent="handleLogin">
              <v-text-field
                v-model="loginForm.username"
                label="用户名"
                prepend-inner-icon="mdi-account"
                :rules="[rules.required]"
                variant="outlined"
                class="mb-2"
              />

              <v-text-field
                v-model="loginForm.password"
                label="密码"
                :type="showPassword ? 'text' : 'password'"
                prepend-inner-icon="mdi-lock"
                :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
                @click:append-inner="showPassword = !showPassword"
                :rules="[rules.required]"
                variant="outlined"
                class="mb-4"
              />

              <v-btn
                type="submit"
                color="primary"
                size="large"
                block
                :loading="loading"
              >
                登录
              </v-btn>
            </v-form>
          </v-card-text>

          <v-card-actions class="justify-center">
            <div class="text-caption text-medium-emphasis">
              演示账号: admin / admin123
            </div>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { showToast } from '@/utils/toast'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref()
const loading = ref(false)
const showPassword = ref(false)

const loginForm = ref({
  username: 'admin',
  password: 'admin123'
})

const rules = {
  required: (v: string) => !!v || '必填'
}

async function handleLogin() {
  const { valid } = await formRef.value.validate()
  if (!valid) return

  loading.value = true
  try {
    await authStore.loginAction(loginForm.value)
    const redirect = route.query.redirect as string || '/'
    router.push(redirect)
  } catch (error: any) {
    showToast(error.response?.data?.message || '登录失败', 'error')
  } finally {
    loading.value = false
  }
}
</script>
