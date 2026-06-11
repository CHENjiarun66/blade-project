<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Shirt, Building2, User, Lock, ShieldCheck, ArrowRight } from 'lucide-vue-next'
import { login, getAuthCodes, getUserInfo } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)

const loginForm = reactive({
  tenant: '',
  account: '',
  password: '',
  captcha: '',
  remember: true
})

const showPassword = ref(false)
const captchaCode = ref('')

// 根据用户权限计算第一个可访问的页面
function getFirstAccessiblePage(permissions: string[]): string {
  const pagePermissionMap: Record<string, string> = {
    '/dashboard': 'menu:dashboard',
    '/analytics': 'menu:analytics',
    '/orders': 'menu:order',
    '/inventory': 'menu:inventory',
    '/products': 'menu:product',
    '/clients': 'menu:customer',
    '/files': 'menu:file',
    '/system': 'menu:system',
    '/catalog': 'data:catalog:view',
  }

  // 按优先级顺序查找第一个有权限的页面
  const priorityPages = ['/dashboard', '/analytics', '/orders', '/inventory', '/products', '/clients', '/files', '/system', '/catalog']
  for (const page of priorityPages) {
    const requiredPermission = pagePermissionMap[page]
    if (permissions.includes(requiredPermission)) {
      return page
    }
  }

  // 如果没有任何页面权限，默认跳转到登录页（不常见）
  return '/login'
}

function getSafeRedirect(redirect: unknown): string {
  if (typeof redirect !== 'string') return ''
  if (!redirect.startsWith('/') || redirect.startsWith('//')) return ''
  if (redirect.startsWith('/login')) return ''
  return redirect
}

function canAccessPath(path: string, permissions: string[]): boolean {
  const pathname = path.split('?')[0].split('#')[0]
  const pagePermissionMap: Record<string, string> = {
    '/dashboard': 'menu:dashboard',
    '/analytics': 'menu:analytics',
    '/orders': 'menu:order',
    '/inventory': 'menu:inventory',
    '/products': 'menu:product',
    '/clients': 'menu:customer',
    '/files': 'menu:file',
    '/system': 'menu:system',
    '/catalog': 'data:catalog:view',
  }

  const match = Object.keys(pagePermissionMap)
    .sort((a, b) => b.length - a.length)
    .find((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))

  if (!match) return false
  return permissions.includes(pagePermissionMap[match])
}

const handleLogin = async () => {
  if (!loginForm.tenant || !loginForm.account || !loginForm.password || !loginForm.captcha) {
    ElMessage.warning('请填写完整登录信息')
    return
  }

  // 验证码校验
  if (loginForm.captcha.toUpperCase() !== captchaCode.value.toUpperCase()) {
    ElMessage.warning('验证码错误')
    refreshCaptcha()
    return
  }

  loading.value = true
  try {
    const res = await login({
      tenantCode: loginForm.tenant,
      username: loginForm.account,
      password: loginForm.password,
      remember: loginForm.remember
    })
    const token = (res as any).accessToken || (res as any).token
    authStore.setTokens(token, (res as any).refreshToken)

    // 获取用户信息和权限码
    try {
      const [userInfoRes, codesRes] = await Promise.all([
        getUserInfo(),
        getAuthCodes()
      ])
      // 设置用户信息
      authStore.setUserInfo({
        userId: String((userInfoRes as any).userId || '1'),
        username: (userInfoRes as any).username || loginForm.account,
        realName: (userInfoRes as any).realName || '管理员',
        avatar: (userInfoRes as any).avatar,
        roles: (userInfoRes as any).roles,
      })
      // 设置权限码
      authStore.setPermissions(codesRes as unknown as string[])
    } catch (e) {
      console.error('[Login] Failed to fetch user info:', e)
      // 设置基本信息作为后备
      authStore.setUserInfo({
        userId: '1',
        username: loginForm.account,
        realName: '管理员',
      })
    }
    ElMessage.success('登录成功')

    // 计算用户有权限访问的第一个页面
    const permissions = authStore.permissions || []
    const redirect = getSafeRedirect(route.query.redirect)
    const firstAccessiblePage = redirect && canAccessPath(redirect, permissions)
      ? redirect
      : getFirstAccessiblePage(permissions)
    try {
      await router.replace(firstAccessiblePage)
      console.log('[Login] Navigation to:', firstAccessiblePage)
    } catch (e) {
      console.error('[Login] Navigation failed:', e)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '登录失败')
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

const refreshCaptcha = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let newCode = ''
  for (let i = 0; i < 4; i++) {
    newCode += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  captchaCode.value = newCode
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<template>
  <div class="login-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration bg-decoration-1"></div>
    <div class="bg-decoration bg-decoration-2"></div>
    <!-- 顶部装饰线 -->
    <div class="top-line"></div>

    <main class="login-container">
      <!-- 品牌标识 -->
      <div class="brand-section">
        <div class="brand-logo">
          <Shirt class="brand-icon" />
        </div>
        <h1 class="brand-title">BLADE ADMIN</h1>
        <p class="brand-subtitle">服装订单管理系统</p>
      </div>

      <!-- 登录卡片 -->
      <section class="login-card">
        <div class="login-header">
          <h2 class="login-title">欢迎回来</h2>
          <p class="login-desc">请输入您的详细信息以登录</p>
        </div>

        <form @submit.prevent="handleLogin" class="login-form">
          <!-- 所属企业 -->
          <div class="form-group">
            <label class="form-label">所属企业</label>
            <div class="input-wrapper">
              <div class="input-icon">
                <Building2 />
              </div>
              <input
                v-model="loginForm.tenant"
                class="form-input"
                placeholder="输入公司 ID 或名称"
                type="text"
              />
            </div>
          </div>

          <!-- 账号 -->
          <div class="form-group">
            <label class="form-label">账号</label>
            <div class="input-wrapper">
              <div class="input-icon">
                <User />
              </div>
              <input
                v-model="loginForm.account"
                class="form-input"
                placeholder="您的管理员账号"
                type="text"
              />
            </div>
          </div>

          <!-- 密码 -->
          <div class="form-group">
            <div class="form-label-row">
              <label class="form-label">密码</label>
              <a class="forgot-link" href="javascript:void(0)">忘记密码？</a>
            </div>
            <div class="input-wrapper">
              <div class="input-icon">
                <Lock />
              </div>
              <input
                v-model="loginForm.password"
                :type="showPassword ? 'text' : 'password'"
                class="form-input"
                placeholder="••••••••"
              />
              <button class="password-toggle" type="button" @click="showPassword = !showPassword">
                <span v-if="!showPassword" class="toggle-icon">👁</span>
                <span v-else class="toggle-icon">👁‍🗨</span>
              </button>
            </div>
          </div>

          <!-- 验证码 -->
          <div class="form-group">
            <div class="form-label-row">
              <label class="form-label">验证码</label>
              <a @click="refreshCaptcha" class="forgot-link" href="javascript:void(0)">看不清？换一张</a>
            </div>
            <div class="captcha-row">
              <div class="input-wrapper captcha-input">
                <div class="input-icon">
                  <ShieldCheck />
                </div>
                <input
                  v-model="loginForm.captcha"
                  class="form-input"
                  placeholder="输入验证码"
                  type="text"
                />
              </div>
              <div class="captcha-code" @click="refreshCaptcha">
                <span class="captcha-text">{{ captchaCode }}</span>
              </div>
            </div>
          </div>

          <!-- 记住我 -->
          <div class="remember-row">
            <el-checkbox v-model="loginForm.remember" label="保持登录状态 (30天)" size="large" />
          </div>

          <!-- 登录按钮 -->
          <button
            class="login-button"
            type="submit"
            :disabled="loading"
          >
            <span v-if="loading">登录中...</span>
            <template v-else>
              <span>登录控制台</span>
              <ArrowRight class="button-icon" />
            </template>
          </button>
        </form>
      </section>

      <!-- 页脚 -->
      <p class="footer-text">
        第一次使用 Blade Admin？
        <a class="footer-link" href="javascript:void(0)">创建新账户</a>
      </p>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7f9;
  color: #2c2f31;
  padding: 24px;
  position: relative;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* 背景装饰 */
.bg-decoration {
  position: fixed;
  border-radius: 50%;
  background: rgba(64, 138, 238, 0.05);
  filter: blur(48px);
  pointer-events: none;
}

.bg-decoration-1 {
  width: 256px;
  height: 256px;
  bottom: -96px;
  left: -96px;
}

.bg-decoration-2 {
  width: 320px;
  height: 320px;
  top: -96px;
  right: -96px;
}

/* 顶部装饰线 */
.top-line {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: #408aee;
}

.login-container {
  width: 100%;
  max-width: 480px;
  position: relative;
  z-index: 10;
}

/* 品牌标识 */
.brand-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 40px;
}

.brand-logo {
  width: 64px;
  height: 64px;
  background: #408aee;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  box-shadow: 0 4px 12px rgba(64, 138, 238, 0.15);
  transition: transform 0.2s;
  cursor: pointer;
}

.brand-logo:hover {
  transform: scale(1.05);
}

.brand-icon {
  width: 40px;
  height: 40px;
  color: #fff;
}

.brand-title {
  font-size: 28px;
  font-weight: 900;
  color: #2c2f31;
  letter-spacing: 2px;
  text-transform: uppercase;
  margin: 0;
}

.brand-subtitle {
  font-size: 14px;
  font-weight: 500;
  color: #595c5e;
  margin: 4px 0 0 0;
  letter-spacing: 0.5px;
}

/* 登录卡片 */
.login-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(223, 227, 230, 0.5);
  padding: 40px;
}

.login-header {
  margin-bottom: 32px;
}

.login-title {
  font-size: 22px;
  font-weight: 700;
  color: #2c2f31;
  margin: 0;
  letter-spacing: -0.5px;
}

.login-desc {
  font-size: 14px;
  color: #595c5e;
  margin: 4px 0 0 0;
}

/* 表单 */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 12px;
  font-weight: 700;
  color: #595c5e;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-left: 4px;
}

.form-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.forgot-link {
  font-size: 12px;
  font-weight: 600;
  color: #408aee;
  text-decoration: none;
  transition: color 0.2s;
}

.forgot-link:hover {
  color: #3577cd;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 16px;
  color: #abadaf;
  display: flex;
  align-items: center;
  transition: color 0.2s;
}

.input-wrapper:focus-within .input-icon {
  color: #408aee;
}

.input-icon svg {
  width: 20px;
  height: 20px;
}

.form-input {
  width: 100%;
  padding: 14px 16px 14px 48px;
  background: #f3f5f7;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  color: #2c2f31;
  transition: box-shadow 0.2s;
  outline: none;
}

.form-input::placeholder {
  color: #abadaf;
}

.form-input:focus {
  box-shadow: 0 0 0 3px rgba(64, 138, 238, 0.15);
}

/* 密码切换 */
.password-toggle {
  position: absolute;
  right: 16px;
  background: none;
  border: none;
  cursor: pointer;
  color: #abadaf;
  display: flex;
  align-items: center;
  padding: 0;
  transition: color 0.2s;
}

.password-toggle:hover {
  color: #2c2f31;
}

.toggle-icon {
  font-size: 16px;
}

/* 验证码 */
.captcha-row {
  display: flex;
  gap: 12px;
}

.captcha-input {
  flex: 1;
}

.captcha-code {
  width: 128px;
  background: #dfe3e6;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 1px solid rgba(223, 227, 230, 0.5);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  transition: background 0.2s;
}

.captcha-code:hover {
  background: #d9dde0;
}

.captcha-text {
  font-size: 20px;
  font-weight: 900;
  letter-spacing: 4px;
  color: #408aee;
  font-style: italic;
  opacity: 0.85;
}

/* 记住我 */
.remember-row {
  display: flex;
  align-items: center;
}

/* 登录按钮 */
.login-button {
  width: 100%;
  padding: 16px;
  background: #408aee;
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(64, 138, 238, 0.25);
  transition: all 0.2s;
}

.login-button:hover:not(:disabled) {
  background: #3577cd;
  transform: translateY(-1px);
}

.login-button:active:not(:disabled) {
  transform: scale(0.98);
}

.login-button:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.button-icon {
  width: 20px;
  height: 20px;
}

/* 页脚 */
.footer-text {
  margin-top: 32px;
  text-align: center;
  font-size: 14px;
  font-weight: 500;
  color: #595c5e;
}

.footer-link {
  color: #408aee;
  font-weight: 700;
  text-decoration: none;
}

.footer-link:hover {
  text-decoration: underline;
  text-underline-offset: 4px;
}

/* Element Plus 复选框覆盖 */
:deep(.el-checkbox__label) {
  color: #595c5e !important;
  font-size: 14px !important;
  font-weight: 500 !important;
}

:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: #408aee !important;
  border-color: #408aee !important;
}
</style>
