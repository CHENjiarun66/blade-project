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
        <el-button type="primary" :loading="rescanning" :disabled="!selectedAccount" @click="requestRescan">重新扫描</el-button>
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
      <template #title>重扫任务 #{{ latestJob.id }}：{{ jobLabel(latestJob.status) }}</template>
      <div class="text-xs">{{ latestJob.accountName || `账号 ${latestJob.accountId}` }} · {{ formatTime(latestJob.requestedAt) }}<span v-if="latestJob.errorSummary"> · {{ latestJob.errorSummary }}</span></div>
    </el-alert>

    <el-tabs v-model="activeTab" class="rounded-2xl border border-slate-200 bg-white px-5 pt-2">
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
          <span class="ml-auto text-xs text-slate-400">打开聊天并让媒体加载后，点击“重新扫描”</span>
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

      <el-tab-pane :label="`客户绑定 (${bindings.length})`" name="bindings">
        <el-table :data="bindings" empty-text="暂无待确认的唯一手机号匹配">
          <el-table-column prop="contactName" label="WhatsApp 联系人" min-width="180" />
          <el-table-column label="号码" min-width="150"><template #default="{row}">{{ maskPhone(row.phoneNormalized) }}</template></el-table-column>
          <el-table-column prop="customerName" label="ERP 客户" min-width="180" />
          <el-table-column prop="matchMethod" label="匹配方式" width="150" />
          <el-table-column label="操作" width="180"><template #default="{row}"><el-button link type="success" @click="handleBinding(row.id,'CONFIRMED')">确认</el-button><el-button link type="danger" @click="handleBinding(row.id,'REJECTED')">拒绝</el-button></template></el-table-column>
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
        <el-alert title="请先打开这个客户的聊天，让图片、视频或语音完成加载；然后回到这里点击“重新扫描”。" type="info" :closable="false" show-icon class="mb-4" />
        <div class="mb-4 flex items-center gap-2 rounded-xl bg-slate-50 p-3">
          <el-tag v-if="selectedIssueChat.imageCount" type="info">图片 {{ selectedIssueChat.imageCount }}</el-tag>
          <el-tag v-if="selectedIssueChat.videoCount">视频 {{ selectedIssueChat.videoCount }}</el-tag>
          <el-tag v-if="selectedIssueChat.audioCount" type="warning">音频 {{ selectedIssueChat.audioCount }}</el-tag>
          <div class="ml-auto flex gap-2"><el-button type="primary" :disabled="!chatPhone(selectedIssueChat)" @click="openChat(selectedIssueChat)">打开这个聊天</el-button><el-button :loading="rescanning" @click="requestRescan">重新扫描</el-button></div>
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
import { computed, defineComponent, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { createCollector, decideBinding, decideWhatsappRecommendation, getInsightEvidence, getIssueChats, getIssueSummary, getIssues, getLatestWhatsappScan, getPendingBindings, getWhatsappAccounts, getWhatsappInsights, requestWhatsappScan, type BindingCandidate, type CollectionIssue, type CollectorCredential, type InsightEvidence, type IssueChat, type IssueSummary, type ScanJob, type WhatsappAccount, type WhatsappInsight } from '@/api/whatsapp'

const MetricCard = defineComponent({ props:{label:String,value:Number,tone:String}, setup(props){return()=>h('div',{class:'rounded-2xl border border-slate-200 bg-white p-4'},[h('p',{class:'text-xs text-slate-500'},props.label),h('p',{class:`mt-2 text-3xl font-bold ${props.tone==='rose'?'text-rose-600':props.tone==='emerald'?'text-emerald-600':'text-amber-600'}`},String(props.value||0))])} })
const emptySummary: IssueSummary={open:0,resolved:0,missingPath:0,missingFile:0,image:0,video:0,audio:0}
const summary=ref<IssueSummary>({...emptySummary}),accounts=ref<WhatsappAccount[]>([]),issueChats=ref<IssueChat[]>([]),bindings=ref<BindingCandidate[]>([]),latestJob=ref<ScanJob|null>(null)
const detailIssues=ref<CollectionIssue[]>([]),selectedIssueChat=ref<IssueChat|null>(null),issueDetailDrawer=ref(false),detailLoading=ref(false),detailPage=ref(1),detailTotal=ref(0)
const insights=ref<WhatsappInsight[]>([]),evidence=ref<InsightEvidence[]>([]),insightStatus=ref('PENDING'),insightPage=ref(1),insightTotal=ref(0),evidenceDialog=ref(false)
const loading=ref(false),rescanning=ref(false),page=ref(1),pageSize=ref(20),total=ref(0),activeTab=ref('insights'),selectedAccount=ref<number>(),filters=reactive({status:'OPEN',mediaType:''})
const collectorDialog=ref(false),creatingCollector=ref(false),createdCredential=ref<CollectorCredential|null>(null),collectorForm=reactive({name:'这台 Mac',accountRef:'mac:primary',displayName:'',phoneNormalized:''})
let pollTimer:number|undefined
const jobAlertType=computed(()=>latestJob.value?.status==='FAILED'?'error':latestJob.value?.status==='SUCCEEDED'?'success':'info')

async function loadAll(){loading.value=true;try{const [s,a,j,b]=await Promise.all([getIssueSummary(),getWhatsappAccounts(),getLatestWhatsappScan(),getPendingBindings()]);summary.value=s.data||{...emptySummary};accounts.value=a.data||[];latestJob.value=j.data;bindings.value=b.data||[];if(!selectedAccount.value&&accounts.value.length)selectedAccount.value=accounts.value[0].id;await Promise.all([loadIssueChats(),loadInsights()]);if(issueDetailDrawer.value)await loadIssueDetails();startPollingIfNeeded()}finally{loading.value=false}}
async function loadIssueChats(){const r=await getIssueChats({page:page.value,size:pageSize.value,status:filters.status||undefined,mediaType:filters.mediaType||undefined,accountId:selectedAccount.value});issueChats.value=r.data.records;total.value=r.data.total}
async function loadIssueDetails(){if(!selectedIssueChat.value)return;detailLoading.value=true;try{const chat=selectedIssueChat.value;const r=await getIssues({page:detailPage.value,size:20,status:filters.status||undefined,mediaType:filters.mediaType||undefined,accountId:chat.accountId,phoneNormalized:chat.phoneNormalized||undefined,conversationJid:chat.phoneNormalized?undefined:chat.conversationJid||undefined,conversationId:chat.phoneNormalized||chat.conversationJid?undefined:chat.conversationId});detailIssues.value=r.data.records;detailTotal.value=r.data.total}finally{detailLoading.value=false}}
async function openIssueDetail(row:IssueChat){selectedIssueChat.value=row;detailPage.value=1;issueDetailDrawer.value=true;await loadIssueDetails()}
async function loadInsights(){const r=await getWhatsappInsights({page:insightPage.value,size:20,status:insightStatus.value||undefined});insights.value=r.data.records;insightTotal.value=r.data.total}
function resetInsights(){insightPage.value=1;void loadInsights()}
function resetAndLoad(){page.value=1;issueDetailDrawer.value=false;void loadIssueChats()}
async function requestRescan(){if(!selectedAccount.value)return;rescanning.value=true;try{latestJob.value=(await requestWhatsappScan(selectedAccount.value)).data;ElMessage.success('重扫任务已提交，Mac 采集器在线后会自动领取');startPollingIfNeeded()}finally{rescanning.value=false}}
function startPollingIfNeeded(){if(pollTimer)window.clearInterval(pollTimer);if(latestJob.value&&['PENDING','CLAIMED'].includes(latestJob.value.status))pollTimer=window.setInterval(async()=>{latestJob.value=(await getLatestWhatsappScan()).data;if(!latestJob.value||!['PENDING','CLAIMED'].includes(latestJob.value.status)){if(pollTimer)clearInterval(pollTimer);await loadAll()}},5000)}
function openChat(row:{phoneNormalized?:string;conversationJid?:string}){const digits=chatPhone(row);if(!digits)return ElMessage.warning('该聊天暂未找到真实手机号，无法安全打开');window.location.href=`whatsapp://send?phone=${digits}`}
async function handleBinding(id:number,status:'CONFIRMED'|'REJECTED'){await ElMessageBox.confirm(status==='CONFIRMED'?'确认将该 WhatsApp 联系人与 ERP 客户绑定？':'确认拒绝这条匹配候选？','确认操作');await decideBinding(id,status);ElMessage.success('处理成功');bindings.value=(await getPendingBindings()).data||[]}
async function handleRecommendation(row:WhatsappInsight,status:'ADOPTED'|'DISMISSED'|'COMPLETED'){await ElMessageBox.confirm(`确认${recommendationLabel(status)}这条建议？`,'确认操作');await decideWhatsappRecommendation(row.recommendationId,status);ElMessage.success('处理成功');await loadInsights()}
async function showEvidence(row:WhatsappInsight){evidence.value=(await getInsightEvidence(row.analysisId)).data||[];evidenceDialog.value=true}
async function handleCreateCollector(){if(!collectorForm.name||!collectorForm.accountRef)return ElMessage.warning('请填写名称和账号标识');creatingCollector.value=true;try{createdCredential.value=(await createCollector({...collectorForm,phoneNormalized:collectorForm.phoneNormalized||undefined})).data;await loadAll()}finally{creatingCollector.value=false}}
async function copyKey(){if(createdCredential.value){await navigator.clipboard.writeText(createdCredential.value.collectorKey);ElMessage.success('密钥已复制')}}
const formatTime=(v?:string)=>v?new Date(v).toLocaleString('zh-CN',{hour12:false}):'暂无'
const maskPhone=(v?:string)=>!v?'—':v.length<8?v:`${v.slice(0,3)}****${v.slice(-4)}`
const chatPhone=(row?:{phoneNormalized?:string;conversationJid?:string}|null)=>row?.phoneNormalized||((row?.conversationJid||'').endsWith('@s.whatsapp.net')?row?.conversationJid?.split('@')[0]:undefined)
const chatDisplayName=(row:IssueChat)=>row.customerName||row.conversationTitle||maskPhone(chatPhone(row))
const mediaLabel=(v?:string)=>({IMAGE:'图片',VIDEO:'视频',AUDIO:'音频',VOICE:'语音'}[v||'']||v||'未知')
const issueLabel=(v:string)=>({MEDIA_PATH_EMPTY:'Mac 尚未获得媒体路径',LOCAL_PATH_EMPTY:'Mac 尚未获得媒体路径',THUMBNAIL_ONLY:'只有缩略图，原文件未同步',MEDIA_FILE_MISSING:'Mac 本地文件不存在',LOCAL_FILE_MISSING:'Mac 本地文件不存在',MEDIA_SIZE_MISMATCH:'媒体文件大小异常',SIZE_MISMATCH:'媒体文件大小异常',MEDIA_READ_FAILED:'媒体文件读取失败',MEDIA_ITEM_MISSING:'媒体元数据缺失',UNSAFE_PATH:'媒体路径异常',COPY_CHANGED:'复制期间文件发生变化'}[v]||v)
const jobLabel=(v:string)=>({PENDING:'等待 Mac 采集器',CLAIMED:'正在扫描',SUCCEEDED:'扫描完成',FAILED:'扫描失败'}[v]||v)
const intentLabel=(v:string)=>({NEW:'新客户',INQUIRING:'正在询价',CONSIDERING:'考虑中',READY_TO_BUY:'准备下单',PURCHASED:'已购买',DORMANT:'沉默客户',UNKNOWN:'待判断'}[v]||v)
const riskLabel=(v:string)=>({LOW:'低风险',MEDIUM:'中风险',HIGH:'高风险',UNKNOWN:'待判断'}[v]||v)
const riskType=(v:string)=>v==='HIGH'?'danger':v==='MEDIUM'?'warning':'success'
const recommendationLabel=(v:string)=>({PENDING:'待处理',ADOPTED:'已采纳',DISMISSED:'已忽略',COMPLETED:'已完成'}[v]||v)
const recommendationType=(v:string)=>v==='COMPLETED'?'success':v==='DISMISSED'?'info':v==='ADOPTED'?'primary':'warning'
function preferenceTags(value:Record<string,unknown>){return Object.entries(value||{}).flatMap(([key,item])=>Array.isArray(item)?item.slice(0,3).map(v=>`${key}: ${String(v)}`):item==null?[]:[`${key}: ${String(item)}`]).slice(0,8)}
onMounted(loadAll);onBeforeUnmount(()=>{if(pollTimer)window.clearInterval(pollTimer)})
</script>
