import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { populateMockData, cleanupLegacyReviewLogsOnce, maybeClearMockOnPairingOnce, getPairingConfig, getServerId, setServerId, fullRefreshSyncState } from './services/db'
import { syncNow, startAutoSync } from './services/sync'
import { fetchServerInfo } from './services/api'

populateMockData()
  .catch(()=>{})
  .then(() => cleanupLegacyReviewLogsOnce().catch(()=>{}))
  .then(() => maybeClearMockOnPairingOnce().catch(()=>({ cleared:false })))
  .then(async (r) => { try{ if (r && r.cleared) await syncNow() } catch(_){} })
  .finally(() => { createApp(App).use(router).mount('#app'); try{ startAutoSync() }catch(_){ } })
  .then(async () => {
    // After mount, validate server fingerprint and auto-refresh if changed
    try{
      const { serverUrl } = await getPairingConfig()
      if (serverUrl){
        const info = await fetchServerInfo(serverUrl)
        const currentId = info && info.serverId
        const storedId = await getServerId()
        if (currentId && storedId && currentId !== storedId){
          await fullRefreshSyncState(); await syncNow()
        }
        if (currentId) await setServerId(currentId)
      }
    }catch(_){ }
  })
