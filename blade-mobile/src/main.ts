import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import vuetify from './plugins/vuetify'
import { registerSW } from 'virtual:pwa-register'

import './style.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(vuetify)

// Register PWA service worker
registerSW({
  onNeedRefresh() {
    if (confirm('有新版本可用，是否刷新？')) {
      location.reload()
    }
  },
  onOfflineReady() {
    console.log('应用已准备好离线使用')
  }
})

app.mount('#app')
