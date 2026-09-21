<template>
  <el-card>
    <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
      <div>
        <div class="flex items-center gap-2">
          <span class="material-symbols-outlined text-blue-500">key</span>
          <h2 class="text-lg font-bold text-slate-800">Agent Key</h2>
        </div>
        <p class="mt-1 text-sm leading-6 text-slate-500">
          给 Mac 上的 Agent 签发独立凭证。Key 自动绑定当前租户，不能确认订单、收款、操作库存或修改删除资料。
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
              <el-tag v-for="scope in row.scopes" :key="scope" size="small" :type="scopeTagType(scope)">
                {{ scopeLabel(scope) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="档口范围" min-width="240">
          <template #default="{ row }">
            <div class="flex flex-col gap-1">
              <div class="flex flex-wrap items-center gap-1">
                <el-tag v-if="row.outletScopeType === 'ALL'" size="small" type="success">全部档口</el-tag>
                <template v-else-if="row.outletScopeType === 'ASSIGNED'">
                  <el-tag
                    v-for="outlet in row.outlets"
                    :key="outlet.id"
                    size="small"
                    :type="outlet.status === 1 ? 'primary' : 'info'"
                    :class="{ 'opacity-60': outlet.status !== 1 }"
                  >
                    {{ outlet.outletName }}<span v-if="outlet.status !== 1">（已停用）</span>
                  </el-tag>
                  <span v-if="!row.outlets || row.outlets.length === 0" class="text-xs text-slate-400">未绑定档口</span>
                </template>
                <el-tag v-else size="small" type="info">不开放档口数据</el-tag>
              </div>
              <div v-if="row.outletScopeType !== 'NONE' && defaultOutletName(row)" class="text-xs text-slate-400">
                默认档口：{{ defaultOutletName(row) }}
              </div>
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
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 1" link type="primary" @click="openRotateDialog(row)">调整权限</el-button>
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
          <el-checkbox-group v-model="createForm.scopes" class="flex w-full flex-col gap-2" @change="normalizeScopeDependencies(createForm.scopes)">
            <el-checkbox v-for="scope in availableScopes" :key="scope" :value="scope" :disabled="scopeDisabled(scope, createForm.scopes)" border class="!ml-0 !h-auto !w-full !px-3 !py-2">
              <span class="font-medium text-slate-700">{{ scopeLabel(scope) }}</span>
              <el-tag class="ml-2" size="small" :type="scopeTagType(scope)">{{ scopeRiskLabel(scope) }}</el-tag>
              <span class="ml-2 text-xs text-slate-500">{{ scopeDescription(scope) }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="档口数据范围">
          <AgentKeyOutletScopeEditor
            v-model:scope-type="createForm.outletScopeType"
            v-model:outlet-ids="createForm.outletIds"
            v-model:default-outlet-id="createForm.defaultOutletId"
            :options="outletOptions"
            :loading="outletOptionsLoading"
          />
        </el-form-item>
        <el-alert
          v-if="restrictedScopeWithoutOutlet(createForm.scopes, createForm.outletScopeType)"
          class="mb-4"
          type="warning"
          :closable="false"
          show-icon
          title="档口范围与权限不匹配"
        >
          已选择订单/分析权限但档口范围为“不开放档口数据”，Agent 将无法读取或新建档口相关数据。
        </el-alert>
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

    <el-dialog v-model="rotateDialogVisible" title="调整 Agent 权限" width="560px" destroy-on-close>
      <el-alert type="warning" :closable="false" show-icon title="调整权限会重新签发 Key">
        旧 Key 会立即停用，新增权限不会静默授予已经流出的旧密钥。请把新 Key 重新保存到 Mac Key 管理器。
      </el-alert>
      <el-form ref="rotateFormRef" class="mt-4" :model="rotateForm" :rules="rotateRules" label-position="top">
        <el-form-item label="权限范围" prop="scopes">
          <el-checkbox-group v-model="rotateForm.scopes" class="flex w-full flex-col gap-2" @change="normalizeScopeDependencies(rotateForm.scopes)">
            <el-checkbox v-for="scope in availableScopes" :key="scope" :value="scope" :disabled="scopeDisabled(scope, rotateForm.scopes)" border class="!ml-0 !h-auto !w-full !px-3 !py-2">
              <span class="font-medium text-slate-700">{{ scopeLabel(scope) }}</span>
              <el-tag class="ml-2" size="small" :type="scopeTagType(scope)">{{ scopeRiskLabel(scope) }}</el-tag>
              <span class="ml-2 text-xs text-slate-500">{{ scopeDescription(scope) }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="档口数据范围">
          <AgentKeyOutletScopeEditor
            v-model:scope-type="rotateForm.outletScopeType"
            v-model:outlet-ids="rotateForm.outletIds"
            v-model:default-outlet-id="rotateForm.defaultOutletId"
            :options="outletOptions"
            :loading="outletOptionsLoading"
          />
        </el-form-item>
        <el-alert
          v-if="restrictedScopeWithoutOutlet(rotateForm.scopes, rotateForm.outletScopeType)"
          class="mb-4"
          type="warning"
          :closable="false"
          show-icon
          title="档口范围与权限不匹配"
        >
          已选择订单/分析权限但档口范围为“不开放档口数据”，Agent 将无法读取或新建档口相关数据。
        </el-alert>
        <el-form-item label="新 Key 有效期" prop="expiresInDays">
          <el-input-number v-model="rotateForm.expiresInDays" :min="1" :max="365" controls-position="right" />
          <span class="ml-2 text-sm text-slate-500">天</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rotateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="rotating" @click="submitRotate">重新签发并停用旧 Key</el-button>
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
          <el-input v-model="agentBaseUrl" placeholder="https://www.chenjianas.asia:33294" @change="saveBaseUrl" />
          <p class="mt-1 text-xs text-slate-400">仅用于生成下面的配置片段，保存在当前浏览器；不会写入 Key 或服务器。</p>
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-slate-700">Agent 环境配置</label>
          <el-input :model-value="environmentSnippet" readonly type="textarea" :rows="3" />
          <el-button class="mt-2" @click="copyText(environmentSnippet, '环境配置')">复制配置</el-button>
        </div>
        <div>
          <label class="mb-1 block text-sm font-medium text-slate-700">Agent 对接顺序</label>
          <p class="mb-2 text-xs leading-5 text-slate-500">
            先调用 <code>GET /api/agent/capabilities</code> 获取能力与档口范围，再调用
            <code>GET /api/agent/outlets</code> 获取可用档口。业务请求必须使用返回的
            <code>outletCode</code>，不要使用内部档口 ID。
          </p>
          <el-input :model-value="outletSnippet" readonly type="textarea" :rows="2" />
          <el-button class="mt-2" @click="copyText(outletSnippet, '对接命令')">复制命令</el-button>
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
  getAgentKeyOutletOptions,
  getAgentKeyScopes,
  rotateAgentKey,
  type AgentKeyCredential,
  type AgentKeyOutletScopeType,
  type AgentKeyView,
  type AgentOutletOption,
} from '@/api/agentKey'
import { formatDate } from '@/utils/format'
import AgentKeyOutletScopeEditor from './AgentKeyOutletScopeEditor.vue'

const DEFAULT_EXTERNAL_URL = 'https://www.chenjianas.asia:33294'
const BASE_URL_STORAGE_KEY = 'bladeAgentApiBaseUrl'
const keys = ref<AgentKeyView[]>([])
const availableScopes = ref<string[]>([])
const outletOptions = ref<AgentOutletOption[]>([])
const outletOptionsLoading = ref(false)
const loading = ref(false)
const creating = ref(false)
const rotating = ref(false)
const createDialogVisible = ref(false)
const rotateDialogVisible = ref(false)
const credentialDialogVisible = ref(false)
const credential = ref<AgentKeyCredential | null>(null)
const createFormRef = ref<FormInstance>()
const rotateFormRef = ref<FormInstance>()
const rotatingKey = ref<AgentKeyView | null>(null)
const agentBaseUrl = ref(localStorage.getItem(BASE_URL_STORAGE_KEY) || DEFAULT_EXTERNAL_URL)

const createForm = reactive({
  name: 'Mac 纸单录入 Agent',
  scopes: ['catalog:read', 'orders:write'] as string[],
  expiresInDays: 90,
  outletScopeType: 'NONE' as AgentKeyOutletScopeType,
  outletIds: [] as number[],
  defaultOutletId: null as number | null,
})

const createRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  scopes: [{ type: 'array', required: true, min: 1, message: '至少选择一个权限', trigger: 'change' }],
  expiresInDays: [{ required: true, message: '请输入有效期', trigger: 'change' }],
}

const rotateForm = reactive({
  scopes: [] as string[],
  expiresInDays: 90,
  outletScopeType: 'NONE' as AgentKeyOutletScopeType,
  outletIds: [] as number[],
  defaultOutletId: null as number | null,
})

const rotateRules: FormRules = {
  scopes: [{ type: 'array', required: true, min: 1, message: '至少选择一个权限', trigger: 'change' }],
  expiresInDays: [{ required: true, message: '请输入有效期', trigger: 'change' }],
}

const environmentSnippet = computed(() => {
  if (!credential.value) return ''
  const baseUrl = normalizeBaseUrl(agentBaseUrl.value)
  return `BLADE_AGENT_API_BASE_URL=${baseUrl}\nBLADE_AGENT_KEY=${credential.value.agentKey}`
})

const outletSnippet = computed(() => {
  const baseUrl = normalizeBaseUrl(agentBaseUrl.value)
  return `curl -H "X-Agent-Key: <KEY>" ${baseUrl}/api/agent/capabilities\ncurl -H "X-Agent-Key: <KEY>" ${baseUrl}/api/agent/outlets`
})

const OUTLET_DEPENDENT_SCOPES = ['orders:read', 'orders:write', 'analytics:read']

async function loadOutletOptions() {
  outletOptionsLoading.value = true
  try {
    const response = await getAgentKeyOutletOptions()
    outletOptions.value = response.data || []
  } catch {
    outletOptions.value = []
  } finally {
    outletOptionsLoading.value = false
  }
}

async function loadData() {
  loading.value = true
  try {
    const [keyResponse, scopeResponse] = await Promise.all([getAgentKeys(), getAgentKeyScopes(), loadOutletOptions()])
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
    outletScopeType: 'NONE' as AgentKeyOutletScopeType,
    outletIds: [],
    defaultOutletId: null,
  })
  createDialogVisible.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate()
  const outletError = validateOutletScope(createForm.outletScopeType, createForm.outletIds, createForm.defaultOutletId)
  if (outletError) {
    ElMessage.error(outletError)
    return
  }
  creating.value = true
  try {
    const response = await createAgentKey({
      name: createForm.name,
      scopes: [...createForm.scopes],
      expiresInDays: createForm.expiresInDays,
      outletScopeType: createForm.outletScopeType,
      outletIds: createForm.outletScopeType === 'ASSIGNED' ? [...createForm.outletIds] : [],
      defaultOutletId: createForm.outletScopeType === 'NONE' ? null : createForm.defaultOutletId,
    })
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

function openRotateDialog(row: AgentKeyView) {
  rotatingKey.value = row
  rotateForm.scopes = [...row.scopes]
  rotateForm.expiresInDays = 90
  rotateForm.outletScopeType = row.outletScopeType || 'NONE'
  rotateForm.outletIds = (row.outlets || []).map((outlet) => outlet.id)
  rotateForm.defaultOutletId = row.defaultOutletId ?? null
  rotateDialogVisible.value = true
}

async function submitRotate() {
  if (!rotateFormRef.value || !rotatingKey.value) return
  await rotateFormRef.value.validate()
  const outletError = validateOutletScope(rotateForm.outletScopeType, rotateForm.outletIds, rotateForm.defaultOutletId)
  if (outletError) {
    ElMessage.error(outletError)
    return
  }
  await ElMessageBox.confirm(
    `旧 Key「${rotatingKey.value.keyPrefix}」会立即失效，并签发一把具有所选权限的新 Key。`,
    '确认调整权限',
    { type: 'warning', confirmButtonText: '确认重新签发' },
  )
  rotating.value = true
  try {
    const response = await rotateAgentKey(rotatingKey.value.id, {
      scopes: [...rotateForm.scopes],
      expiresInDays: rotateForm.expiresInDays,
      outletScopeType: rotateForm.outletScopeType,
      outletIds: rotateForm.outletScopeType === 'ASSIGNED' ? [...rotateForm.outletIds] : [],
      defaultOutletId: rotateForm.outletScopeType === 'NONE' ? null : rotateForm.defaultOutletId,
    })
    credential.value = response.data
    rotateDialogVisible.value = false
    credentialDialogVisible.value = true
    await loadData()
  } catch (error: any) {
    ElMessage.error(error?.message || 'Agent Key 权限调整失败')
  } finally {
    rotating.value = false
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
    'products:read': '读取商品主档',
    'orders:read': '读取正式订单',
    'customers:read': '读取客户资料',
    'orders:write': '创建订单草稿',
    'products:create': '新增商品',
    'products:cost:write': '写入商品成本',
    'orders:cost:write': '写入草稿成本',
    'customers:create': '新增客户',
    'analytics:read': '读取经营分析',
    'outlets:read': '读取可用档口',
    'whatsapp:analyze': 'WhatsApp 分析任务',
  } as Record<string, string>)[scope] || scope
}

function scopeDescription(scope: string) {
  return ({
    'catalog:read': '按款号、颜色和尺码匹配 SKU',
    'products:read': '分页读取商品、颜色、尺码和 SKU；不含成本价',
    'orders:read': '分页读取订单和商品明细；不含电话、地址、成本和毛利',
    'customers:read': '读取客户名称、电话、地址和备注；属于敏感资料',
    'orders:write': '仅生成待人工确认的草稿',
    'products:create': '只新增商品，不修改同编码商品，也不写库存',
    'products:cost:write': '配合新增商品权限，统一成本自动应用到全部 SKU',
    'orders:cost:write': '配合草稿权限，允许写入商品成本快照和运费成本',
    'customers:create': '只新增客户；重复电话不覆盖，不允许修改或删除',
    'analytics:read': '读取已授权的聚合数据',
    'outlets:read': '读取当前 Agent 可访问的档口列表；业务请求请使用 outletCode',
    'whatsapp:analyze': '领取并回传分析结果',
  } as Record<string, string>)[scope] || ''
}

function scopeRiskLabel(scope: string) {
  if (scope === 'customers:read') return '敏感只读'
  if (scope === 'products:cost:write' || scope === 'orders:cost:write') return '敏感写入'
  if (scope === 'products:create' || scope === 'customers:create' || scope === 'orders:write' || scope === 'whatsapp:analyze') return '写入'
  return '只读'
}

function scopeTagType(scope: string): 'success' | 'warning' | 'danger' | 'info' {
  if (scope === 'customers:read' || scope === 'products:cost:write' || scope === 'orders:cost:write') return 'danger'
  if (scope === 'products:create' || scope === 'customers:create' || scope === 'orders:write' || scope === 'whatsapp:analyze') return 'warning'
  if (scope === 'products:read' || scope === 'orders:read' || scope === 'analytics:read') return 'success'
  if (scope === 'outlets:read') return 'info'
  return 'info'
}

function scopeDisabled(scope: string, selected: string[]) {
  if (scope === 'products:cost:write') return !selected.includes('products:create')
  if (scope === 'orders:cost:write') return !selected.includes('orders:write')
  return false
}

function normalizeScopeDependencies(selected: string[]) {
  if (!selected.includes('products:create')) {
    const index = selected.indexOf('products:cost:write')
    if (index >= 0) selected.splice(index, 1)
  }
  if (!selected.includes('orders:write')) {
    const index = selected.indexOf('orders:cost:write')
    if (index >= 0) selected.splice(index, 1)
  }
}

// 订单/分析类权限依赖档口范围；范围为 NONE 时只提示、不阻塞保存。
function restrictedScopeWithoutOutlet(scopes: string[], scopeType: AgentKeyOutletScopeType) {
  return scopeType === 'NONE' && scopes.some((scope) => OUTLET_DEPENDENT_SCOPES.includes(scope))
}

function validateOutletScope(
  scopeType: AgentKeyOutletScopeType,
  outletIds: number[],
  defaultOutletId: number | null,
): string | null {
  if (scopeType === 'NONE') return null
  if (scopeType === 'ALL') {
    if (defaultOutletId != null) {
      const target = outletOptions.value.find((option) => option.id === defaultOutletId)
      if (!target || target.status !== 1) return '默认档口必须是启用的档口'
    }
    return null
  }
  if (outletIds.length === 0) return '指定档口范围至少需要选择一个档口'
  if (defaultOutletId != null) {
    const target = outletOptions.value.find((option) => option.id === defaultOutletId)
    if (!target) return '默认档口不在可选档口中'
    if (!outletIds.includes(defaultOutletId)) return '默认档口必须属于已选档口'
    if (target.status !== 1) return '默认档口必须是启用的档口'
  }
  return null
}

function defaultOutletName(row: AgentKeyView) {
  const outlets = row.outlets || []
  const target = outlets.find((outlet) => outlet.isDefault)
    || outlets.find((outlet) => outlet.id === row.defaultOutletId)
  return target?.outletName || ''
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
