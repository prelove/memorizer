<template>
  <div :class="['study', miniMode ? 'mini' : '']">
    <div class="header">
      <div class="stats" v-show="!statsCollapsed">
        <div class="pill">Deck #{{ deckId }}</div>
        <div class="pill">Due {{ dueCount }}</div>
        <div class="pill">Today {{ today }}/{{ target }}</div>
      </div>
      <div class="progress"><div class="bar" :style="{ width: progressPct + '%' }"></div></div>
      <div class="actions">
        <button class="btn" @click="openEdit" :disabled="!noteId">Edit</button>
        <button class="btn" @click="snooze" :disabled="!currentCardId">Snooze</button>
        <button class="btn" @click="toggleStats">{{ statsCollapsed ? 'Show' : 'Hide' }} Stats</button>
        <button class="btn" @click="toggleMini">{{ miniMode ? 'Normal' : 'Mini' }}</button>
      </div>
    </div>

    <div class="card">
      <div class="face">{{ face }}</div>
      <div class="meta" v-if="(reading && showBack) || pos"><span class="pill" v-if="reading && showBack">{{ reading }}</span> <span class="pill" v-if="pos">{{ pos }}</span></div>
      <div class="examples" v-if="exampleLines.length">
        <div class="ex-line">{{ exampleLines[exampleIdx] }}</div>
        <div class="controls">
          <button class="btn" @click="prevExample" :disabled="exampleLines.length<=1">◀</button>
          <button class="btn" @click="nextExample" :disabled="exampleLines.length<=1">▶</button>
          <label class="pill" style="margin-left:8px"><input type="checkbox" v-model="autoRoll" /> Auto-roll</label>
        </div>
      </div>
      <div class="empty" v-if="!currentCardId && noDueMessage">
        <div class="empty-text">{{ noDueMessage }}</div>
        <div class="controls" style="justify-content:center">
          <button class="btn" @click="snooze">Snooze</button>
          <button class="btn" @click="showNextAnyway">Show next anyway</button>
        </div>
      </div>
    </div>

    <div class="controls rate">
      <button class="btn" @click="flip">Flip</button>
      <button class="btn rate-again" :disabled="submitting || !currentCardId" @click="rate(1)">Again</button>
      <button class="btn rate-hard" :disabled="submitting || !currentCardId" @click="rate(2)">Hard</button>
      <button class="btn rate-good" :disabled="submitting || !currentCardId" @click="rate(3)">Good</button>
      <button class="btn rate-easy" :disabled="submitting || !currentCardId" @click="rate(4)">Easy</button>
    </div>
    <div class="pill" v-if="submitting">Submitting...</div>

    <!-- Mobile bottom bar -->
    <div class="bottom-bar">
      <button class="btn big" @click="flip" aria-label="Flip">🔄 <span class="lbl">Flip</span></button>
      <button class="btn big rate-again" :disabled="submitting || !currentCardId" @click="rate(1)" aria-label="Again">1 <span class="lbl">Again</span></button>
      <button class="btn big rate-hard" :disabled="submitting || !currentCardId" @click="rate(2)" aria-label="Hard">2 <span class="lbl">Hard</span></button>
      <button class="btn big rate-good" :disabled="submitting || !currentCardId" @click="rate(3)" aria-label="Good">3 <span class="lbl">Good</span></button>
      <button class="btn big rate-easy" :disabled="submitting || !currentCardId" @click="rate(4)" aria-label="Easy">4 <span class="lbl">Easy</span></button>
    </div>

    <div v-if="showEditor" class="modal">
      <div class="dialog">
        <h3>Edit Card</h3>
        <div class="row"><label>Front</label><textarea v-model="editFront"></textarea></div>
        <div class="row"><label>Back</label><textarea v-model="editBack"></textarea></div>
        <div class="row"><label>Reading</label><input v-model="editReading" /></div>
        <div class="row"><label>POS</label><input v-model="editPos" /></div>
        <div class="row"><label>Examples</label><textarea v-model="editExamples" placeholder="One per line"></textarea></div>
        <div class="controls" style="justify-content:flex-end">
          <button class="btn" @click="closeEdit">Cancel</button>
          <button class="btn" @click="saveEdit">Save</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, computed } from 'vue'
import { useRoute } from 'vue-router'
import { nextDueCardByDeck, nextNCardsByDeck, addReviewLog, markReviewsSynced, updateLocalDueAfterRating, getDueCountByDeck, getTodayReviewCount, getDailyTarget, updateNote } from '../services/db'
import { getPairingConfig } from '../services/db'
import { postReviews, updateNotes } from '../services/api'
import { syncNow } from '../services/sync'

const route = useRoute()
const deckId = ref(route.params.id)
const front = ref('')
const back = ref('')
const showBack = ref(false)
const face = ref('')
const currentCardId = ref(null)
const submitting = ref(false)
const noteId = ref(null)
const reading = ref('')
const pos = ref('')
const dueCount = ref(0)
const today = ref(0)
const target = ref(50)
const autoRoll = ref(true)
const exampleLines = ref([])
const exampleIdx = ref(0)
let exampleTimer = 0
const showEditor = ref(false)
const editFront = ref('')
const editBack = ref('')
const editReading = ref('')
const editPos = ref('')
const editExamples = ref('')
const statsCollapsed = ref(false)
const miniMode = ref(false)
let nextCache = null

async function loadNext(){
  let c = null
  if (nextCache){ c = nextCache; nextCache = null }
  else {
    const arr = await nextNCardsByDeck(deckId.value, 2)
    c = arr[0] || null
    nextCache = arr[1] || null
  }
  if (!c){
    currentCardId.value=null; noteId.value=null; front.value=''; back.value=''; showBack.value=false; face.value=''; exampleLines.value=[]
    noDueMessage.value = 'No card due in this deck.'
    return
  }
  currentCardId.value = c.cardId
  noteId.value = c.noteId
  front.value = c.front
  back.value = c.back
  showBack.value = false
  face.value = front.value
  reading.value = c.reading || ''
  pos.value = c.pos || ''
  exampleLines.value = parseExamples(c.examples)
  exampleIdx.value = 0
  refreshMeta()
}

function flip(){
  showBack.value = !showBack.value
  face.value = showBack.value ? back.value : front.value
}

async function rate(r){
  if (!currentCardId.value){ await loadNext(); return }
  submitting.value = true
  try{
    const logId = await addReviewLog(currentCardId.value, r)
    const { serverUrl, token } = await getPairingConfig()
    if (serverUrl && token){
      const payload = [{ cardId: Number(currentCardId.value), rating: r, ts: Date.now(), latencyMs: null }]
      try{
        const resp = await postReviews(serverUrl, token, payload)
        if (resp && resp.ok){ await markReviewsSynced([logId]) }
      }catch(_){ /* ignore network errors; will sync later */ }
    }
    try{ await updateLocalDueAfterRating(currentCardId.value, r) } catch(_){ }
  } finally {
    submitting.value = false
    await loadNext()
  }
}

// Show any next card even if not due (fallback)
async function showNextAnyway(){ nextCache = null; const arr = await nextNCardsByDeck(deckId.value, 1); if (arr && arr[0]){ noDueMessage.value=''; setFromCard(arr[0]) } }
function setFromCard(c){ currentCardId.value=c.cardId; noteId.value=c.noteId; front.value=c.front; back.value=c.back; showBack.value=false; face.value=front.value; reading.value=c.reading||''; pos.value=c.pos||''; exampleLines.value=parseExamples(c.examples); exampleIdx.value=0; refreshMeta() }

onMounted(loadNext)
watch(()=>route.params.id, v => { deckId.value = v; loadNext() })

function parseExamples(ex){ if (!ex) return []; if (Array.isArray(ex)) return ex; return String(ex).split(/\n|\r\n|;\s*/).filter(Boolean).slice(0,5) }
function nextExample(){ if (exampleLines.value.length) exampleIdx.value = (exampleIdx.value+1) % exampleLines.value.length }
function prevExample(){ if (exampleLines.value.length) exampleIdx.value = (exampleIdx.value-1+exampleLines.value.length) % exampleLines.value.length }
async function refreshMeta(){ dueCount.value = await getDueCountByDeck(deckId.value); today.value = await getTodayReviewCount(deckId.value); target.value = await getDailyTarget() }
const progressPct = computed(() => { const t = target.value||0; const v = today.value||0; if (t<=0) return 0; return Math.max(0, Math.min(100, Math.round((v/t)*100))) })
function openEdit(){ if (!noteId.value) return; showEditor.value = true; editFront.value = front.value; editBack.value = back.value; editReading.value = reading.value || ''; editPos.value = pos.value || ''; editExamples.value = exampleLines.value.join('\n') }
function closeEdit(){ showEditor.value = false }
async function saveEdit(){
  try{
    const patch = { front: editFront.value, back: editBack.value, reading: editReading.value, pos: editPos.value, examples: editExamples.value }
    // Update local note first
    await updateNote(noteId.value, patch)
    // Attempt server update (LWW)
    try{
      const { serverUrl, token } = await getPairingConfig()
      if (serverUrl && token){
        const now = Date.now()
        await updateNotes(serverUrl, token, [{ id: Number(noteId.value), ...patch, updatedAt: now }])
      }
    }catch(_){ /* ignore; will reconcile on next sync */ }
    // Reflect changes in view
    front.value = editFront.value
    back.value = editBack.value
    reading.value = editReading.value
    pos.value = editPos.value
    exampleLines.value = parseExamples(editExamples.value)
    showEditor.value = false
  }catch(e){ alert('Save failed: '+ e.message) }
}
function snooze(){ if (!currentCardId.value) return; updateLocalDueAfterRating(currentCardId.value, 1).then(loadNext) }
watch(autoRoll, (nv) => { if (exampleTimer){ clearInterval(exampleTimer); exampleTimer=0 } if (nv && exampleLines.value.length){ exampleTimer = setInterval(nextExample, 2200) } })
onMounted(() => { if (autoRoll.value && exampleLines.value.length){ exampleTimer = setInterval(nextExample, 2200) } })
onBeforeUnmount(() => { try { if (exampleTimer) clearInterval(exampleTimer) } catch(_){} })

function toggleStats(){ statsCollapsed.value = !statsCollapsed.value; try { localStorage.setItem('statsCollapsed', statsCollapsed.value ? '1' : '0') } catch(_){} }
onMounted(() => { try { statsCollapsed.value = localStorage.getItem('statsCollapsed') === '1' } catch(_){} })
function toggleMini(){ miniMode.value = !miniMode.value; try { localStorage.setItem('miniMode', miniMode.value ? '1' : '0') } catch(_){} }
onMounted(() => { try { miniMode.value = localStorage.getItem('miniMode') === '1' } catch(_){} })

// Keyboard shortcuts: Space to flip; 1/2/3/4 to rate; S to snooze; E to edit
function handleKey(e){
  const k = e.key
  if (k === ' '){ e.preventDefault(); flip(); return }
  if (k === '1'){ e.preventDefault(); rate(1); return }
  if (k === '2'){ e.preventDefault(); rate(2); return }
  if (k === '3'){ e.preventDefault(); rate(3); return }
  if (k === '4'){ e.preventDefault(); rate(4); return }
  if (k === 's' || k === 'S'){ e.preventDefault(); snooze(); return }
  if (k === 'e' || k === 'E'){ e.preventDefault(); openEdit(); return }
}
onMounted(() => { try { window.addEventListener('keydown', handleKey) } catch(_){} })
onBeforeUnmount(() => { try { window.removeEventListener('keydown', handleKey) } catch(_){} })

// No-due inline message
const noDueMessage = ref('')
</script>

<style scoped>
.study{display:flex;flex-direction:column;gap:12px;min-height:70vh}
.header{display:flex;gap:8px;align-items:center;justify-content:space-between;flex-wrap:wrap}
.stats{display:flex;gap:8px;align-items:center;flex-wrap:wrap}
.actions{display:flex;gap:8px}
.progress{height:8px;background:var(--panel);border:1px solid var(--border);border-radius:8px;overflow:hidden}
.progress .bar{height:8px;background:var(--accent);width:0}
.card{background:var(--panel);border:1px solid var(--border);border-radius:8px;padding:12px;flex:1;display:flex;flex-direction:column;justify-content:center}
.face{font-size:32px;font-weight:600;min-height:68px;text-align:center}
.meta{display:flex;justify-content:center;gap:8px;margin-top:6px}
.examples{margin-top:8px;display:flex;align-items:center;justify-content:center;gap:8px;flex-wrap:wrap}
.ex-line{font-size:16px;color:var(--muted);text-align:center}
.empty{display:flex;flex-direction:column;align-items:center;gap:8px;margin-top:10px}
.empty-text{color:var(--muted)}
.controls.rate{display:flex;gap:8px;flex-wrap:wrap;justify-content:center}
.bottom-bar{display:none}
.bottom-bar{position:fixed;left:0;right:0;bottom:0;background:var(--panel);border-top:1px solid var(--border);padding:10px;display:flex;gap:12px;justify-content:center;z-index:900;box-shadow:0 -6px 18px rgba(0,0,0,.45)}
.bottom-bar .btn.big{padding:14px 18px;font-size:18px;border-radius:10px}
@media (max-width: 640px){ .controls.rate{display:none} .bottom-bar{display:flex} .study{padding-bottom:70px} }
@media (min-width: 480px){ .bottom-bar .lbl{display:inline-block} }
.bottom-bar .lbl{display:none;margin-left:6px}
/* Mini-screen mode: ultra-compact */
@media (max-width: 360px){ .header .stats{display:none} .examples{display:none} }

/* Rating color cues (brand palette variables) */
.rate-again{ background:var(--rate-again-bg); border-color:var(--rate-again-border); color:var(--rate-again-fg) }
.rate-hard{  background:var(--rate-hard-bg);  border-color:var(--rate-hard-border);  color:var(--rate-hard-fg) }
.rate-good{  background:var(--rate-good-bg);  border-color:var(--rate-good-border);  color:var(--rate-good-fg) }
.rate-easy{  background:var(--rate-easy-bg);  border-color:var(--rate-easy-border);  color:var(--rate-easy-fg) }

/* Mini mode reductions */
.study.mini .face{font-size:22px; min-height:48px}
.study.mini .examples{display:none}
.study.mini .card{padding:8px}
.study.mini .actions .btn{padding:6px 8px}
.study.mini .controls.rate .btn{padding:6px 8px}
.modal{position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;z-index:1000}
.dialog{background:var(--bg);color:var(--fg);border:1px solid var(--border);border-radius:8px;padding:12px;min-width:300px;max-width:520px;width:90%}
.row{display:flex;flex-direction:column;margin:8px 0}
.row label{font-size:12px;color:var(--muted);margin-bottom:4px}
.row textarea{min-height:60px}
@media (max-width: 640px){ .face{font-size:22px;min-height:56px} .actions .btn{padding:6px 10px} }
@media (max-width: 420px){ .face{font-size:18px;min-height:48px} }
</style>
