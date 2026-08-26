<template>
  <section class="wa-conversation">
    <header class="wa-conversation__head">
      <span class="wa-avatar">{{ avatarText(chat.displayName) }}</span>
      <div class="min-w-0">
        <h3 class="truncate font-semibold text-slate-900">{{ chat.displayName }}</h3>
        <p class="text-xs text-slate-500">{{ maskPhone(chat.phoneNormalized) }} · 已归档 {{ total }} 条消息</p>
      </div>
      <div class="ml-auto flex items-center gap-2">
        <slot name="actions" />
        <el-button :disabled="!chat.phoneNormalized" @click="openWhatsapp">在 WhatsApp 打开</el-button>
      </div>
    </header>

    <div ref="canvas" v-loading="loading" class="wa-conversation__canvas" @scroll.passive="handleScroll">
      <div class="wa-page-note">{{ loadHint }}</div>
      <article
        v-for="message in messages"
        :key="message.id"
        class="wa-message-line"
        :class="message.direction === 'OUTBOUND' ? 'is-outbound' : 'is-inbound'"
      >
        <div class="wa-message-bubble">
          <div v-for="media in message.media" :key="media.id || `${message.id}-${media.mediaType}`" class="wa-message-media">
            <el-image
              v-if="media.fileId && ['IMAGE', 'STICKER'].includes(media.mediaType)"
              class="wa-message-image"
              :class="{ 'is-sticker': media.mediaType === 'STICKER' }"
              :src="filePreviewUrl(media.fileId)"
              :preview-src-list="[filePreviewUrl(media.fileId)]"
              fit="cover"
              preview-teleported
            />
            <video v-else-if="media.fileId && media.mediaType === 'VIDEO'" class="wa-message-video" controls preload="metadata" :src="filePreviewUrl(media.fileId)" />
            <audio v-else-if="media.fileId && ['AUDIO', 'VOICE'].includes(media.mediaType)" class="wa-message-audio" controls preload="metadata" :src="filePreviewUrl(media.fileId)" />
            <a v-else-if="media.fileId && media.mediaType === 'DOCUMENT'" class="wa-message-document" :href="filePreviewUrl(media.fileId)" target="_blank" rel="noopener">
              <span class="material-symbols-outlined text-base">description</span>
              {{ media.originalName || '打开文档' }}
            </a>
            <div v-else class="wa-message-missing">
              <span class="wa-message-missing__icon">!</span>
              <span>
                <strong>{{ mediaLabel(media.mediaType) }}尚未完整归档</strong>
                <small>{{ issueLabel(media.issueType, media.downloadStatus) }}</small>
              </span>
            </div>
            <p v-if="media.caption" class="mt-2 whitespace-pre-wrap text-sm">{{ media.caption }}</p>
          </div>
          <p v-if="message.textContent" class="wa-message-text">{{ message.textContent }}</p>
          <footer class="wa-message-meta">
            <span v-if="message.starred">★</span>
            <time>{{ messageTime(message.sentAt) }}</time>
            <span v-if="message.direction === 'OUTBOUND'" aria-label="我方发出">✓✓</span>
          </footer>
        </div>
      </article>
      <el-empty v-if="!loading && !messages.length" description="这个聊天暂无可显示的消息" />
    </div>

    <footer class="wa-conversation__foot">
      <span><span class="material-symbols-outlined align-middle text-sm">lock</span> 只读归档，不会向客户发送任何内容</span>
      <span>已显示 {{ messages.length }} / {{ total }} 条</span>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getArchiveMessages, type ArchiveChat, type ArchiveMessage } from '@/api/whatsapp'
import { filePreviewUrl } from '@/api/file'

const props = defineProps<{ chat: ArchiveChat }>()

const messages = ref<ArchiveMessage[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const loadingOlder = ref(false)
const canvas = ref<HTMLElement | null>(null)

const hasOlder = computed(() => messages.value.length < total.value)
const loadHint = computed(() => loadingOlder.value ? '正在加载更早消息…' : hasOlder.value ? '向上滚动加载更早消息' : '已经到达最早消息 · 只读归档')

async function loadLatest() {
  const accountId = props.chat.accountId
  const identityKey = props.chat.identityKey
  loading.value = true
  messages.value = []
  page.value = 1
  total.value = 0
  try {
    const response = await getArchiveMessages({ page: 1, size: 50, accountId, identityKey })
    if (props.chat.accountId !== accountId || props.chat.identityKey !== identityKey) return
    messages.value = response.data.records
    total.value = response.data.total
    await scrollToBottom()
  } finally {
    loading.value = false
  }
}

async function loadOlderMessages() {
  if (loadingOlder.value || !hasOlder.value) return
  const accountId = props.chat.accountId
  const identityKey = props.chat.identityKey
  const previousHeight = canvas.value?.scrollHeight || 0
  const nextPage = page.value + 1
  loadingOlder.value = true
  try {
    const response = await getArchiveMessages({ page: nextPage, size: 50, accountId, identityKey })
    if (props.chat.accountId !== accountId || props.chat.identityKey !== identityKey) return
    const knownIds = new Set(messages.value.map(message => message.id))
    messages.value = [...response.data.records.filter(message => !knownIds.has(message.id)), ...messages.value]
    page.value = nextPage
    total.value = response.data.total
    await nextTick()
    if (canvas.value) canvas.value.scrollTop = canvas.value.scrollHeight - previousHeight
  } finally {
    loadingOlder.value = false
  }
}

function handleScroll(event: Event) {
  if ((event.currentTarget as HTMLElement).scrollTop < 80) void loadOlderMessages()
}

async function scrollToBottom() {
  await nextTick()
  window.requestAnimationFrame(() => {
    if (canvas.value) canvas.value.scrollTop = canvas.value.scrollHeight
  })
}

function openWhatsapp() {
  const phone = props.chat.phoneNormalized?.replace(/\D/g, '')
  if (!phone) return ElMessage.warning('该聊天暂未找到真实手机号，无法安全打开')
  window.location.href = `whatsapp://send?phone=${phone}`
}

const avatarText = (value?: string) => (value || '?').trim().slice(0, 1).toUpperCase()
const maskPhone = (value?: string) => !value ? '—' : value.length < 8 ? value : `${value.slice(0, 3)}****${value.slice(-4)}`
const messageTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }) : ''
const mediaLabel = (value?: string) => ({ IMAGE: '图片', VIDEO: '视频', AUDIO: '音频', VOICE: '语音', DOCUMENT: '文档', STICKER: '贴纸' }[value || ''] || value || '媒体')
const issueLabel = (issue?: string, status?: string) => ({
  MEDIA_PATH_EMPTY: 'Mac 尚未获得媒体路径，请打开聊天加载后重新扫描',
  LOCAL_PATH_EMPTY: 'Mac 尚未获得媒体路径，请打开聊天加载后重新扫描',
  THUMBNAIL_ONLY: '目前只有缩略图，原文件尚未同步',
  MEDIA_FILE_MISSING: 'Mac 本地文件不存在',
  LOCAL_FILE_MISSING: 'Mac 本地文件不存在',
  MEDIA_SIZE_MISMATCH: '媒体文件大小异常',
  SIZE_MISMATCH: '媒体文件大小异常',
  MEDIA_READ_FAILED: '媒体文件读取失败',
  MEDIA_ITEM_MISSING: '媒体元数据缺失',
}[issue || ''] || (status === 'IMPORTED' ? '已归档' : 'Mac 端还没有可读取的原文件'))

watch([() => props.chat.accountId, () => props.chat.identityKey], loadLatest, { immediate: true })
defineExpose({ reload: loadLatest })
</script>

<style scoped>
.wa-conversation{display:flex;min-width:0;min-height:0;height:100%;overflow:hidden;flex-direction:column;border:1px solid #dfe5e7;border-radius:16px;background:#fff}.wa-conversation__head{display:flex;min-height:68px;align-items:center;gap:11px;padding:10px 18px;border-bottom:1px solid #dfe5e7;background:#f7faf9}.wa-avatar{display:grid;width:40px;height:40px;flex:0 0 40px;place-items:center;border-radius:50%;background:linear-gradient(145deg,#d9f4e9,#bce7d8);font-weight:700;color:#14765d}.wa-conversation__canvas{min-height:0;flex:1;overflow:auto;padding:22px 7%;background-color:#efeae2;background-image:radial-gradient(rgba(70,104,91,.07) 1px,transparent 1px);background-size:18px 18px}.wa-page-note{width:max-content;margin:0 auto 18px;padding:5px 11px;border-radius:8px;background:rgba(255,255,255,.82);font-size:11px;color:#718078}.wa-message-line{display:flex;margin:7px 0}.wa-message-line.is-outbound{justify-content:flex-end}.wa-message-bubble{position:relative;max-width:min(70%,620px);padding:8px 9px 5px;border-radius:9px;background:#fff;box-shadow:0 1px 1px rgba(28,47,39,.13)}.is-outbound .wa-message-bubble{background:#d9fdd3}.wa-message-text{white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px;line-height:1.55;color:#1e2c27}.wa-message-meta{display:flex;justify-content:flex-end;gap:4px;margin-top:3px;font-size:10px;color:#718078}.wa-message-media{min-width:230px}.wa-message-image{width:min(360px,100%);height:240px;border-radius:7px;background:#d8dedb}.wa-message-image.is-sticker{height:180px;background:transparent}.wa-message-video{display:block;width:min(440px,100%);max-height:350px;border-radius:7px;background:#18201d}.wa-message-audio{display:block;width:min(360px,100%)}.wa-message-document{display:flex;align-items:center;gap:8px;padding:14px;border-radius:8px;background:rgba(255,255,255,.5);font-size:13px;color:#176e58}.wa-message-missing{display:flex;min-height:78px;align-items:center;gap:12px;padding:12px;border:1px dashed #daa892;border-radius:8px;background:rgba(255,247,241,.86);color:#8b4e36}.wa-message-missing__icon{display:grid;width:30px;height:30px;flex:0 0 30px;place-items:center;border-radius:50%;background:#fff0e8;font-weight:800}.wa-message-missing strong,.wa-message-missing small{display:block}.wa-message-missing small{margin-top:3px;font-size:11px;color:#9a6a56}.wa-conversation__foot{display:flex;min-height:52px;align-items:center;justify-content:space-between;gap:16px;padding:9px 16px;border-top:1px solid #dfe5e7;background:#f7faf9;font-size:12px;color:#64736c}@media(max-width:900px){.wa-message-bubble{max-width:86%}}
</style>
