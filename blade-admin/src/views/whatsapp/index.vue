<template>
  <div class="space-y-5">
    <div class="flex items-end justify-between">
      <div>
        <h2 class="text-2xl font-bold text-slate-900">WhatsApp 归档</h2>
        <p class="mt-1 text-sm text-slate-500">检查 Mac 已同步的聊天媒体。这里反映的是 Mac 本地完整性，不代表 iPhone 云端的全量状态。</p>
      </div>
      <div class="flex gap-2">
        <el-button @click="collectorDialog=true">连接采集器</el-button>
        <el-button :icon="Refresh" @click="loadAll">刷新</el-button>
        <el-button type="primary" :loading="rescanning" :disabled="!selectedAccount" @click="requestAccountRescan">扫描整个账号</el-button>
      </div>
    </div>

    <div class="grid grid-cols-5 gap-4">
      <MetricCard label="待恢复媒体" :value="summary.open" tone="rose" />
      <MetricCard label="路径尚未同步" :value="summary.missingPath" tone="amber" />
      <MetricCard label="文件缺失/异常" :value="summary.missingFile" tone="orange" />
      <MetricCard label="已恢复" :value="summary.resolved" tone="emerald" />
      <div class="rounded-2xl border border-slate-200 bg-white p-4">
        <p class="text-xs text-slate-500">最近采集</p>
        <p class="mt-2 text-sm font-semibold text-slate-800">{{ formatTime(summary.lastScanAt) }}</p>
        <el-tag class="mt-2" size="small" :type="summary.lastScanStatus === 'SUCCEEDED' ? 'success' : 'info'">{{ summary.lastScanStatus || '暂无' }}</el-tag>
      </div>
    </div>

    <el-alert v-if="latestJob" :closable="false" show-icon :type="jobAlertType" class="rounded-xl">
      <template #title>{{ latestJob.scopeType === 'CONTACT' ? '客户扫描' : '账号扫描' }}任务 #{{ latestJob.id }}：{{ jobLabel(latestJob.status) }}</template>
      <div class="text-xs">{{ latestJob.accountName || `账号 ${latestJob.accountId}` }}<span v-if="latestJob.scopeType === 'CONTACT'"> · {{ maskPhone(latestJob.targetPhoneNormalized) }}</span> · {{ formatTime(latestJob.requestedAt) }}<span v-if="latestJob.errorSummary"> · {{ latestJob.errorSummary }}</span></div>
    </el-alert>

    <el-tabs v-model="activeTab" class="rounded-2xl border border-slate-200 bg-white px-5 pt-2">
      <el-tab-pane label="聊天记录" name="archive">
        <div class="archive-shell">
          <aside class="archive-sidebar">
            <div class="archive-tools">
              <el-select v-model="selectedAccount" placeholder="选择账号" class="w-full" @change="resetArchive">
                <el-option v-for="account in accounts" :key="account.id" :label="account.displayName || account.accountRef" :value="account.id" />
              </el-select>
              <el-input v-model="archiveKeyword" clearable placeholder="搜索姓名或手机号" @keyup.enter="searchArchive" @clear="searchArchive">
                <template #suffix><button class="archive-search" aria-label="搜索聊天" @click="searchArchive">搜索</button></template>
              </el-input>
            </div>
            <div v-loading="archiveChatsLoading" class="archive-chat-list">
              <button v-for="chat in archiveChats" :key="`${chat.accountId}-${chat.identityKey}`" class="archive-chat-row" :class="{'is-active':selectedArchiveChat?.identityKey===chat.identityKey}" @click="selectArchiveChat(chat)">
                <span class="archive-avatar">{{ avatarText(chat.displayName) }}</span>
                <span class="min-w-0 flex-1 text-left">
                  <span class="flex items-center justify-between gap-2"><strong class="truncate text-sm text-slate-800">{{ chat.displayName || maskPhone(chat.phoneNormalized) }}</strong><time class="shrink-0 text-[11px] text-slate-400">{{ shortTime(chat.lastMessageAt) }}</time></span>
                  <span class="mt-1 flex items-center justify-between gap-2"><span class="truncate text-xs text-slate-500">{{ chatPreview(chat) }}</span><span class="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-500">{{ chat.messageCount }}</span></span>
                </span>
              </button>
              <el-empty v-if="!archiveChatsLoading&&!archiveChats.length" :image-size="72" description="没有找到聊天记录" />
            </div>
            <el-pagination v-model:current-page="archiveChatPage" size="small" layout="prev, pager, next" :page-size="30" :total="archiveChatTotal" @current-change="loadArchiveChats" />
          </aside>

          <section v-if="selectedArchiveChat" class="archive-conversation">
            <header class="archive-conversation-head">
              <span class="archive-avatar archive-avatar--small">{{ avatarText(selectedArchiveChat.displayName) }}</span>
              <div class="min-w-0"><h3 class="truncate font-semibold text-slate-900">{{ selectedArchiveChat.displayName }}</h3><p class="text-xs text-slate-500">{{ maskPhone(selectedArchiveChat.phoneNormalized) }} · 已归档 {{ selectedArchiveChat.messageCount }} 条消息</p></div>
              <el-button class="ml-auto" @click="openArchiveChat">在 WhatsApp 打开</el-button>
            </header>
            <div ref="archiveCanvas" v-loading="archiveMessagesLoading" class="archive-message-canvas" @scroll.passive="handleArchiveScroll">
              <div class="archive-page-note">{{ archiveLoadHint }}</div>
              <article v-for="message in archiveMessages" :key="message.id" class="message-line" :class="message.direction==='OUTBOUND'?'is-outbound':'is-inbound'">
                <div class="message-bubble">
                  <div v-for="media in message.media" :key="media.id || `${message.id}-${media.mediaType}`" class="message-media">
                    <el-image v-if="media.fileId&&['IMAGE','STICKER'].includes(media.mediaType)" class="message-image" :class="{'is-sticker':media.mediaType==='STICKER'}" :src="filePreviewUrl(media.fileId)" :preview-src-list="[filePreviewUrl(media.fileId)]" fit="cover" preview-teleported />
                    <video v-else-if="media.fileId&&media.mediaType==='VIDEO'" class="message-video" controls preload="metadata" :src="filePreviewUrl(media.fileId)" />
                    <audio v-else-if="media.fileId&&['AUDIO','VOICE'].includes(media.mediaType)" class="message-audio" controls preload="metadata" :src="filePreviewUrl(media.fileId)" />
                    <a v-else-if="media.fileId&&media.mediaType==='DOCUMENT'" class="message-document" :href="filePreviewUrl(media.fileId)" target="_blank" rel="noopener">📄 {{ media.originalName || '打开文档' }}</a>
                    <div v-else class="message-missing"><span class="message-missing-icon">!</span><span><strong>{{ mediaLabel(media.mediaType) }}尚未完整归档</strong><small>{{ archiveIssueLabel(media.issueType,media.downloadStatus) }}</small></span></div>
                    <p v-if="media.caption" class="mt-2 whitespace-pre-wrap text-sm">{{ media.caption }}</p>
                  </div>
                  <p v-if="message.textContent" class="message-text">{{ message.textContent }}</p>
                  <footer class="message-meta"><span v-if="message.starred">★</span><time>{{ messageTime(message.sentAt) }}</time><span v-if="message.direction==='OUTBOUND'" aria-label="我方发出">✓✓</span></footer>
                </div>
              </article>
              <el-empty v-if="!archiveMessagesLoading&&!archiveMessages.length" description="这个聊天暂无可显示的消息" />
            </div>
            <footer class="archive-conversation-foot">
              <span>🔒 只读归档，不会向客户发送任何内容</span>
              <span>已显示 {{ archiveMessages.length }} / {{ archiveMessageTotal }} 条</span>
            </footer>
          </section>
          <section v-else class="archive-empty"><div><span>💬</span><h3>选择一个客户查看聊天记录</h3><p>这里可以核对文字、图片、视频和语音是否归档准确。</p></div></section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="智能跟进" name="insights">
        <div class="mb-4 flex items-center gap-3">
          <el-select v-model="insightStatus" class="w-40" @change="resetInsights">
            <el-option label="待处理" value="PENDING" /><el-option label="已采纳" value="ADOPTED" />
            <el-option label="已完成" value="COMPLETED" /><el-option label="已忽略" value="DISMISSED" /><el-option label="全部" value="" />
          </el-select>
          <span class="ml-auto text-xs text-slate-400">Agent 只生成建议，不会自动发送 WhatsApp 消息</span>
        </div>
        <el-table :data="insights" empty-text="暂无 Agent 分析结果" stripe>
          <el-table-column prop="customerName" label="ERP 客户" min-width="150" />
          <el-table-column label="客户洞察" min-width="300">
            <template #default="{row}"><div class="font-medium text-slate-800">{{ row.summary }}</div><div class="mt-1 flex flex-wrap gap-1"><el-tag v-for="item in preferenceTags(row.preferences)" :key="item" size="small" type="info">{{ item }}</el-tag></div></template>
          </el-table-column>
          <el-table-column label="意向 / 风险" width="150"><template #default="{row}"><div>{{ intentLabel(row.intentStage) }}</div><el-tag class="mt-1" size="small" :type="riskType(row.churnRisk)">{{ riskLabel(row.churnRisk) }}</el-tag></template></el-table-column>
          <el-table-column label="建议跟进" min-width="250"><template #default="{row}"><div>{{ row.recommendedAction }}</div><div class="mt-1 text-xs text-slate-400">建议时间：{{ formatTime(row.dueAt) }} · 置信度 {{ Math.round(Number(row.confidence)*100) }}%</div></template></el-table-column>
          <el-table-column label="状态" width="100"><template #default="{row}"><el-tag size="small" :type="recommendationType(row.status)">{{ recommendationLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="220" fixed="right"><template #default="{row}"><el-button link @click="showEvidence(row)">查看依据</el-button><template v-if="['PENDING','ADOPTED'].includes(row.status)"><el-button link type="primary" @click="handleRecommendation(row,'ADOPTED')">采纳</el-button><el-button link type="success" @click="handleRecommendation(row,'COMPLETED')">完成</el-button><el-button link type="danger" @click="handleRecommendation(row,'DISMISSED')">忽略</el-button></template></template></el-table-column>
        </el-table>
        <div class="flex justify-end py-4"><el-pagination v-model:current-page="insightPage" :page-size="20" layout="total, prev, pager, next" :total="insightTotal" @current-change="loadInsights" /></div>
      </el-tab-pane>

      <el-tab-pane label="缺失媒体" name="issues">
        <div class="mb-4 flex items-center gap-3">
          <el-select v-model="selectedAccount" placeholder="选择账号" class="w-56" clearable @change="resetAndLoad">
            <el-option v-for="account in accounts" :key="account.id" :label="account.displayName || account.accountRef" :value="account.id" />
          </el-select>
          <el-select v-model="filters.status" class="w-36" @change="resetAndLoad">
            <el-option label="待恢复" value="OPEN" /><el-option label="已恢复" value="RESOLVED" /><el-option label="全部" value="" />
          </el-select>
          <el-select v-model="filters.mediaType" placeholder="媒体类型" clearable class="w-36" @change="resetAndLoad">
            <el-option label="图片" value="IMAGE" /><el-option label="视频" value="VIDEO" /><el-option label="语音/音频" value="AUDIO" />
          </el-select>
          <span class="ml-auto text-xs text-slate-400">打开聊天并让媒体加载后，在详情里点击“仅扫描此客户”</span>
        </div>
        <el-table v-loading="loading" :data="issueChats" stripe empty-text="当前筛选条件下没有缺失媒体">
          <el-table-column label="客户 / 聊天" min-width="230">
            <template #default="{ row }"><button class="text-left" @click="openIssueDetail(row)"><div class="font-medium text-slate-800 hover:text-blue-600">{{ chatDisplayName(row) }}</div><div class="text-xs text-slate-400"><span v-if="row.customerName && row.conversationTitle">{{ row.conversationTitle }} · </span>{{ maskPhone(chatPhone(row)) }}</div></button></template>
          </el-table-column>
          <el-table-column label="缺失媒体" min-width="240"><template #default="{row}"><div class="flex items-center gap-2"><strong class="text-rose-600">{{ row.issueCount }} 项</strong><el-tag v-if="row.imageCount" size="small" type="info">图片 {{ row.imageCount }}</el-tag><el-tag v-if="row.videoCount" size="small">视频 {{ row.videoCount }}</el-tag><el-tag v-if="row.audioCount" size="small" type="warning">音频 {{ row.audioCount }}</el-tag></div></template></el-table-column>
          <el-table-column label="最近消息" width="170"><template #default="{row}">{{ formatTime(row.latestMessageTime) }}</template></el-table-column>
          <el-table-column label="状态" width="110"><template #default="{row}"><el-tag :type="row.openCount>0?'danger':'success'" size="small">{{ row.openCount>0?`待恢复 ${row.openCount}`:'已恢复' }}</el-tag></template></el-table-column>
          <el-table-column label="最近检测" width="170"><template #default="{row}">{{ formatTime(row.lastDetectedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="110" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openIssueDetail(row)">查看详情</el-button></template></el-table-column>
        </el-table>
        <div class="flex justify-end py-4"><el-pagination v-model:current-page="page" v-model:page-size="pageSize" layout="total, prev, pager, next" :total="total" @current-change="loadIssueChats" /></div>
      </el-tab-pane>

      <el-tab-pane :label="`客户绑定（待确认 ${bindings.length} / 已绑定 ${confirmedBindings.length}）`" name="bindings">
        <el-alert class="mb-5" type="info" :closable="false" show-icon>
          <template #title>绑定后，这个 WhatsApp 聊天会归入对应的 ERP 客户</template>
          <div class="text-xs leading-5">有可分析的聊天记录时，Agent 才能把聊天内容与该客户的订单、商品一起分析，并在“智能跟进”里给出偏好和联系时机建议。绑定本身不会自动发消息、改订单或覆盖人工标签。</div>
        </el-alert>
        <h3 class="mb-3 font-semibold text-slate-800">待确认匹配</h3>
        <el-table :data="bindings" empty-text="暂无待确认的唯一手机号匹配">
          <el-table-column prop="contactName" label="WhatsApp 联系人" min-width="180" />
          <el-table-column label="号码" min-width="150"><template #default="{row}">{{ maskPhone(row.phoneNormalized) }}</template></el-table-column>
          <el-table-column prop="customerName" label="ERP 客户" min-width="180" />
          <el-table-column prop="matchMethod" label="匹配方式" width="150" />
          <el-table-column label="操作" width="180"><template #default="{row}"><el-button link type="success" @click="handleBinding(row.id,'CONFIRMED')">确认</el-button><el-button link type="danger" @click="handleBinding(row.id,'REJECTED')">拒绝</el-button></template></el-table-column>
        </el-table>
        <h3 class="mb-3 mt-8 font-semibold text-slate-800">已绑定客户</h3>
        <el-table :data="confirmedBindings" empty-text="暂无已绑定客户">
          <el-table-column prop="contactName" label="WhatsApp 联系人" min-width="180" />
          <el-table-column label="号码" min-width="150"><template #default="{row}">{{ maskPhone(row.phoneNormalized) }}</template></el-table-column>
          <el-table-column prop="customerName" label="ERP 客户" min-width="180" />
          <el-table-column label="绑定时间" width="180"><template #default="{row}">{{ formatTime(row.confirmedAt) }}</template></el-table-column>
          <el-table-column label="状态" width="110"><template #default><el-tag size="small" type="success">已绑定</el-tag></template></el-table-column>
          <el-table-column label="操作" width="190" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openCustomer(row)">客户档案</el-button><el-button link type="success" @click="viewBoundChat(row)">查看聊天</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="collectorDialog" title="连接 Mac 采集器" width="560px">
      <el-alert title="密钥只显示一次。创建后把它保存到 Mac 采集器配置中。" type="warning" :closable="false" class="mb-4" />
      <el-form label-width="110px">
        <el-form-item label="采集器名称"><el-input v-model="collectorForm.name" placeholder="例如：办公室 Mac" /></el-form-item>
        <el-form-item label="账号标识"><el-input v-model="collectorForm.accountRef" placeholder="例如：mac:office-main" /></el-form-item>
        <el-form-item label="显示名称"><el-input v-model="collectorForm.displayName" /></el-form-item>
        <el-form-item label="WhatsApp号码"><el-input v-model="collectorForm.phoneNormalized" placeholder="仅数字，含国家码" /></el-form-item>
      </el-form>
      <div v-if="createdCredential" class="rounded-xl bg-slate-950 p-4 text-white"><p class="mb-2 text-xs text-slate-400">X-Collector-Key（请立即复制）</p><div class="flex gap-2"><code class="min-w-0 flex-1 break-all text-xs">{{ createdCredential.collectorKey }}</code><el-button size="small" @click="copyKey">复制</el-button></div></div>
      <template #footer><el-button @click="collectorDialog=false">关闭</el-button><el-button type="primary" :loading="creatingCollector" @click="handleCreateCollector">创建凭证</el-button></template>
    </el-dialog>
    <el-dialog v-model="evidenceDialog" title="Agent 建议依据" width="680px">
      <el-timeline v-if="evidence.length"><el-timeline-item v-for="item in evidence" :key="item.messageId" :timestamp="formatTime(item.sentAt)" placement="top"><div class="rounded-xl bg-slate-50 p-3"><p class="mb-1 text-xs text-slate-400">{{ item.direction==='OUTBOUND'?'我方':'客户' }} · 消息 #{{ item.messageId }}</p><p class="text-sm text-slate-700">{{ item.excerpt }}</p></div></el-timeline-item></el-timeline>
      <el-empty v-else description="没有可显示的证据" />
    </el-dialog>
    <el-drawer v-model="issueDetailDrawer" size="820px" destroy-on-close>
      <template #header><div><h3 class="text-lg font-semibold text-slate-900">{{ selectedIssueChat ? chatDisplayName(selectedIssueChat) : '缺失媒体详情' }}</h3><p class="mt-1 text-xs text-slate-400">{{ maskPhone(chatPhone(selectedIssueChat)) }} · 共 {{ selectedIssueChat?.issueCount || 0 }} 项</p></div></template>
      <div v-if="selectedIssueChat" class="flex h-full flex-col">
        <el-alert title="请先打开这个客户的聊天，让图片、视频或语音完成加载；然后回到这里点击“仅扫描此客户”。" type="info" :closable="false" show-icon class="mb-4" />
        <div class="mb-4 flex items-center gap-2 rounded-xl bg-slate-50 p-3">
          <el-tag v-if="selectedIssueChat.imageCount" type="info">图片 {{ selectedIssueChat.imageCount }}</el-tag>
          <el-tag v-if="selectedIssueChat.videoCount">视频 {{ selectedIssueChat.videoCount }}</el-tag>
          <el-tag v-if="selectedIssueChat.audioCount" type="warning">音频 {{ selectedIssueChat.audioCount }}</el-tag>
          <div class="ml-auto flex gap-2"><el-button type="primary" :disabled="!chatPhone(selectedIssueChat)" @click="openChat(selectedIssueChat)">打开这个聊天</el-button><el-button :loading="rescanning" :disabled="!chatPhone(selectedIssueChat)" @click="requestContactRescan">仅扫描此客户</el-button></div>
        </div>
        <el-table v-loading="detailLoading" :data="detailIssues" stripe empty-text="没有缺失媒体明细">
          <el-table-column label="消息时间" width="170"><template #default="{row}">{{ formatTime(row.messageTime) }}</template></el-table-column>
          <el-table-column label="媒体" width="90"><template #default="{row}"><el-tag size="small">{{ mediaLabel(row.mediaType) }}</el-tag></template></el-table-column>
          <el-table-column label="缺失原因" min-width="210"><template #default="{row}">{{ issueLabel(row.issueType) }}</template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='OPEN'?'danger':'success'" size="small">{{ row.status==='OPEN'?'待恢复':'已恢复' }}</el-tag></template></el-table-column>
          <el-table-column label="最近检测" width="170"><template #default="{row}">{{ formatTime(row.lastDetectedAt) }}</template></el-table-column>
        </el-table>
        <div class="flex justify-end py-4"><el-pagination v-model:current-page="detailPage" :page-size="20" layout="total, prev, pager, next" :total="detailTotal" @current-change="loadIssueDetails" /></div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { createCollector, decideBinding, decideWhatsappRecommendation, getArchiveChats, getArchiveMessages, getConfirmedBindings, getInsightEvidence, getIssueChats, getIssueSummary, getIssues, getLatestWhatsappScan, getPendingBindings, getWhatsappAccounts, getWhatsappInsights, refreshBindingCandidates, requestWhatsappScan, type ArchiveChat, type ArchiveMessage, type BindingCandidate, type CollectionIssue, type CollectorCredential, type InsightEvidence, type IssueChat, type IssueSummary, type ScanJob, type WhatsappAccount, type WhatsappInsight } from '@/api/whatsapp'
import { filePreviewUrl } from '@/api/file'

const MetricCard = defineComponent({ props:{label:String,value:Number,tone:String}, setup(props){return()=>h('div',{class:'rounded-2xl border border-slate-200 bg-white p-4'},[h('p',{class:'text-xs text-slate-500'},props.label),h('p',{class:`mt-2 text-3xl font-bold ${props.tone==='rose'?'text-rose-600':props.tone==='emerald'?'text-emerald-600':'text-amber-600'}`},String(props.value||0))])} })
const router=useRouter()
const emptySummary: IssueSummary={open:0,resolved:0,missingPath:0,missingFile:0,image:0,video:0,audio:0}
const summary=ref<IssueSummary>({...emptySummary}),accounts=ref<WhatsappAccount[]>([]),issueChats=ref<IssueChat[]>([]),bindings=ref<BindingCandidate[]>([]),confirmedBindings=ref<BindingCandidate[]>([]),latestJob=ref<ScanJob|null>(null)
const detailIssues=ref<CollectionIssue[]>([]),selectedIssueChat=ref<IssueChat|null>(null),issueDetailDrawer=ref(false),detailLoading=ref(false),detailPage=ref(1),detailTotal=ref(0)
const insights=ref<WhatsappInsight[]>([]),evidence=ref<InsightEvidence[]>([]),insightStatus=ref('PENDING'),insightPage=ref(1),insightTotal=ref(0),evidenceDialog=ref(false)
const loading=ref(false),rescanning=ref(false),page=ref(1),pageSize=ref(20),total=ref(0),activeTab=ref('archive'),selectedAccount=ref<number>(),filters=reactive({status:'OPEN',mediaType:''})
const archiveChats=ref<ArchiveChat[]>([]),archiveChatPage=ref(1),archiveChatTotal=ref(0),archiveChatsLoading=ref(false),archiveKeyword=ref(''),selectedArchiveChat=ref<ArchiveChat|null>(null)
const archiveMessages=ref<ArchiveMessage[]>([]),archiveMessagePage=ref(1),archiveMessageTotal=ref(0),archiveMessagesLoading=ref(false),loadingOlderMessages=ref(false),archiveCanvas=ref<HTMLElement|null>(null)
const collectorDialog=ref(false),creatingCollector=ref(false),createdCredential=ref<CollectorCredential|null>(null),collectorForm=reactive({name:'这台 Mac',accountRef:'mac:primary',displayName:'',phoneNormalized:''})
let pollTimer:number|undefined
const jobAlertType=computed(()=>latestJob.value?.status==='FAILED'?'error':latestJob.value?.status==='SUCCEEDED'?'success':'info')
const hasOlderMessages=computed(()=>archiveMessages.value.length<archiveMessageTotal.value)
const archiveLoadHint=computed(()=>loadingOlderMessages.value?'正在加载更早消息…':hasOlderMessages.value?'向上滚动加载更早消息':'已经到达最早消息 · 只读归档')

async function loadAll(){loading.value=true;try{const [s,a,j,b,confirmed]=await Promise.all([getIssueSummary(),getWhatsappAccounts(),getLatestWhatsappScan(),refreshBindingCandidates(),getConfirmedBindings()]);summary.value=s.data||{...emptySummary};accounts.value=a.data||[];latestJob.value=j.data;bindings.value=b.data||[];confirmedBindings.value=confirmed.data||[];if(!selectedAccount.value&&accounts.value.length)selectedAccount.value=accounts.value[0].id;await Promise.all([loadIssueChats(),loadInsights(),loadArchiveChats()]);if(issueDetailDrawer.value)await loadIssueDetails();startPollingIfNeeded()}finally{loading.value=false}}
async function loadIssueChats(){const r=await getIssueChats({page:page.value,size:pageSize.value,status:filters.status||undefined,mediaType:filters.mediaType||undefined,accountId:selectedAccount.value});issueChats.value=r.data.records;total.value=r.data.total}
async function loadIssueDetails(){if(!selectedIssueChat.value)return;detailLoading.value=true;try{const chat=selectedIssueChat.value;const r=await getIssues({page:detailPage.value,size:20,status:filters.status||undefined,mediaType:filters.mediaType||undefined,accountId:chat.accountId,phoneNormalized:chat.phoneNormalized||undefined,conversationJid:chat.phoneNormalized?undefined:chat.conversationJid||undefined,conversationId:chat.phoneNormalized||chat.conversationJid?undefined:chat.conversationId});detailIssues.value=r.data.records;detailTotal.value=r.data.total}finally{detailLoading.value=false}}
async function openIssueDetail(row:IssueChat){selectedIssueChat.value=row;detailPage.value=1;issueDetailDrawer.value=true;await loadIssueDetails()}
async function loadInsights(){const r=await getWhatsappInsights({page:insightPage.value,size:20,status:insightStatus.value||undefined});insights.value=r.data.records;insightTotal.value=r.data.total}
async function loadArchiveChats(){if(!selectedAccount.value)return;archiveChatsLoading.value=true;try{const r=await getArchiveChats({page:archiveChatPage.value,size:30,accountId:selectedAccount.value,keyword:archiveKeyword.value||undefined});archiveChats.value=r.data.records;archiveChatTotal.value=r.data.total;if(!selectedArchiveChat.value&&archiveChats.value.length)await selectArchiveChat(archiveChats.value[0])}finally{archiveChatsLoading.value=false}}
async function selectArchiveChat(chat:ArchiveChat){selectedArchiveChat.value=chat;archiveMessagePage.value=1;archiveMessages.value=[];archiveMessageTotal.value=0;await loadArchiveMessages();await scrollArchiveToBottom()}
async function loadArchiveMessages(){if(!selectedArchiveChat.value)return;archiveMessagesLoading.value=true;const chat=selectedArchiveChat.value;try{const r=await getArchiveMessages({page:1,size:50,accountId:chat.accountId,identityKey:chat.identityKey});if(selectedArchiveChat.value?.identityKey!==chat.identityKey)return;archiveMessagePage.value=1;archiveMessages.value=r.data.records;archiveMessageTotal.value=r.data.total}finally{archiveMessagesLoading.value=false}}
async function loadOlderMessages(){const chat=selectedArchiveChat.value;if(!chat||loadingOlderMessages.value||!hasOlderMessages.value)return;const canvas=archiveCanvas.value;const previousHeight=canvas?.scrollHeight||0;const nextPage=archiveMessagePage.value+1;loadingOlderMessages.value=true;try{const r=await getArchiveMessages({page:nextPage,size:50,accountId:chat.accountId,identityKey:chat.identityKey});if(selectedArchiveChat.value?.identityKey!==chat.identityKey)return;const knownIds=new Set(archiveMessages.value.map(message=>message.id));archiveMessages.value=[...r.data.records.filter(message=>!knownIds.has(message.id)),...archiveMessages.value];archiveMessagePage.value=nextPage;archiveMessageTotal.value=r.data.total;await nextTick();if(canvas)canvas.scrollTop=canvas.scrollHeight-previousHeight}finally{loadingOlderMessages.value=false}}
function handleArchiveScroll(event:Event){const canvas=event.currentTarget as HTMLElement;if(canvas.scrollTop<80)void loadOlderMessages()}
async function scrollArchiveToBottom(){await nextTick();window.requestAnimationFrame(()=>{if(archiveCanvas.value)archiveCanvas.value.scrollTop=archiveCanvas.value.scrollHeight})}
function searchArchive(){archiveChatPage.value=1;selectedArchiveChat.value=null;archiveMessages.value=[];void loadArchiveChats()}
function resetArchive(){archiveChatPage.value=1;selectedArchiveChat.value=null;archiveMessages.value=[];void Promise.all([loadArchiveChats(),loadIssueChats()])}
function openArchiveChat(){if(selectedArchiveChat.value)openChat({phoneNormalized:selectedArchiveChat.value.phoneNormalized})}
function resetInsights(){insightPage.value=1;void loadInsights()}
function resetAndLoad(){page.value=1;issueDetailDrawer.value=false;void loadIssueChats()}
async function requestAccountRescan(){if(!selectedAccount.value)return;rescanning.value=true;try{latestJob.value=(await requestWhatsappScan(selectedAccount.value)).data;ElMessage.success('账号全盘扫描已提交，Mac 采集器会自动领取');startPollingIfNeeded()}finally{rescanning.value=false}}
async function requestContactRescan(){const chat=selectedIssueChat.value;const phone=chatPhone(chat);if(!chat||!phone)return ElMessage.warning('该聊天暂未找到真实手机号，无法定向扫描');rescanning.value=true;try{latestJob.value=(await requestWhatsappScan(chat.accountId,{phoneNormalized:phone,conversationJid:chat.conversationJid})).data;ElMessage.success('已提交仅扫描此客户的任务');startPollingIfNeeded()}finally{rescanning.value=false}}
function startPollingIfNeeded(){if(pollTimer)window.clearInterval(pollTimer);if(latestJob.value&&['PENDING','CLAIMED'].includes(latestJob.value.status))pollTimer=window.setInterval(async()=>{latestJob.value=(await getLatestWhatsappScan()).data;if(!latestJob.value||!['PENDING','CLAIMED'].includes(latestJob.value.status)){if(pollTimer)clearInterval(pollTimer);await loadAll()}},5000)}
function openChat(row:{phoneNormalized?:string;conversationJid?:string}){const digits=chatPhone(row);if(!digits)return ElMessage.warning('该聊天暂未找到真实手机号，无法安全打开');window.location.href=`whatsapp://send?phone=${digits}`}
async function handleBinding(id:number,status:'CONFIRMED'|'REJECTED'){await ElMessageBox.confirm(status==='CONFIRMED'?'确认将该 WhatsApp 联系人与 ERP 客户绑定？':'确认拒绝这条匹配候选？','确认操作');await decideBinding(id,status);const [pending,confirmed]=await Promise.all([getPendingBindings(),getConfirmedBindings()]);bindings.value=pending.data||[];confirmedBindings.value=confirmed.data||[];ElMessage.success(status==='CONFIRMED'?'绑定成功：聊天已归入 ERP 客户，有可分析记录时会生成 Agent 建议；系统不会自动发消息':'已拒绝该匹配')}
function openCustomer(row:BindingCandidate){void router.push(`/customers/${row.customerId}`)}
async function viewBoundChat(row:BindingCandidate){activeTab.value='archive';archiveKeyword.value=row.phoneNormalized||row.contactName||'';archiveChatPage.value=1;selectedArchiveChat.value=null;archiveMessages.value=[];await loadArchiveChats()}
async function handleRecommendation(row:WhatsappInsight,status:'ADOPTED'|'DISMISSED'|'COMPLETED'){await ElMessageBox.confirm(`确认${recommendationLabel(status)}这条建议？`,'确认操作');await decideWhatsappRecommendation(row.recommendationId,status);ElMessage.success('处理成功');await loadInsights()}
async function showEvidence(row:WhatsappInsight){evidence.value=(await getInsightEvidence(row.analysisId)).data||[];evidenceDialog.value=true}
async function handleCreateCollector(){if(!collectorForm.name||!collectorForm.accountRef)return ElMessage.warning('请填写名称和账号标识');creatingCollector.value=true;try{createdCredential.value=(await createCollector({...collectorForm,phoneNormalized:collectorForm.phoneNormalized||undefined})).data;await loadAll()}finally{creatingCollector.value=false}}
async function copyKey(){if(createdCredential.value){await navigator.clipboard.writeText(createdCredential.value.collectorKey);ElMessage.success('密钥已复制')}}
const formatTime=(v?:string)=>v?new Date(v).toLocaleString('zh-CN',{hour12:false}):'暂无'
const maskPhone=(v?:string)=>!v?'—':v.length<8?v:`${v.slice(0,3)}****${v.slice(-4)}`
const chatPhone=(row?:{phoneNormalized?:string;conversationJid?:string}|null)=>row?.phoneNormalized||((row?.conversationJid||'').endsWith('@s.whatsapp.net')?row?.conversationJid?.split('@')[0]:undefined)
const chatDisplayName=(row:IssueChat)=>row.customerName||row.conversationTitle||maskPhone(chatPhone(row))
const mediaLabel=(v?:string)=>({IMAGE:'图片',VIDEO:'视频',AUDIO:'音频',VOICE:'语音',DOCUMENT:'文档',STICKER:'贴纸',TEXT:'文字'}[v||'']||v||'未知')
const avatarText=(v?:string)=>(v||'?').trim().slice(0,1).toUpperCase()
const shortTime=(v?:string)=>v?new Date(v).toLocaleDateString('zh-CN',{month:'numeric',day:'numeric'}):''
const messageTime=(v?:string)=>v?new Date(v).toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit',hour12:false}):''
const chatPreview=(row:ArchiveChat)=>row.lastText||(row.lastMessageType?`[${mediaLabel(row.lastMessageType)}]`:'暂无内容')
const archiveIssueLabel=(issue?:string,status?:string)=>issue?issueLabel(issue):status==='IMPORTED'?'已归档':'Mac 端还没有可读取的原文件，请打开聊天加载后重新扫描'
const issueLabel=(v:string)=>({MEDIA_PATH_EMPTY:'Mac 尚未获得媒体路径',LOCAL_PATH_EMPTY:'Mac 尚未获得媒体路径',THUMBNAIL_ONLY:'只有缩略图，原文件未同步',MEDIA_FILE_MISSING:'Mac 本地文件不存在',LOCAL_FILE_MISSING:'Mac 本地文件不存在',MEDIA_SIZE_MISMATCH:'媒体文件大小异常',SIZE_MISMATCH:'媒体文件大小异常',MEDIA_READ_FAILED:'媒体文件读取失败',MEDIA_ITEM_MISSING:'媒体元数据缺失',UNSAFE_PATH:'媒体路径异常',COPY_CHANGED:'复制期间文件发生变化'}[v]||v)
const jobLabel=(v:string)=>({PENDING:'等待 Mac 采集器',CLAIMED:'正在扫描',SUCCEEDED:'扫描完成',FAILED:'扫描失败'}[v]||v)
const intentLabel=(v:string)=>({NEW:'新客户',INQUIRING:'正在询价',CONSIDERING:'考虑中',READY_TO_BUY:'准备下单',PURCHASED:'已购买',DORMANT:'沉默客户',UNKNOWN:'待判断'}[v]||v)
const riskLabel=(v:string)=>({LOW:'低风险',MEDIUM:'中风险',HIGH:'高风险',UNKNOWN:'待判断'}[v]||v)
const riskType=(v:string)=>v==='HIGH'?'danger':v==='MEDIUM'?'warning':'success'
const recommendationLabel=(v:string)=>({PENDING:'待处理',ADOPTED:'已采纳',DISMISSED:'已忽略',COMPLETED:'已完成'}[v]||v)
const recommendationType=(v:string)=>v==='COMPLETED'?'success':v==='DISMISSED'?'info':v==='ADOPTED'?'primary':'warning'
function preferenceTags(value:Record<string,unknown>){return Object.entries(value||{}).flatMap(([key,item])=>Array.isArray(item)?item.slice(0,3).map(v=>`${key}: ${String(v)}`):item==null?[]:[`${key}: ${String(item)}`]).slice(0,8)}
watch(activeTab,(tab)=>{if(tab==='archive'&&!archiveChats.value.length)void loadArchiveChats()})
onMounted(loadAll);onBeforeUnmount(()=>{if(pollTimer)window.clearInterval(pollTimer)})
</script>

<style scoped>
.archive-shell{height:690px;display:grid;grid-template-columns:340px minmax(0,1fr);margin-bottom:20px;overflow:hidden;border:1px solid #dfe5e7;border-radius:16px;background:#f7f9f8}.archive-sidebar{display:flex;min-width:0;flex-direction:column;border-right:1px solid #dfe5e7;background:#fff}.archive-tools{display:grid;gap:10px;padding:14px;border-bottom:1px solid #edf0f1}.archive-search{font-size:12px;color:#168b6b}.archive-chat-list{min-height:0;flex:1;overflow:auto}.archive-chat-row{display:flex;width:100%;gap:12px;padding:13px 14px;border-bottom:1px solid #f0f2f3;transition:background .15s}.archive-chat-row:hover{background:#f4faf7}.archive-chat-row.is-active{background:#e9f7f1}.archive-avatar{display:grid;width:44px;height:44px;flex:0 0 44px;place-items:center;border-radius:50%;background:linear-gradient(145deg,#d9f4e9,#bce7d8);font-weight:700;color:#14765d}.archive-avatar--small{width:38px;height:38px;flex-basis:38px}.archive-sidebar :deep(.el-pagination){justify-content:center;padding:10px;border-top:1px solid #edf0f1}.archive-conversation{display:flex;min-width:0;min-height:0;overflow:hidden;flex-direction:column}.archive-conversation-head{display:flex;height:68px;align-items:center;gap:11px;padding:0 18px;border-bottom:1px solid #dfe5e7;background:#f7faf9}.archive-message-canvas{min-height:0;flex:1;overflow:auto;padding:22px 7%;background-color:#efeae2;background-image:radial-gradient(rgba(70,104,91,.07) 1px,transparent 1px);background-size:18px 18px}.archive-page-note{width:max-content;margin:0 auto 18px;padding:5px 11px;border-radius:8px;background:rgba(255,255,255,.82);font-size:11px;color:#718078}.message-line{display:flex;margin:7px 0}.message-line.is-outbound{justify-content:flex-end}.message-bubble{position:relative;max-width:min(70%,620px);padding:8px 9px 5px;border-radius:9px;background:#fff;box-shadow:0 1px 1px rgba(28,47,39,.13)}.is-outbound .message-bubble{background:#d9fdd3}.message-text{white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px;line-height:1.55;color:#1e2c27}.message-meta{display:flex;justify-content:flex-end;gap:4px;margin-top:3px;font-size:10px;color:#718078}.message-media{min-width:230px}.message-image{width:min(360px,100%);height:240px;border-radius:7px;background:#d8dedb}.message-image.is-sticker{height:180px;background:transparent}.message-video{display:block;width:min(440px,100%);max-height:350px;border-radius:7px;background:#18201d}.message-audio{display:block;width:min(360px,100%)}.message-document{display:block;padding:14px;border-radius:8px;background:rgba(255,255,255,.5);font-size:13px;color:#176e58}.message-missing{display:flex;min-height:78px;align-items:center;gap:12px;padding:12px;border:1px dashed #daa892;border-radius:8px;background:rgba(255,247,241,.86);color:#8b4e36}.message-missing-icon{display:grid;width:30px;height:30px;flex:0 0 30px;place-items:center;border-radius:50%;background:#fff0e8;font-weight:800}.message-missing strong,.message-missing small{display:block}.message-missing small{margin-top:3px;font-size:11px;color:#9a6a56}.archive-conversation-foot{display:flex;min-height:54px;align-items:center;justify-content:space-between;gap:16px;padding:9px 16px;border-top:1px solid #dfe5e7;background:#f7faf9;font-size:12px;color:#64736c}.archive-empty{display:grid;place-items:center;text-align:center;color:#7d8c85}.archive-empty span{font-size:50px}.archive-empty h3{margin-top:10px;font-weight:600;color:#36463f}.archive-empty p{margin-top:5px;font-size:13px}@media(max-width:900px){.archive-shell{grid-template-columns:280px minmax(0,1fr)}.message-bubble{max-width:86%}}
.archive-shell{grid-template-rows:minmax(0,1fr)}
</style>
