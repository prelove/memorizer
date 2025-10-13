import Dexie from 'dexie'

export const db = new Dexie('memorizerDB')
db.version(1).stores({
  decks: 'id,name',
  notes: 'id,deckId',
  cards: 'id,noteId,dueAt',
  review_logs: '++id,cardId,synced'
})
db.version(2).stores({
  settings: 'key'
})

export async function populateMockData(){
  await db.open()
  // Skip seeding if paired
  try {
    const ls = (typeof localStorage !== 'undefined') ? localStorage.getItem('server_url') : null
    if (ls && ls.trim() !== '') return
  } catch (_) { /* ignore */ }
  const count = await db.decks.count()
  if (count > 0) return
  const decks = [
    { id: 1, name:'Japanese N5' },
    { id: 2, name:'English GRE' }
  ]
  const notes = [
    { id: 101, deckId:1, front:'犬', back:'dog' },
    { id: 102, deckId:1, front:'猫', back:'cat' },
    { id: 201, deckId:2, front:'loquacious', back:'talkative' }
  ]
  const cards = [
    { id: 1001, noteId:101, dueAt: Date.now() - 1000 },
    { id: 1002, noteId:102, dueAt: Date.now() - 2000 },
    { id: 2001, noteId:201, dueAt: Date.now() - 3000 }
  ]
  await db.transaction('rw', db.decks, db.notes, db.cards, async () => {
    await db.decks.bulkAdd(decks)
    await db.notes.bulkAdd(notes)
    await db.cards.bulkAdd(cards)
  })
  try { await db.table('settings').put({ key:'mock_seeded', value: '1' }) } catch(_){}
}

// One-time cleanup for legacy invalid review logs (e.g., cardId <= 0)
export async function cleanupLegacyReviewLogsOnce(){
  await db.open()
  const done = await db.table('settings').get('cleanup_v1_done')
  if (done && String(done.value) === '1') return { deleted: 0 }
  let deleted = 0
  await db.transaction('rw', db.review_logs, db.table('settings'), async () => {
    const bad = await db.review_logs.filter(r => !r || !r.cardId || Number(r.cardId) <= 0).toArray()
    for (const r of bad){ await db.review_logs.delete(r.id); deleted++ }
    await db.table('settings').put({ key:'cleanup_v1_done', value: '1' })
    await db.table('settings').put({ key:'cleanup_v1_deleted', value: String(deleted) })
  })
  return { deleted }
}

export async function getDecks(){
  return db.decks.toArray()
}

export async function getDecksWithCounts(){
  await db.open()
  const decks = await db.decks.toArray()
  const out = []
  for (const d of decks){
    const noteRows = await db.notes.where('deckId').equals(d.id).toArray()
    const noteIds = noteRows.map(n=>n.id)
    const notesCount = noteIds.length
    let cardsCount = 0
    let dueCount = 0
    if (noteIds.length){
      const cards = await db.cards.where('noteId').anyOf(noteIds).toArray()
      cardsCount = cards.length
      const now = Date.now()
      for (const c of cards){ if (c && c.dueAt && Number(c.dueAt) <= now) dueCount++ }
    }
    out.push({ id: d.id, name: d.name, notesCount, cardsCount, dueCount })
  }
  return out
}

export async function nextDueCardByDeck(deckId){
  // join cards->notes in app logic
  const noteRows = await db.notes.where('deckId').equals(+deckId).toArray()
  const noteIds = noteRows.map(n=>n.id)
  const pool = noteIds.length ? await db.cards.where('noteId').anyOf(noteIds).toArray() : []
  pool.sort((a,b)=> (a.dueAt||0)-(b.dueAt||0))
  const c = pool[0]
  if (!c) return null
  const n = await db.notes.get(c.noteId)
  return { cardId:c.id, noteId:c.noteId, front:n.front, back:n.back, reading:n.reading, pos:n.pos, examples:n.examples }
}

// Return the next N cards (sorted by dueAt asc) with joined note fields
export async function nextNCardsByDeck(deckId, limit){
  await db.open()
  const noteIds = (await db.notes.where('deckId').equals(+deckId).toArray()).map(n=>n.id)
  if (!noteIds.length) return []
  const pool = await db.cards.where('noteId').anyOf(noteIds).toArray()
  pool.sort((a,b)=> (a.dueAt||0)-(b.dueAt||0))
  const out = []
  for (const c of pool){
    const n = await db.notes.get(c.noteId)
    out.push({ cardId:c.id, noteId:c.noteId, front:n.front, back:n.back, reading:n.reading, pos:n.pos, examples:n.examples, dueAt:c.dueAt })
    if (out.length >= (limit||2)) break
  }
  return out
}

export async function getNotesByDeck(deckId){
  await db.open()
  const notes = await db.notes.where('deckId').equals(+deckId).toArray()
  const out = []
  for (const n of notes){
    const card = await db.cards.where('noteId').equals(n.id).first()
    const noteUpdated = n.updatedAt || null
    const cardUpdated = card ? (card.updatedAt || null) : null
    const updatedAt = (noteUpdated && cardUpdated) ? Math.max(Number(noteUpdated), Number(cardUpdated)) : (noteUpdated || cardUpdated || null)
    out.push({ id:n.id, front:n.front, back:n.back, reading:n.reading, pos:n.pos, examples:n.examples, tags:n.tags, updatedAt, cardId: card? card.id : null, dueAt: card? card.dueAt : null })
  }
  return out
}

export async function getCardDetail(cardId){
  await db.open()
  const c = await db.cards.get(+cardId)
  if (!c) return null
  const n = await db.notes.get(c.noteId)
  return { cardId: c.id, noteId: c.noteId, front: n?.front, back: n?.back, reading: n?.reading, pos: n?.pos, examples: n?.examples, tags: n?.tags, dueAt: c.dueAt, intervalDays: c.intervalDays, ease: c.ease, reps: c.reps, lapses: c.lapses, status: c.status }
}

export async function updateLocalDueAfterRating(cardId, rating){
  await db.open()
  const c = await db.cards.get(Number(cardId))
  if (!c) return
  const now = Date.now()
  let delta = 60000
  const r = typeof rating === 'number' ? rating : (toRating(rating)||1)
  if (r === 1) delta = 60*1000
  else if (r === 2) delta = 5*60*1000
  else if (r === 3) delta = 30*60*1000
  else if (r === 4) delta = 2*60*60*1000
  await db.cards.update(c.id, { dueAt: now + delta, updatedAt: now })
}

function toRating(val){
  if (val == null) return null
  if (typeof val === 'number') return val
  const s = String(val).trim().toUpperCase()
  if (/^[1-4]$/.test(s)) return parseInt(s,10)
  if (s === 'AGAIN') return 1
  if (s === 'HARD') return 2
  if (s === 'GOOD') return 3
  if (s === 'EASY') return 4
  return null
}

function uuidv4(){
  // simple UUID v4
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random()*16|0, v = c==='x'? r : (r&0x3|0x8); return v.toString(16)
  })
}

export async function addReviewLog(cardId, rating){
  const rt = toRating(rating)
  const cid = Number(cardId)
  await db.open()
  const row = { cardId: cid, rating: rt, ts: Date.now(), latencyMs: null, uuid: uuidv4(), synced: 0 }
  const id = await db.review_logs.add(row)
  return { id, uuid: row.uuid }
}

export async function savePairingConfig(serverUrl, token){
  await db.open()
  await db.table('settings').put({ key:'serverUrl', value: serverUrl })
  await db.table('settings').put({ key:'token', value: token })
  try {
    // Mirror to localStorage for quick boot and resilience
    localStorage.setItem('server_url', serverUrl || '')
    localStorage.setItem('server_token', token || '')
  } catch (_) { /* ignore */ }
}

export async function getPairingConfig(){
  await db.open()
  const server = await db.table('settings').get('serverUrl')
  const token  = await db.table('settings').get('token')
  let serverUrl = server? server.value : null
  let tok = token? token.value : null
  // Fallback to localStorage if DB empty
  try {
    if (!serverUrl) serverUrl = localStorage.getItem('server_url') || null
    if (!tok) tok = localStorage.getItem('server_token') || null
    // If we recovered from localStorage, persist back to Dexie
    if (serverUrl) await db.table('settings').put({ key:'serverUrl', value: serverUrl })
    if (tok) await db.table('settings').put({ key:'token', value: tok })
  } catch (_) { /* ignore */ }
  return { serverUrl, token: tok }
}

export async function getServerId(){
  await db.open()
  const it = await db.table('settings').get('serverId')
  return it ? it.value : null
}
export async function setServerId(id){
  await db.open()
  await db.table('settings').put({ key:'serverId', value: id })
}

// --- Sync helpers ---
export async function getLastSyncTs(){
  await db.open()
  const it = await db.table('settings').get('lastSyncTs')
  return it ? (parseInt(it.value)||0) : 0
}

export async function setLastSyncTs(ts){
  await db.open()
  await db.table('settings').put({ key:'lastSyncTs', value: ts })
}

export async function fullRefreshSyncState(){
  await db.open()
  await db.table('settings').put({ key:'lastSyncTs', value: 0 })
}

export async function getLocalCounts(){
  await db.open()
  const decks = await db.decks.count()
  const notes = await db.notes.count()
  const cards = await db.cards.count()
  const lastSyncTs = await getLastSyncTs()
  return { decks, notes, cards, lastSyncTs }
}

export async function getDueCountByDeck(deckId){
  await db.open()
  const noteIds = (await db.notes.where('deckId').equals(+deckId).toArray()).map(n=>n.id)
  if (!noteIds.length) return 0
  const cards = await db.cards.where('noteId').anyOf(noteIds).toArray()
  const now = Date.now()
  return cards.reduce((acc,c)=> acc + (c && c.dueAt && Number(c.dueAt)<=now ? 1 : 0), 0)
}

export async function getTodayReviewCount(deckId){
  await db.open()
  const start = new Date(); start.setHours(0,0,0,0)
  const end = new Date(); end.setHours(23,59,59,999)
  const logs = await db.review_logs.toArray()
  if (!deckId) return logs.filter(r => r.ts>=start.getTime() && r.ts<=end.getTime()).length
  // map cardId -> deckId
  const cardMap = {}
  if (deckId){
    const notes = await db.notes.where('deckId').equals(+deckId).toArray()
    const noteIds = notes.map(n=>n.id)
    const cards = noteIds.length ? await db.cards.where('noteId').anyOf(noteIds).toArray() : []
    for (const c of cards){ cardMap[c.id] = true }
  }
  return logs.filter(r => r.ts>=start.getTime() && r.ts<=end.getTime() && cardMap[r.cardId]).length
}

export async function getDailyTarget(){
  await db.open()
  const t = await db.table('settings').get('dailyTarget')
  return t ? (parseInt(t.value)||50) : 50
}

export async function updateNote(noteId, patch){
  await db.open()
  const now = Date.now()
  const p = Object.assign({}, patch, { updatedAt: now })
  await db.notes.update(Number(noteId), p)
}

// If mock data was seeded and pairing completed once, clear mock so server data can replace it.
export async function maybeClearMockOnPairingOnce(){
  await db.open()
  const cleared = await db.table('settings').get('mock_clear_done')
  if (cleared && String(cleared.value) === '1') return { cleared: false }
  const mock = await db.table('settings').get('mock_seeded')
  if (!mock || String(mock.value) !== '1') return { cleared: false }
  const last = await db.table('settings').get('lastSyncTs')
  // Only clear after we know user has synced at least once (to confirm pairing)
  const lastTs = last ? parseInt(last.value||'0',10) : 0
  if (lastTs <= 0) return { cleared: false }
  await db.transaction('rw', db.decks, db.notes, db.cards, db.review_logs, db.table('settings'), async () => {
    await db.decks.clear(); await db.notes.clear(); await db.cards.clear(); await db.review_logs.clear()
    await db.table('settings').put({ key:'mock_seeded', value: '0' })
    await db.table('settings').put({ key:'mock_clear_done', value: '1' })
    await db.table('settings').put({ key:'lastSyncTs', value: 0 })
  })
  return { cleared: true }
}

export async function upsertDecks(items){
  if (!items || !items.length) return 0
  await db.open()
  await db.transaction('rw', db.decks, async () => {
    for (const d of items){ await db.decks.put({ id:d.id, name:d.name }) }
  })
  return items.length
}

export async function deleteNoteLocal(id){
  await db.open()
  const note = await db.notes.get(Number(id))
  if (!note) return
  await db.cards.where('noteId').equals(note.id).delete()
  await db.notes.delete(note.id)
}

export async function updateDeckLocal(id, name){
  await db.open()
  await db.decks.update(Number(id), { name })
}

export async function deleteDeckLocal(id){
  await db.open()
  const deckId = Number(id)
  const notes = await db.notes.where('deckId').equals(deckId).toArray()
  const noteIds = notes.map(n=>n.id)
  if (noteIds.length){ await db.cards.where('noteId').anyOf(noteIds).delete() }
  await db.notes.where('deckId').equals(deckId).delete()
  await db.decks.delete(deckId)
}

export async function upsertNotes(items){
  if (!items || !items.length) return 0
  await db.open()
  await db.transaction('rw', db.notes, async () => {
    for (const n of items){
      if (n.deleted){ await db.notes.delete(n.id); continue }
      await db.notes.put({ id:n.id, deckId:n.deckId, front:n.front, back:n.back, reading:n.reading, pos:n.pos, examples:n.examples, tags:n.tags, updatedAt:n.updatedAt })
    }
  })
  return items.length
}

export async function upsertCards(items){
  if (!items || !items.length) return 0
  await db.open()
  await db.transaction('rw', db.cards, async () => {
    for (const c of items){
      if (c.deleted){ await db.cards.delete(c.id); continue }
      await db.cards.put({ id:c.id, noteId:c.noteId, dueAt:c.dueAt, updatedAt:c.updatedAt, intervalDays:c.intervalDays, ease:c.ease, reps:c.reps, lapses:c.lapses, status:c.status })
    }
  })
  return items.length
}

export async function getPendingReviews(){
  await db.open()
  return db.review_logs.where('synced').equals(0).toArray()
}

export async function markReviewsSynced(ids){
  if (!ids || !ids.length) return
  await db.open()
  await db.transaction('rw', db.review_logs, async () => {
    for (const id of ids){ await db.review_logs.update(id, { synced: 1 }) }
  })
}
