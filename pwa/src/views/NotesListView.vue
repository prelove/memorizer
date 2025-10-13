<template>
  <div>
    <h2>Notes in Deck {{ deckId }}</h2>
    <div class="controls" style="margin:8px 0; display:flex; gap:8px; align-items:center; flex-wrap:wrap">
      <input v-model="query" placeholder="Filter by text..." style="width:100%;max-width:420px" />
      <select v-model="sortKey" class="btn" style="padding:8px">
        <option value="due">Sort: Due first</option>
        <option value="due_date">Sort: Due date</option>
        <option value="updated">Sort: Updated</option>
        <option value="front_asc">Sort: Front A→Z</option>
        <option value="front_desc">Sort: Front Z→A</option>
      </select>
      <div class="pill">Due {{ dueCount }}</div>
      <button class="btn" @click="openAdd">+ Add</button>
    </div>
    <div v-if="visible.length===0" class="pill">No notes found</div>
    <div v-for="n in visible" :key="n.id" class="deck">
      <div @click="openCard(n.cardId)" :style="{cursor: n.cardId? 'pointer':'default'}">
        <div>{{ n.front }}</div>
        <div class="pill">{{ n.reading || '' }} {{ n.pos || '' }}</div>
      </div>
      <div class="controls">
        <button class="btn" v-if="n.cardId" @click="openCard(n.cardId)">Open</button>
        <button class="btn" @click="confirmDelete(n.id)">Delete</button>
        <div class="pill">
          <span v-if="n.cardId">#{{ n.cardId }}</span>
          <span v-else>No card</span>
          <span v-if="n.dueAt && isDue(n.dueAt)" style="margin-left:8px;color:#ff9aa2">Due</span>
        </div>
      </div>
    </div>
    <!-- Add modal -->
    <div v-if="showAdd" class="modal">
      <div class="dialog">
        <h3>Add Card to Deck #{{ deckId }}</h3>
        <div class="row"><label>Front</label><textarea v-model="addFront" /></div>
        <div class="row"><label>Back</label><textarea v-model="addBack" /></div>
        <div class="row"><label>Reading</label><input v-model="addReading" /></div>
        <div class="row"><label>POS</label><input v-model="addPos" placeholder="noun/verb/adj..." /></div>
        <div class="row"><label>Examples</label><textarea v-model="addExamples" placeholder="One per line" /></div>
        <div class="controls" style="justify-content:flex-end">
          <button class="btn" @click="closeAdd">Cancel</button>
          <button class="btn" @click="saveAdd" :disabled="adding">{{ adding? 'Saving...' : 'Save' }}</button>
        </div>
      </div>
    </div>
  </div>
  <!-- Move modal -->
  <div v-if="showMove" class="modal">
    <div class="dialog">
      <h3>Move Note</h3>
      <div class="row">
        <label>Destination Deck</label>
        <select v-model="moveDeckId" class="btn" style="padding:8px">
          <option v-for="d in decks" :key="d.id" :value="d.id">{{ d.name }}</option>
        </select>
      </div>
      <div class="controls" style="justify-content:flex-end">
        <button class="btn" @click="closeMove">Cancel</button>
        <button class="btn" @click="saveMove" :disabled="moving">{{ moving? 'Moving...' : 'Move' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getNotesByDeck, upsertNotes, upsertCards, deleteNoteLocal, getDecks } from '../services/db'
import { getPairingConfig } from '../services/db'
import { createCard, deleteNote, updateNotes } from '../services/api'

const route = useRoute()
const router = useRouter()
const deckId = ref(route.params.id)
const notes = ref([])
const query = ref('')
const sortKey = ref('due')

const filtered = computed(() => {
  const q = (query.value||'').toLowerCase()
  if (!q) return notes.value
  return notes.value.filter(n =>
    (n.front&&n.front.toLowerCase().includes(q)) ||
    (n.back&&n.back.toLowerCase().includes(q)) ||
    (n.reading&&n.reading.toLowerCase().includes(q)) ||
    (n.pos&&n.pos.toLowerCase().includes(q)) ||
    (n.tags&&String(n.tags).toLowerCase().includes(q))
  )
})

const cmp = (a,b) => a<b ? -1 : a>b ? 1 : 0
const visible = computed(() => {
  const arr = filtered.value.slice()
  if (sortKey.value === 'front_asc'){
    arr.sort((a,b) => cmp((a.front||'').toLowerCase(), (b.front||'').toLowerCase()))
  } else if (sortKey.value === 'front_desc'){
    arr.sort((a,b) => -cmp((a.front||'').toLowerCase(), (b.front||'').toLowerCase()))
  } else if (sortKey.value === 'due_date'){
    arr.sort((a,b) => (a.dueAt||Infinity) - (b.dueAt||Infinity))
  } else if (sortKey.value === 'updated'){
    arr.sort((a,b) => (b.updatedAt||0) - (a.updatedAt||0))
  } else {
    const now = Date.now()
    arr.sort((a,b) => {
      const ad = a.dueAt ? (Number(a.dueAt) <= now ? 0 : 1) : 1
      const bd = b.dueAt ? (Number(b.dueAt) <= now ? 0 : 1) : 1
      if (ad !== bd) return ad - bd
      return cmp((a.front||'').toLowerCase(), (b.front||'').toLowerCase())
    })
  }
  return arr
})

const dueCount = computed(() => {
  const now = Date.now()
  return filtered.value.reduce((acc,n) => acc + (n.dueAt && Number(n.dueAt) <= now ? 1 : 0), 0)
})

async function reload(){ notes.value = await getNotesByDeck(deckId.value) }

onMounted(async () => {
  await reload()
  try { window.addEventListener('memorizer:sync-complete', reload) } catch(_){}
})

onBeforeUnmount(() => { try { window.removeEventListener('memorizer:sync-complete', reload) } catch(_){} })

function openCard(cardId){
  if (!cardId) return
  router.push(`/card/${cardId}`)
}

function isDue(ts){
  try { return ts && Number(ts) <= Date.now() } catch { return false }
}

// Add card modal
const showAdd = ref(false)
const addFront = ref('')
const addBack = ref('')
const addReading = ref('')
const addPos = ref('')
const addExamples = ref('')
const adding = ref(false)
function openAdd(){ showAdd.value = true }
function closeAdd(){ showAdd.value = false; addFront.value=''; addBack.value=''; addReading.value=''; addPos.value=''; addExamples.value='' }
async function saveAdd(){
  if (!addFront.value.trim() || !addBack.value.trim()){ alert('Front and Back are required'); return }
  adding.value = true
  try{
    const { serverUrl, token } = await getPairingConfig()
    if (!serverUrl || !token){ alert('Not paired or offline. Please Connect first.'); return }
    const payload = { deckId: Number(deckId.value), front: addFront.value.trim(), back: addBack.value.trim(), reading: addReading.value.trim()||null, pos: addPos.value.trim()||null, examples: addExamples.value||null }
    const res = await createCard(serverUrl, token, payload)
    if (res && res.note && res.card){
      await upsertNotes([res.note])
      await upsertCards([res.card])
      await reload()
      closeAdd()
    }
  }catch(e){ alert('Create failed: '+ (e?.message||e)) }
  finally { adding.value = false }
}

async function confirmDelete(id){
  if (!confirm('Delete this note and associated card(s)?')) return
  try{
    const { serverUrl, token } = await getPairingConfig()
    if (serverUrl && token){ await deleteNote(serverUrl, token, Number(id)) }
    await deleteNoteLocal(Number(id))
    await reload()
  } catch(e){ alert('Delete failed: '+ (e?.message||e)) }
}
</script>

<style scoped>
.dialog{background:var(--bg);color:var(--fg);border:1px solid var(--border);border-radius:8px;padding:12px;min-width:300px;max-width:520px;width:90%}
.row{display:flex;flex-direction:column;margin:8px 0}
.row label{font-size:12px;color:var(--muted);margin-bottom:4px}
.row textarea{min-height:60px}
.modal{position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;z-index:1000}
input, textarea{background:var(--panel);color:var(--fg);border:1px solid var(--border);border-radius:6px;padding:10px}
</style>
