<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getUserInfo } from '@/api/auth'
import { resetUserPassword } from '@/api/user'
import { useAuthStore } from '@/stores/auth'
import { formatDate } from '@/utils/format'

const authStore = useAuthStore()

interface UserInfo {
  userId?: number
  username?: string
  realName?: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  roles?: { roleName: string; roleCode: string }[]
  createTime?: string
}

const userInfo = ref<UserInfo | null>(null)
const loading = ref(false)

const passwordFormRef = ref<FormInstance>()
const passwordLoading = ref(false)
const passwordDialogVisible = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules: FormRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

onMounted(async () => {
  await fetchUserInfo()
})

async function fetchUserInfo() {
  loading.value = true
  try {
    const res = await getUserInfo()
    userInfo.value = res as unknown as UserInfo
  } catch (e) {
    console.error('Failed to fetch user info:', e)
  } finally {
    loading.value = false
  }
}

function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

async function handlePasswordSubmit() {
  if (!passwordFormRef.value) return

  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return

    passwordLoading.value = true
    try {
      const userId = userInfo.value?.userId || authStore.userInfo?.userId
      if (!userId) {
        ElMessage.error('无法获取用户ID')
        return
      }
      await resetUserPassword(Number(userId), passwordForm.newPassword)
      ElMessage.success('密码修改成功')
      passwordDialogVisible.value = false
    } catch (e: any) {
      ElMessage.error(e.message || '密码修改失败')
    } finally {
      passwordLoading.value = false
    }
  })
}
</script>

<template>
  <div class="personal-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">个人中心</h2>
      <p class="page-desc">管理您的个人信息和账户安全</p>
    </div>

    <div class="personal-content">
      <!-- 用户信息卡片 -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">基本信息</span>
          </div>
        </template>

        <div v-loading="loading">
          <!-- 头像区域 -->
          <div class="avatar-section">
            <div class="avatar-wrapper">
              <img
                v-if="userInfo?.avatar"
                :src="userInfo.avatar"
                class="avatar-img"
                alt="头像"
              />
              <div v-else class="avatar-placeholder">
                {{ (userInfo?.realName || userInfo?.username || '管').slice(0, 1) }}
              </div>
            </div>
            <div class="avatar-info">
              <h3 class="user-name">{{ userInfo?.realName || userInfo?.username || '管理员' }}</h3>
              <p class="user-account">账号：{{ userInfo?.username }}</p>
            </div>
          </div>

          <!-- 信息列表 -->
          <div class="info-list">
            <div class="info-item">
              <span class="info-label">昵称</span>
              <span class="info-value">{{ userInfo?.nickname || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">邮箱</span>
              <span class="info-value">{{ userInfo?.email || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">手机号</span>
              <span class="info-value">{{ userInfo?.phone || '-' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">角色</span>
              <span class="info-value">
                <template v-if="userInfo?.roles && userInfo.roles.length">
                  <el-tag
                    v-for="role in userInfo.roles"
                    :key="role.roleCode"
                    size="small"
                    class="role-tag"
                  >
                    {{ role.roleName }}
                  </el-tag>
                </template>
                <template v-else>-</template>
              </span>
            </div>
            <div class="info-item">
              <span class="info-label">创建时间</span>
              <span class="info-value">{{ formatDate(userInfo?.createTime) || '-' }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 安全设置卡片 -->
      <el-card class="security-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">安全设置</span>
          </div>
        </template>

        <div class="security-list">
          <div class="security-item">
            <div class="security-info">
              <h4 class="security-title">登录密码</h4>
              <p class="security-desc">已设置密码，保障账户安全</p>
            </div>
            <el-button type="primary" plain size="small" @click="openPasswordDialog">
              修改密码
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 修改密码弹窗 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="80px"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="请输入原密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码（至少6位）"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="handlePasswordSubmit">
          确认修改
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.personal-container {
  max-width: 900px;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 700;
  color: #1a1c1e;
  margin: 0 0 4px 0;
}

.page-desc {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
}

.personal-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-card,
.security-card {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

:deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid #f3f4f6;
}

:deep(.el-card__body) {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1c1e;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f3f4f6;
  margin-bottom: 16px;
}

.avatar-wrapper {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #408aee, #3066e0);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24px;
  font-weight: 700;
}

.avatar-info {
  flex: 1;
}

.user-name {
  font-size: 18px;
  font-weight: 700;
  color: #1a1c1e;
  margin: 0 0 4px 0;
}

.user-account {
  font-size: 13px;
  color: #9ca3af;
  margin: 0;
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  font-size: 14px;
}

.info-label {
  width: 80px;
  color: #6b7280;
  flex-shrink: 0;
}

.info-value {
  color: #1a1c1e;
  font-weight: 500;
}

.role-tag {
  margin-right: 6px;
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #3b82f6;
}

.security-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f3f4f6;
}

.security-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.security-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1c1e;
  margin: 0 0 2px 0;
}

.security-desc {
  font-size: 13px;
  color: #9ca3af;
  margin: 0;
}

.security-info {
  flex: 1;
}
</style>
