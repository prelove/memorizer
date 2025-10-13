<template>
  <div>
    <h2>Decks</h2>
    <div v-if="!decks.length" class="pill" style="margin:8px 0">No decks found. Open the menu and press Sync Now or Full Refresh.</div>
    <div v-for="d in decks" :key="d.id" class="deck">
      <div>
        <div>{{ d.name }}</div>
        <div class="pill">Notes {{ d.notesCount }} · Cards {{ d.cardsCount }} <span :style="{ color: d.dueCount>0 ? '#ff9aa2':'#bdbdbd' }">· Due {{ d.dueCount }}</span></div>
      </div>
      <div class="controls">
        <button class="btn" @click="studyDeck(d.id)">Study</button>
        <button class="btn" @click="openNotes(d.id)">Browse</button>
        <button class="btn" @click="openRename(d)">Rename</button>
        <button class="btn" @click="confirmDeleteDeck(d)">Delete</button>
      </div>
    </div>
    <div class="controls" style="margin-top:8px;gap:8px">
      <button class="btn" @click="reload">Refresh</button>
      <button class="btn" @click="openCreateDeck">+ Add Deck</button>
    </div>
  </div>
  <!-- Rename modal -->
  <div v-if="showRename" class="modal">
    <div class="dialog">
      <h3>Rename Deck</h3>
      <div class="row"><label>New name</label><input v-model="renameName" /></div>
      <div class="controls" style="justify-content:flex-end">
        <button class="btn" @click="closeRename">Cancel</button>
        <button class="btn" @click="saveRename" :disabled="renaming">{{ renaming ? 'Saving...' : 'Save' }}</button>
      </div>
    </div>
  </div>
  <!-- Create deck modal -->
  <div v-if="showCreate" class="modal">
    <div class="dialog">
      <h3>Create Deck</h3>
      <div class="row"><label>Name</label><input v-model="createName" /></div>
      <div class="controls" style="justify-content:flex-end">
        <button class="btn" @click="closeCreate">Cancel</button>
        <button class="btn" @click="saveCreate" :disabled="creating">{{ creating ? 'Creating...' : 'Create' }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { getDecksWithCounts, updateDeckLocal, deleteDeckLocal, upsertDecks } from '../services/db'
import { getPairingConfig } from '../services/db'
import { updateDeck, deleteDeck, createDeck } from '../services/api'
import { useRouter } from 'vue-router'

const decks = ref([])
const router = useRouter()

async function reload(){ decks.value = await getDecksWithCounts() }

onMounted(async () => {
  await reload()
  try {
    window.addEventListener('memorizer:sync-complete', reload)
  } catch(_){}
})

onBeforeUnmount(() => { try { window.removeEventListener('memorizer:sync-complete', reload) } catch(_){} })

async function openRename(deck){
  renameDeckId.value = deck.id
  renameName.value = deck.name
  showRename.value = true
}

async function confirmDeleteDeck(deck){
  if (!confirm(`Delete deck "${deck.name}" and all notes/cards?`)) return
  try{
    const { serverUrl, token } = await getPairingConfig()
    if (serverUrl && token){ await deleteDeck(serverUrl, token, deck.id) }
    await deleteDeckLocal(deck.id)
    await reload()
  }catch(e){ alert('Delete failed: '+ (e?.message||e)) }
}

// Rename modal state
const showRename = ref(false)
const renameDeckId = ref(null)
const renameName = ref('')
const renaming = ref(false)
function closeRename(){ showRename.value = false; renameDeckId.value=null; renameName.value='' }
async function saveRename(){
  if (!renameDeckId.value) return
  if (!renameName.value.trim()){ alert('Name is required'); return }
  renaming.value = true
  try{
    const { serverUrl, token } = await getPairingConfig()
    if (serverUrl && token){ await updateDeck(serverUrl, token, renameDeckId.value, renameName.value.trim()) }
    await updateDeckLocal(renameDeckId.value, renameName.value.trim())
    await reload(); closeRename()
  }catch(e){ alert('Rename failed: '+ (e?.message||e)) }
  finally { renaming.value = false }
}

function openNotes(id){
  router.push(`/deck/${id}/notes`)
}

function studyDeck(id){
  router.push(`/deck/${id}`)
}

// Create deck modal
const showCreate = ref(false)
const createName = ref('')
const creating = ref(false)
function openCreateDeck(){ showCreate.value = true; createName.value='' }
function closeCreate(){ showCreate.value = false; createName.value='' }
async function saveCreate(){
  if (!createName.value.trim()){ alert('Name is required'); return }
  creating.value = true
  try{
    const { serverUrl, token } = await getPairingConfig()
    if (!serverUrl || !token){ alert('Not paired. Open Connect.'); return }
    const res = await createDeck(serverUrl, token, createName.value.trim())
    if (res && res.id){
      // Echo into Dexie for instant UI
      await upsertDecks([{ id: res.id, name: res.name }])
      await reload(); closeCreate(); try{ window.dispatchEvent(new CustomEvent('memorizer:toast',{ detail:'Deck created' })) }catch(_){ }
    }
  }catch(e){ alert('Create failed: '+ (e?.message||e)) }
  finally { creating.value = false }
}
</script>

<style scoped>
.modal{position:fixed;left:0;top:0;right:0;bottom:0;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;z-index:1000}
.dialog{background:var(--bg);color:var(--fg);border:1px solid var(--border);border-radius:8px;padding:12px;min-width:300px;max-width:520px;width:90%}
.row{display:flex;flex-direction:column;margin:8px 0}
.row label{font-size:12px;color:var(--muted);margin-bottom:4px}
input{background:var(--panel);color:var(--fg);border:1px solid var(--border);border-radius:6px;padding:10px}
</style>
