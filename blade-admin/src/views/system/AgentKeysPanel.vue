<template>
  <el-card>
    <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
      <div>
        <div class="flex items-center gap-2">
          <span class="material-symbols-outlined text-blue-500">key</span>
          <h2 class="text-lg font-bold text-slate-800">Agent Key</h2>
        </div>
        <p class="mt-1 text-sm leading-6 text-slate-500">
          给 Mac 上的 Agent 签发独立凭证。Key 自动绑定当前租户，不能确认订单、收款或操作库存。
        </p>
      </div>
      <el-button type="primary" :loading="creating" @click="openCreateDialog">
        <span class="material-symbols-outlined mr-1 text-sm">add</span>新建 Agent Key
      </el-button>
    </div>

    <el-alert class="mt-4" type="info" :closable="false" show-icon>
      <template #title>外网地址由 Mac Agent 配置</template>
      不把地址写进 Key。当前浏览器可生成配置片段，地址变化后只需修改
      <code>BLADE_AGENT_API_BASE_URL</code>。
    </el-alert>

    <div class="mt-5 overflow-hidden rounded-xl border border-slate-200">
      <el-table :data="keys" v-loading="loading" stripe empty-text="尚未签发 Agent Key">
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="keyPrefix" label="Key 前缀" min-width="180">
          <template #default="{ row }"><code class="text-xs text-slate-600">{{ row.keyPrefix }}</code></template>
        </el-table-column>
        <el-table-column label="权限范围" min-width="250">
          <template #default="{ row }">
            <div class="flex flex-wrap gap-1">
              <el-tag v-for="scope in row.scopes" :key="scope" size="small" type="info">{{ scopeLabel(scope) }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row)">{{ statusText(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期至" width="180">
          <template #default="{ row }">{{ formatDate(row.expiresTime) }}</template>
        </el-table-column>
        <el-table-column label="最近使用" min-width="190">
          <template #default="{ row }">
            <div class="text-sm text-slate-600">{{ row.lastUsedTime ? formatDate(row.lastUsedTime) : '从未使用' }}</div>
            <div v-if="row.lastUsedIp" class="mt-0.5 text-xs text-slate-400">{{ row.lastUsedIp }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="primary" @click="handleRotate(row)">轮换</el-button>
            <el-button v-if="row.status === 1" link type="danger" @click="handleDisable(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createDialogVisible" title="新建 Agent Key" width="520px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-position="top">
        <el-form-item label="名称" prop="name">
          <el-input v-model="createForm.name" maxlength="100" show-word-limit placeholder="例如：Mac 纸单录入 Agent" />
        </el-form-item>
        <el-form-item label="权限范围" prop="scopes">
          <el-checkbox-group v-model="createForm.scopes" class="flex flex-col gap-3">
            <el-checkbox v-for="scope in availableScopes" :key="scope" :value="scope">
              <span class="font-medium text-slate-700">{{ scopeLabel(scope) }}</span>
              <span class="ml-2 text-xs text-slate-400">{{ scopeDescription(scope) }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="有效期" prop="expiresInDays">
          <el-input-number v-model="createForm.expiresInDays" :min="1" :max="365" controls-position="right" />
          <span class="ml-2 text-sm text-slate-500">天，建议每 90 天轮换</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建并显示密钥</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="credentialDialogVisible" title="保存 Agent 凭证" width="620px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon title="完整密钥只显示这一次">
        关闭窗口后系统无法再次显示。请立即复制到 Mac 钥匙串或受保护的 Agent 配置中。
      </el-alert>
      <div v-if="credential" class="mt-5 space-y-4">
        <div>
          <label class="mb-1 block text-sm font-medium text-slate-700">完整 Agent Key</label>
          <el-input :model-value="credential.agentKey" readonly type="textarea" :rows="2" />
          <el-button class="mt-2" type="primary" plain @click="copyText(credential.agentKey, 'Agent Key')">复制密钥</el-button>
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-slate-700">Mac Agent API 地址</label>
          <el-input v-model="agentBaseUrl" placeholder="https://frp-pen.com:33294" @change="saveBaseUrl" />
          <p class="mt-1 text-xs text-slate-400">仅用于生成下面的配置片段，保存在当前浏览器；不会写入 Key 或服务器。</p>
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-slate-700">Agent 环境配置</label>
          <el-input :model-value="environmentSnippet" readonly type="textarea" :rows="3" />
          <el-button class="mt-2" @click="copyText(environmentSnippet, '环境配置')">复制配置</el-button>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="credentialDialogVisible = false">我已安全保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createAgentKey,
  disableAgentKey,
  getAgentKeys,
  getAgentKeyScopes,
  rotateAgentKey,
  type AgentKeyCredential,
  type AgentKeyView,
} from '@/api/agentKey'
import { formatDate } from '@/utils/format'

const DEFAULT_EXTERNAL_URL = 'https://frp-pen.com:33294'
const BASE_URL_STORAGE_KEY = 'bladeAgentApiBaseUrl'
const keys = ref<AgentKeyView[]>([])
const availableScopes = ref<string[]>([])
const loading = ref(false)
const creating = ref(false)
const createDialogVisible = ref(false)
const credentialDialogVisible = ref(false)
const credential = ref<AgentKeyCredential | null>(null)
const createFormRef = ref<FormInstance>()
const agentBaseUrl = ref(localStorage.getItem(BASE_URL_STORAGE_KEY) || DEFAULT_EXTERNAL_URL)

const createForm = reactive({
  name: 'Mac 纸单录入 Agent',
  scopes: ['catalog:read', 'orders:write'] as string[],
  expiresInDays: 90,
})

const createRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  scopes: [{ type: 'array', required: true, min: 1, message: '至少选择一个权限', trigger: 'change' }],
  expiresInDays: [{ required: true, message: '请输入有效期', trigger: 'change' }],
}

const environmentSnippet = computed(() => {
  if (!credential.value) return ''
  const baseUrl = normalizeBaseUrl(agentBaseUrl.value)
  return `BLADE_AGENT_API_BASE_URL=${baseUrl}\nBLADE_AGENT_KEY=${credential.value.agentKey}`
})

async function loadData() {
  loading.value = true
  try {
    const [keyResponse, scopeResponse] = await Promise.all([getAgentKeys(), getAgentKeyScopes()])
    keys.value = keyResponse.data || []
    availableScopes.value = scopeResponse.data || []
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  Object.assign(createForm, {
    name: 'Mac 纸单录入 Agent',
    scopes: ['catalog:read', 'orders:write'],
    expiresInDays: 90,
  })
  createDialogVisible.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate()
  creating.value = true
  try {
    const response = await createAgentKey({ ...createForm })
    credential.value = response.data
    createDialogVisible.value = false
    credentialDialogVisible.value = true
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || 'Agent Key 创建失败')
  } finally {
    creating.value = false
  }
}

async function handleRotate(row: AgentKeyView) {
  await ElMessageBox.confirm(
    `轮换后旧 Key「${row.keyPrefix}」会立即失效。请确认 Mac Agent 可以及时更新新密钥。`,
    '轮换 Agent Key',
    { type: 'warning', confirmButtonText: '继续轮换' },
  )
  try {
    const response = await rotateAgentKey(row.id, 90)
    credential.value = response.data
    credentialDialogVisible.value = true
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || 'Agent Key 轮换失败')
  }
}

async function handleDisable(row: AgentKeyView) {
  await ElMessageBox.confirm(
    `停用后使用「${row.keyPrefix}」的 Agent 会立即无法访问系统，且不能重新启用。`,
    '停用 Agent Key',
    { type: 'warning', confirmButtonText: '确认停用' },
  )
  try {
    await disableAgentKey(row.id)
    ElMessage.success('Agent Key 已停用')
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || 'Agent Key 停用失败')
  }
}

function normalizeBaseUrl(value: string) {
  return value.trim().replace(/\/+$/, '')
}

function saveBaseUrl() {
  try {
    const normalized = normalizeBaseUrl(agentBaseUrl.value)
    const parsed = new URL(normalized)
    if (!['http:', 'https:'].includes(parsed.protocol) || (parsed.pathname && parsed.pathname !== '/') || parsed.search || parsed.hash) {
      throw new Error('invalid')
    }
    agentBaseUrl.value = normalized
    localStorage.setItem(BASE_URL_STORAGE_KEY, normalized)
  } catch {
    ElMessage.error('请输入仅包含协议、域名和端口的地址，不要包含 /api 或其他路径')
  }
}

async function copyText(value: string, label: string) {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(`${label}已复制`)
  } catch {
    ElMessage.error('复制失败，请手动选择并复制')
  }
}

function scopeLabel(scope: string) {
  return ({
    'catalog:read': '查询商品候选',
    'orders:write': '创建订单草稿',
    'analytics:read': '读取经营分析',
    'whatsapp:analyze': 'WhatsApp 分析任务',
  } as Record<string, string>)[scope] || scope
}

function scopeDescription(scope: string) {
  return ({
    'catalog:read': '按款号、颜色和尺码匹配 SKU',
    'orders:write': '仅生成待人工确认的草稿',
    'analytics:read': '读取已授权的聚合数据',
    'whatsapp:analyze': '领取并回传分析结果',
  } as Record<string, string>)[scope] || ''
}

function statusText(row: AgentKeyView) {
  if (row.status === 0) return '已停用'
  if (row.expired) return '已过期'
  return '使用中'
}

function statusType(row: AgentKeyView): 'success' | 'warning' | 'info' {
  if (row.status === 0) return 'info'
  if (row.expired) return 'warning'
  return 'success'
}

onMounted(loadData)
</script>
