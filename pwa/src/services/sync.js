import { getPairingConfig, getLastSyncTs, setLastSyncTs, upsertDecks, upsertNotes, upsertCards, getPendingReviews, markReviewsSynced } from './db'
import { fetchDecks, fetchNotes, fetchCards, postReviews, postSync } from './api'

export async function syncNow(){
  const { serverUrl, token } = await getPairingConfig()
  if (!serverUrl || !token) throw new Error('Not paired')
  const since = await getLastSyncTs()
  const now = Date.now()
  let counts = { decks:0, notes:0, cards:0, pushed:0 }

  // Try unified /api/sync first
  try {
    let pending = await getPendingReviews()
    pending = pending.filter(r => r && r.cardId && Number(r.cardId) > 0)
    const req = {
      lastSyncTimestamp: since || 0,
      reviewLogs: pending.map(r => ({ cardId: Number(r.cardId), rating: (typeof r.rating === 'string') ? ({ AGAIN:1, HARD:2, GOOD:3, EASY:4 }[r.rating.toUpperCase()] || r.rating) : r.rating, reviewedAt: r.ts, latencyMs: r.latencyMs || null, uuid: r.uuid || null }))
    }
    const resp = await postSync(serverUrl, token, req)
    if (resp && resp.data){
      counts.decks = await upsertDecks(resp.data.decks || [])
      counts.notes = await upsertNotes(resp.data.notes || [])
      counts.cards = await upsertCards(resp.data.cards || [])
      if (pending.length){ await markReviewsSynced(pending.map(r=>r.id)); counts.pushed = pending.length }
      await setLastSyncTs(resp.syncTimestamp || now)
      return counts
    }
  } catch (e) {
    console.warn('unified sync failed, falling back', e)
  }

  // Legacy fallback
  const [decks, notes, cards] = await Promise.all([
    fetchDecks(serverUrl, token),
    fetchNotes(serverUrl, token, since),
    fetchCards(serverUrl, token, since)
  ])
  counts.decks = await upsertDecks(decks)
  counts.notes = await upsertNotes(notes)
  counts.cards = await upsertCards(cards)

  let pending = await getPendingReviews()
  pending = pending.filter(r => r && r.cardId && Number(r.cardId) > 0)
  if (pending.length){
    const payload = pending.map(r => ({ cardId: r.cardId, rating: r.rating, ts: r.ts, latencyMs: r.latencyMs || null, uuid: r.uuid || null }))
    const reviewsResp = await postReviews(serverUrl, token, payload)
    if (reviewsResp && reviewsResp.ok){ await markReviewsSynced(pending.map(r=>r.id)); counts.pushed = reviewsResp.processed || pending.length }
  }
  await setLastSyncTs(now)
  return counts
}

// Auto-sync loop
let autoTimer = 0
let backoffMs = 60000
export async function startAutoSync(){
  function schedule(){ try { if (autoTimer) clearTimeout(autoTimer) } catch(_){}; autoTimer = setTimeout(loop, backoffMs) }
  async function loop(){
    try {
      const { serverUrl, token } = await getPairingConfig()
      if (serverUrl && token && navigator.onLine){
        await syncNow()
        backoffMs = 60000
      } else {
        backoffMs = Math.min(backoffMs*1.5, 10*60*1000)
      }
    } catch(_) {
      backoffMs = Math.min(backoffMs*1.5, 10*60*1000)
    } finally { schedule() }
  }
  try { window.addEventListener('online', () => { backoffMs = 2000; try{ if (autoTimer) clearTimeout(autoTimer) }catch(_){ }; autoTimer = setTimeout(loop, backoffMs) }) } catch(_){}
  try { document.addEventListener('visibilitychange', () => { if (!document.hidden){ backoffMs = 2000; try{ if (autoTimer) clearTimeout(autoTimer) }catch(_){ }; autoTimer = setTimeout(loop, backoffMs) } }) } catch(_){}
  backoffMs = 2000; schedule()
}
