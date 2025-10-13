<template>
  <div class="app">
    <header class="topbar">
      <div class="left">
        <button class="icon-btn" aria-label="Menu" @click="toggleDrawer">☰</button>
        <router-link to="/" class="brand">Decks</router-link>
      </div>
      <div class="right">
        <button class="icon-btn" aria-label="Theme" @click="toggleTheme">{{ theme==='dark' ? '🌙' : '☀️' }}</button>
      </div>
    </header>
    <main class="content">
      <div class="pill" style="margin-bottom:8px">Local: decks {{ counts.decks }}, notes {{ counts.notes }}, cards {{ counts.cards }} · Last sync: {{ lastSyncText }}</div>
      <transition name="page" mode="out-in">
        <router-view />
      </transition>
    </main>
    <!-- Global sticky progress -->
    <div class="global-progress">
      <div class="gp-bar"><div class="gp-fill" :style="{ width: globalProgressPct + '%' }"></div></div>
      <div class="gp-text">Today {{ globalToday }}/{{ globalTarget }}</div>
    </div>

    <!-- Slide-over Drawer -->
    <div :class="['drawer-backdrop', drawerVisible ? 'show' : '']" @click="closeDrawer"></div>
    <aside
      :class="['drawer', drawerVisible ? 'open' : '']"
      role="dialog"
      aria-modal="true"
      aria-label="Function Menu"
      ref="drawerEl"
      @keydown="onDrawerKeydown"
      tabindex="-1"
    >
      <h3>Menu</h3>
      <div class="menu">
        <router-link to="/" @click="closeDrawer">Decks</router-link>
        <router-link to="/connect" @click="closeDrawer">Connect</router-link>
        <button class="btn" @click="handleSyncClick" :disabled="syncing">{{ syncing? 'Syncing...' : 'Sync Now' }}</button>
        <button class="btn" @click="handleFullRefreshClick" :disabled="syncing">Full Refresh</button>
        <button class="btn" @click="toggleTheme">Switch to {{ theme==='dark' ? 'Light' : 'Dark' }} Mode</button>
      </div>
    </aside>
    <div v-show="toastVisible" class="toast">{{ toastMsg }}</div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { syncNow } from './services/sync'
import { fullRefreshSyncState, getLocalCounts, getTodayReviewCount, getDailyTarget } from './services/db'

const syncing = ref(false)
const counts = ref({ decks:0, notes:0, cards:0 })
const lastSyncText = ref('never')
const toastVisible = ref(false)
const toastMsg = ref('')
let toastTimer = 0
const theme = ref('dark')
const drawerVisible = ref(false)
const globalToday = ref(0)
const globalTarget = ref(50)
const drawerEl = ref(null)
let lastFocus = null
const route = useRoute()

async function doSync(){
  syncing.value = true
  try {
    const r = await syncNow()
    console.log('sync result', r)
    alert(`Synced: decks ${r.decks}, notes ${r.notes}, cards ${r.cards}, pushed ${r.pushed}`)
    await refreshCounts()
    try { window.dispatchEvent(new CustomEvent('memorizer:sync-complete')) } catch(_){}
    showToast('Data updated')
  } catch (e) {
    alert('Sync failed: ' + (e?.message||e))
  } finally {
    syncing.value = false
  }
}

async function doFullRefresh(){
  syncing.value = true
  try {
    await fullRefreshSyncState()
    const r = await syncNow()
    console.log('full refresh + sync', r)
    alert(`Full refresh complete. Synced: decks ${r.decks}, notes ${r.notes}, cards ${r.cards}, pushed ${r.pushed}`)
    await refreshCounts()
    try { window.dispatchEvent(new CustomEvent('memorizer:sync-complete')) } catch(_){}
    showToast('Data updated')
  } catch (e) {
    alert('Full refresh failed: ' + (e?.message||e))
  } finally {
    syncing.value = false
  }
}

function fmtTs(ts){
  if (!ts || ts<=0) return 'never'
  try { return new Date(ts).toLocaleString() } catch { return String(ts) }
}

async function refreshCounts(){
  const c = await getLocalCounts()
  counts.value = { decks:c.decks||0, notes:c.notes||0, cards:c.cards||0 }
  lastSyncText.value = fmtTs(c.lastSyncTs)
  try { globalToday.value = await getTodayReviewCount(null) } catch(_){}
  try { globalTarget.value = await getDailyTarget() } catch(_){}
}

onMounted(refreshCounts)

function showToast(msg){
  toastMsg.value = msg
  toastVisible.value = true
  try { if (toastTimer) clearTimeout(toastTimer) } catch(_){}
  toastTimer = setTimeout(() => { toastVisible.value = false }, 2200)
}

function onSyncComplete(){ showToast('Data updated') }

onMounted(() => { try { window.addEventListener('memorizer:sync-complete', onSyncComplete) } catch(_){} })
onBeforeUnmount(() => { try { window.removeEventListener('memorizer:sync-complete', onSyncComplete); if (toastTimer) clearTimeout(toastTimer) } catch(_){} })

// Theme handling
function applyTheme(){
  try{
    const t = theme.value
    document.documentElement.setAttribute('data-theme', t)
    localStorage.setItem('theme', t)
  }catch(_){ }
}
function toggleTheme(){ theme.value = (theme.value === 'dark') ? 'light' : 'dark'; applyTheme() }
onMounted(() => {
  try {
    const saved = localStorage.getItem('theme')
    if (saved === 'light' || saved === 'dark') theme.value = saved
    else if (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches) theme.value = 'light'
  } catch(_){}
  applyTheme()
})

function toggleDrawer(){ drawerVisible.value = !drawerVisible.value }
function closeDrawer(){
  drawerVisible.value = false
  try { if (lastFocus) lastFocus.focus() } catch(_){ }
}
function handleSyncClick(){ doSync(); closeDrawer() }
function handleFullRefreshClick(){ doFullRefresh(); closeDrawer() }
function trapFocus(e){
  const el = drawerEl.value; if (!el) return
  const focusables = el.querySelectorAll('a, button, input, select, textarea, [tabindex]:not([tabindex="-1"])')
  if (!focusables.length) return
  const first = focusables[0], last = focusables[focusables.length-1]
  if (e.shiftKey && document.activeElement === first){ e.preventDefault(); last.focus() }
  else if (!e.shiftKey && document.activeElement === last){ e.preventDefault(); first.focus() }
}
function onDrawerKeydown(e){
  if (e.key === 'Escape'){ e.preventDefault(); closeDrawer(); return }
  if (e.key === 'Tab'){ trapFocus(e) }
}
onMounted(() => {
  // focus handling when opening
  watch(drawerVisible, (v) => {
    if (v){
      try { lastFocus = document.activeElement } catch(_){ lastFocus = null }
      setTimeout(() => { try { if (drawerEl.value) drawerEl.value.focus() } catch(_){ } }, 0)
    }
  })
})
// Auto-close drawer on route change
watch(() => route.fullPath, () => { if (drawerVisible.value) closeDrawer() })

// Cross-app toast helper event
onMounted(() => {
  try{
    window.addEventListener('memorizer:toast', (e) => {
      const msg = (e && e.detail) ? String(e.detail) : 'Done'
      showToast(msg)
    })
  }catch(_){ }
})

const globalProgressPct = computed(() => {
  const t = globalTarget.value||0, v = globalToday.value||0
  if (t<=0) return 0
  return Math.max(0, Math.min(100, Math.round((v/t)*100)))
})
</script>

<style>
:root{--bg:#1f2327;--fg:#f1f3f5;--muted:#bdbdbd;--accent:#3fb950;--panel:#2a2f34;--border:#121417}
[data-theme='light']{--bg:#f7f7f8;--fg:#222;--muted:#666;--accent:#2da44e;--panel:#ffffff;--border:#e6e6e6}
[data-theme='dark']{--bg:#1f2327;--fg:#f1f3f5;--muted:#bdbdbd;--accent:#3fb950;--panel:#2a2f34;--border:#121417}
/* Brand rating palette (customize here) */
:root{
  --rate-again-bg:#e74c3c; --rate-again-fg:#fff; --rate-again-border:#c0392b;
  --rate-hard-bg:#f39c12;  --rate-hard-fg:#1f2327; --rate-hard-border:#d68910;
  --rate-good-bg:#2ecc71;  --rate-good-fg:#fff; --rate-good-border:#27ae60;
  --rate-easy-bg:#3498db;  --rate-easy-fg:#fff; --rate-easy-border:#2e86c1;
}
body{margin:0;background:var(--bg);color:var(--fg);font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif}
.topbar{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid var(--border);gap:12px}
.left{display:flex;align-items:center;gap:8px}
.right{display:flex;align-items:center;gap:8px}
.brand{margin:0;font-size:18px;color:var(--fg);text-decoration:none;display:inline-block;padding:8px 12px;border-radius:8px}
.icon-btn{background:var(--panel);color:var(--fg);border:1px solid var(--border);border-radius:10px;padding:10px 14px}
.topbar a{color:var(--fg);text-decoration:none;margin-left:12px}
.content{padding:16px}
.btn{background:var(--panel);color:var(--fg);border:1px solid var(--border);border-radius:6px;padding:8px 12px}
.btn:active{transform:scale(.98)}
.deck{background:var(--panel);padding:12px;border-radius:8px;margin:8px 0;display:flex;justify-content:space-between;align-items:center;border:1px solid var(--border)}
.card-face{font-size:22px;margin:12px 0}
.controls{display:flex;gap:8px;margin-top:12px}
.pill{font-size:12px;color:var(--muted)}
.toast{position:fixed;bottom:16px;right:16px;background:var(--panel);color:var(--fg);padding:10px 14px;border-radius:8px;border:1px solid var(--border);box-shadow:0 2px 8px rgba(0,0,0,.35);z-index:9999}
/* Drawer */
.drawer-backdrop{position:fixed;inset:0;background:rgba(0,0,0,.0);opacity:0;transition:opacity .2s ease;pointer-events:none;z-index:1100}
.drawer-backdrop.show{opacity:1;background:rgba(0,0,0,.4);pointer-events:auto}
.drawer{position:fixed;left:0;top:0;bottom:0;width:260px;background:var(--bg);border-right:1px solid var(--border);padding:16px;transform:translateX(-100%);transition:transform .25s cubic-bezier(.2,.8,.2,1);z-index:1200;outline:none}
.drawer.open{transform:translateX(0)}
.drawer .menu{display:flex;flex-direction:column;gap:10px}
.drawer .menu a{color:var(--fg);text-decoration:none}

/* Global bottom progress */
.global-progress{position:sticky;bottom:0;left:0;right:0;background:var(--bg);border-top:1px solid var(--border);padding:8px 12px;display:flex;align-items:center;gap:10px;z-index:800}
.gp-bar{flex:1;height:8px;background:var(--panel);border:1px solid var(--border);border-radius:8px;overflow:hidden}
.gp-fill{height:8px;background:var(--accent);width:0}
.gp-text{font-size:12px;color:var(--muted)}

@media (max-width:560px){ .btn{padding:6px 10px} .brand{font-size:16px} }

/* Page transition */
.page-enter-active, .page-leave-active{ transition: opacity .18s ease, transform .18s ease }
.page-enter-from, .page-leave-to{ opacity: 0; transform: translateY(6px) }
</style>
