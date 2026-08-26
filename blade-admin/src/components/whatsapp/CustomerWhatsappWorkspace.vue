<template>
  <div v-loading="loading" class="customer-wa-workspace">
    <el-alert v-if="loadError" type="warning" :closable="false" show-icon class="mb-4">
      <template #title>暂时无法读取 WhatsApp 工作区</template>
      <p class="text-xs">{{ loadError }}</p>
    </el-alert>

    <div v-else-if="!links.length && !loading" class="wa-state-card">
      <div class="wa-state-icon"><span class="material-symbols-outlined">link_off</span></div>
      <h3>还没有匹配到这个客户的 WhatsApp</h3>
      <p>请确认客户国家区号和本地号码填写正确。系统只接受租户内唯一的国际号码精确匹配，不会根据姓名猜测。</p>
      <div class="mt-5 flex justify-center gap-2">
        <el-button :loading="refreshing" @click="refreshMatches">重新检查号码匹配</el-button>
        <el-button type="primary" @click="$router.push('/whatsapp')">打开 WhatsApp 归档</el-button>
      </div>
    </div>

    <div v-else-if="!confirmedLinks.length" class="wa-state-card wa-state-card--pending">
      <div class="wa-state-icon"><span class="material-symbols-outlined">person_search</span></div>
      <h3>找到 WhatsApp 联系人，等待你确认绑定</h3>
      <p>确认后，聊天记录才会归入这个 ERP 客户，并可与订单、商品一起用于 Agent 分析。确认不会发送消息或修改订单。</p>
      <div v-for="link in pendingLinks" :key="link.bindingId" class="wa-match-row">
        <div>
          <strong>{{ link.contactName || 'WhatsApp 联系人' }}</strong>
          <span>{{ maskPhone(link.phoneNormalized) }} · {{ link.accountName || 'Mac WhatsApp' }}</span>
        </div>
        <el-button type="success" :loading="bindingId === link.bindingId" @click="confirmBinding(link)">确认绑定</el-button>
      </div>
    </div>

    <template v-else-if="activeLink">
      <div class="wa-summary">
        <div class="wa-summary__identity">
          <span class="wa-summary__avatar">{{ avatarText(activeLink.contactName) }}</span>
          <div class="min-w-0">
            <div class="flex items-center gap-2">
              <h3 class="truncate text-base font-bold text-slate-900">{{ activeLink.contactName || customerName }}</h3>
              <el-tag type="success" size="small">已绑定</el-tag>
            </div>
            <p>{{ maskPhone(activeLink.phoneNormalized) }} · {{ activeLink.accountName || 'Mac WhatsApp Business' }}</p>
          </div>
          <el-select v-if="confirmedLinks.length > 1" v-model="activeBindingId" class="ml-auto w-56">
            <el-option v-for="link in confirmedLinks" :key="link.bindingId" :label="`${link.contactName || '联系人'} · ${maskPhone(link.phoneNormalized)}`" :value="link.bindingId" />
          </el-select>
        </div>

        <div class="wa-summary__metrics">
          <div><span>聊天记录</span><strong>{{ activeLink.messageCount.toLocaleString() }}</strong><small>条已归档消息</small></div>
          <div><span>最近沟通</span><strong class="text-base">{{ shortDate(activeLink.lastMessageAt) }}</strong><small>{{ formatTime(activeLink.lastMessageAt) }}</small></div>
          <button class="wa-issue-metric" :class="{ 'has-issues': activeLink.openIssueCount > 0 }" @click="openIssues">
            <span>待恢复媒体</span><strong>{{ activeLink.openIssueCount }}</strong><small>图片 {{ activeLink.imageIssueCount }} · 视频 {{ activeLink.videoIssueCount }} · 音频 {{ activeLink.audioIssueCount }}</small>
          </button>
          <div><span>最近同步</span><strong class="text-base">{{ shortDate(activeLink.lastSyncTime) }}</strong><small>{{ formatTime(activeLink.lastSyncTime) }}</small></div>
        </div>
      </div>

      <el-alert v-if="scanJob" :closable="false" show-icon :type="scanAlertType" class="mb-4 rounded-xl">
        <template #title>客户扫描 #{{ scanJob.id }}：{{ scanLabel(scanJob.status) }}</template>
        <p class="text-xs">{{ scanJob.accountName || activeLink.accountName }} · {{ formatTime(scanJob.requestedAt) }}</p>
      </el-alert>

      <div class="customer-wa-chat">
        <WhatsappConversation ref="conversation" :chat="archiveChat">
          <template #actions>
            <el-button :loading="rescanning" @click="requestRescan">仅扫描此客户</el-button>
          </template>
        </WhatsappConversation>
      </div>
    </template>

    <el-drawer v-model="issuesVisible" size="760px" destroy-on-close>
      <template #header>
        <div>
          <h3 class="text-lg font-semibold text-slate-900">{{ customerName }} · 缺失媒体</h3>
          <p class="mt-1 text-xs text-slate-400">先在 WhatsApp 打开聊天让媒体加载，再回到客户详情点击“仅扫描此客户”</p>
        </div>
      </template>
      <el-table v-loading="issuesLoading" :data="issues" stripe empty-text="这个客户当前没有待恢复媒体">
        <el-table-column label="消息时间" width="170"><template #default="{ row }">{{ formatTime(row.messageTime) }}</template></el-table-column>
        <el-table-column label="媒体" width="90"><template #default="{ row }"><el-tag size="small">{{ mediaLabel(row.mediaType) }}</el-tag></template></el-table-column>
        <el-table-column label="缺失原因" min-width="220"><template #default="{ row }">{{ issueLabel(row.issueType) }}</template></el-table-column>
        <el-table-column label="最近检测" width="170"><template #default="{ row }">{{ formatTime(row.lastDetectedAt) }}</template></el-table-column>
      </el-table>
      <div class="flex justify-end py-4"><el-pagination v-model:current-page="issuePage" :page-size="20" layout="total, prev, pager, next" :total="issueTotal" @current-change="loadIssues" /></div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import WhatsappConversation from './WhatsappConversation.vue'
import {
  decideBinding, getCustomerWhatsappWorkspace, getIssues, getLatestWhatsappScan,
  refreshBindingCandidates, requestWhatsappScan,
  type ArchiveChat, type CollectionIssue, type CustomerWhatsappWorkspace, type ScanJob,
} from '@/api/whatsapp'

const props = defineProps<{ customerId: number; customerName: string }>()

const loading = ref(false)
const refreshing = ref(false)
const rescanning = ref(false)
const bindingId = ref<number>()
const links = ref<CustomerWhatsappWorkspace[]>([])
const activeBindingId = ref<number>()
const loadError = ref('')
const scanJob = ref<ScanJob | null>(null)
const conversation = ref<InstanceType<typeof WhatsappConversation> | null>(null)
const issuesVisible = ref(false)
const issuesLoading = ref(false)
const issues = ref<CollectionIssue[]>([])
const issuePage = ref(1)
const issueTotal = ref(0)
let pollTimer: number | undefined

const confirmedLinks = computed(() => links.value.filter(link => link.bindingStatus === 'CONFIRMED'))
const pendingLinks = computed(() => links.value.filter(link => link.bindingStatus === 'PENDING'))
const activeLink = computed(() => confirmedLinks.value.find(link => link.bindingId === activeBindingId.value) || confirmedLinks.value[0])
const archiveChat = computed<ArchiveChat>(() => ({
  accountId: activeLink.value!.accountId,
  identityKey: activeLink.value!.identityKey,
  displayName: activeLink.value!.contactName || props.customerName,
  phoneNormalized: activeLink.value!.phoneNormalized,
  messageCount: activeLink.value!.messageCount,
  lastMessageAt: activeLink.value!.lastMessageAt,
}))
const scanAlertType = computed(() => scanJob.value?.status === 'FAILED' ? 'error' : scanJob.value?.status === 'SUCCEEDED' ? 'success' : 'info')

async function loadWorkspace() {
  loading.value = true
  loadError.value = ''
  try {
    const response = await getCustomerWhatsappWorkspace(props.customerId)
    links.value = response.data || []
    const confirmed = links.value.find(link => link.bindingStatus === 'CONFIRMED')
    if (confirmed && !confirmedLinks.value.some(link => link.bindingId === activeBindingId.value)) activeBindingId.value = confirmed.bindingId
  } catch (error: any) {
    loadError.value = error?.response?.data?.message || '请确认当前账号有 WhatsApp 归档查看权限后重试。'
  } finally {
    loading.value = false
  }
}

async function refreshMatches() {
  refreshing.value = true
  try {
    await refreshBindingCandidates()
    await loadWorkspace()
    ElMessage.success(links.value.length ? '号码匹配已更新' : '仍未找到唯一精确匹配，请检查国家区号和号码')
  } finally {
    refreshing.value = false
  }
}

async function confirmBinding(link: CustomerWhatsappWorkspace) {
  await ElMessageBox.confirm(`确认将 WhatsApp 联系人“${link.contactName || maskPhone(link.phoneNormalized)}”绑定到 ${props.customerName}？`, '确认客户绑定', {
    confirmButtonText: '确认绑定', cancelButtonText: '取消', type: 'success',
  })
  bindingId.value = link.bindingId
  try {
    await decideBinding(link.bindingId, 'CONFIRMED')
    ElMessage.success('绑定成功，聊天记录已归入该客户')
    await loadWorkspace()
  } finally {
    bindingId.value = undefined
  }
}

async function requestRescan() {
  const link = activeLink.value
  if (!link) return
  if (!link.phoneNormalized && !link.conversationJid) return ElMessage.warning('该联系人缺少可用于定向扫描的真实号码')
  rescanning.value = true
  try {
    scanJob.value = (await requestWhatsappScan(link.accountId, {
      phoneNormalized: link.phoneNormalized,
      conversationJid: link.phoneNormalized ? undefined : link.conversationJid,
    })).data
    ElMessage.success('已提交仅扫描此客户的任务，Mac 采集器会自动领取')
    startPolling()
  } finally {
    rescanning.value = false
  }
}

function startPolling() {
  if (pollTimer) window.clearInterval(pollTimer)
  pollTimer = window.setInterval(async () => {
    const latest = (await getLatestWhatsappScan()).data
    if (!latest || latest.id !== scanJob.value?.id) return
    scanJob.value = latest
    if (!['PENDING', 'CLAIMED'].includes(latest.status)) {
      if (pollTimer) window.clearInterval(pollTimer)
      await loadWorkspace()
      await conversation.value?.reload()
      if (issuesVisible.value) await loadIssues()
    }
  }, 5000)
}

async function openIssues() {
  if (!activeLink.value) return
  issuePage.value = 1
  issuesVisible.value = true
  await loadIssues()
}

async function loadIssues() {
  const link = activeLink.value
  if (!link) return
  issuesLoading.value = true
  try {
    const response = await getIssues({
      page: issuePage.value, size: 20, status: 'OPEN', accountId: link.accountId,
      phoneNormalized: link.phoneNormalized || undefined,
      conversationJid: link.phoneNormalized ? undefined : link.conversationJid || undefined,
    })
    issues.value = response.data.records
    issueTotal.value = response.data.total
  } finally {
    issuesLoading.value = false
  }
}

const avatarText = (value?: string) => (value || props.customerName || '?').trim().slice(0, 1).toUpperCase()
const maskPhone = (value?: string) => !value ? '—' : value.length < 8 ? value : `${value.slice(0, 3)}****${value.slice(-4)}`
const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '暂无'
const shortDate = (value?: string) => value ? new Date(value).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }) : '暂无'
const scanLabel = (value: string) => ({ PENDING: '等待 Mac 采集器', CLAIMED: '正在扫描', SUCCEEDED: '扫描完成', FAILED: '扫描失败' }[value] || value)
const mediaLabel = (value?: string) => ({ IMAGE: '图片', VIDEO: '视频', AUDIO: '音频', VOICE: '语音', DOCUMENT: '文档', STICKER: '贴纸' }[value || ''] || value || '媒体')
const issueLabel = (value: string) => ({ MEDIA_PATH_EMPTY: 'Mac 尚未获得媒体路径', THUMBNAIL_ONLY: '只有缩略图，原文件未同步', MEDIA_FILE_MISSING: 'Mac 本地文件不存在', MEDIA_SIZE_MISMATCH: '媒体文件大小异常', MEDIA_READ_FAILED: '媒体文件读取失败', MEDIA_ITEM_MISSING: '媒体元数据缺失' }[value] || value)

onMounted(async () => {
  await refreshBindingCandidates().catch(() => undefined)
  await loadWorkspace()
})
onBeforeUnmount(() => { if (pollTimer) window.clearInterval(pollTimer) })
</script>

<style scoped>
.customer-wa-workspace{min-height:240px}.wa-state-card{display:flex;min-height:360px;flex-direction:column;align-items:center;justify-content:center;border:1px solid #e2e8f0;border-radius:18px;background:#fff;padding:48px;text-align:center}.wa-state-card--pending{background:linear-gradient(180deg,#fff,#f6fbf8)}.wa-state-icon{display:grid;width:58px;height:58px;place-items:center;border-radius:18px;background:#e8f7f1;color:#168b6b}.wa-state-icon .material-symbols-outlined{font-size:30px}.wa-state-card h3{margin-top:18px;font-size:18px;font-weight:700;color:#17212b}.wa-state-card>p{margin-top:8px;max-width:620px;font-size:13px;line-height:1.7;color:#64748b}.wa-match-row{display:flex;width:min(620px,100%);align-items:center;justify-content:space-between;gap:20px;margin-top:18px;padding:14px 16px;border:1px solid #d9e8e1;border-radius:14px;background:#fff;text-align:left}.wa-match-row strong,.wa-match-row span{display:block}.wa-match-row strong{font-size:14px;color:#1e293b}.wa-match-row span{margin-top:3px;font-size:12px;color:#94a3b8}.wa-summary{margin-bottom:16px;overflow:hidden;border:1px solid #dfe8e4;border-radius:18px;background:#fff}.wa-summary__identity{display:flex;align-items:center;gap:12px;padding:16px 18px;border-bottom:1px solid #edf2ef;background:linear-gradient(110deg,#f3fbf7,#fff)}.wa-summary__identity p{margin-top:3px;font-size:12px;color:#789086}.wa-summary__avatar{display:grid;width:44px;height:44px;flex:0 0 44px;place-items:center;border-radius:50%;background:#cceede;color:#117458;font-weight:800}.wa-summary__metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr))}.wa-summary__metrics>div,.wa-summary__metrics>button{min-width:0;padding:15px 18px;border-right:1px solid #edf2ef;text-align:left}.wa-summary__metrics>:last-child{border-right:0}.wa-summary__metrics span,.wa-summary__metrics strong,.wa-summary__metrics small{display:block}.wa-summary__metrics span{font-size:11px;color:#84948c}.wa-summary__metrics strong{margin-top:5px;font-size:23px;color:#1d2c26}.wa-summary__metrics small{margin-top:2px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:10px;color:#9aa8a1}.wa-issue-metric{transition:background .15s}.wa-issue-metric:hover{background:#f7faf9}.wa-issue-metric.has-issues strong{color:#e45b4b}.customer-wa-chat{height:680px}.customer-wa-chat :deep(.wa-conversation){height:100%}@media(max-width:900px){.wa-summary__metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.wa-summary__metrics>:nth-child(2){border-right:0}.wa-summary__metrics>:nth-child(-n+2){border-bottom:1px solid #edf2ef}}
</style>
