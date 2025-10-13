export async function verifyPairing(serverUrl, token){
  // use public pairing endpoint (no auth required)
  const u = new URL('/pair/verify', serverUrl)
  u.searchParams.set('token', token)
  const res = await fetch(u.toString(), { mode:'cors' })
  if (!res.ok) throw new Error('verify failed: '+res.status)
  const data = await res.json()
  return !!data.ok
}

export async function fetchDecks(serverUrl, token){
  const u = new URL('/api/decks', serverUrl)
  const res = await fetch(u.toString(), { headers: { 'X-Token': token }})
  if (!res.ok) throw new Error('decks '+res.status)
  return res.json()
}

export async function fetchNotes(serverUrl, token, since){
  const u = new URL('/api/notes', serverUrl)
  if (since && since>0) u.searchParams.set('since', String(since))
  const res = await fetch(u.toString(), { headers: { 'X-Token': token }})
  if (!res.ok) throw new Error('notes '+res.status)
  return res.json()
}

export async function fetchCards(serverUrl, token, since){
  const u = new URL('/api/cards', serverUrl)
  if (since && since>0) u.searchParams.set('since', String(since))
  const res = await fetch(u.toString(), { headers: { 'X-Token': token }})
  if (!res.ok) throw new Error('cards '+res.status)
  return res.json()
}

export async function postReviews(serverUrl, token, payload){
  const u = new URL('/api/reviews', serverUrl)
  const res = await fetch(u.toString(), { method:'POST', headers: { 'Content-Type':'application/json', 'X-Token': token }, body: JSON.stringify(payload) })
  if (!res.ok) throw new Error('reviews '+res.status)
  return res.json()
}

export async function postSync(serverUrl, token, payload){
  const u = new URL('/api/sync', serverUrl)
  const res = await fetch(u.toString(), { method:'POST', headers: { 'Content-Type':'application/json', 'X-Token': token }, body: JSON.stringify(payload) })
  if (!res.ok) throw new Error('sync '+res.status)
  return res.json()
}

export async function decodeQrImage(serverUrl, dataUrl){
  const u = new URL('/api/pair/decode', serverUrl)
  const res = await fetch(u.toString(), { method:'POST', headers:{ 'Content-Type':'application/json' }, body: JSON.stringify({ image: dataUrl }) })
  if (!res.ok) throw new Error('decode '+res.status)
  return res.json()
}

export async function updateNotes(serverUrl, token, items){
  const u = new URL('/api/notes/update', serverUrl)
  const res = await fetch(u.toString(), { method:'POST', headers: { 'Content-Type':'application/json', 'X-Token': token }, body: JSON.stringify(items) })
  if (!res.ok) throw new Error('notes_update '+res.status)
  return res.json()
}

export async function fetchServerInfo(serverUrl){
  const u = new URL('/api/server/info', serverUrl)
  const res = await fetch(u.toString())
  if (!res.ok) throw new Error('server_info '+res.status)
  return res.json()
}

export async function createCard(serverUrl, token, payload){
  const u = new URL('/api/cards/create', serverUrl)
  const res = await fetch(u.toString(), { method:'POST', headers: { 'Content-Type':'application/json', 'X-Token': token }, body: JSON.stringify(payload) })
  if (!res.ok) throw new Error('create_card '+res.status)
  return res.json()
}

export async function deleteNote(serverUrl, token, id){
  const u = new URL('/api/notes/delete', serverUrl)
  const res = await fetch(u.toString(), { method:'POST', headers: { 'Content-Type':'application/json', 'X-Token': token }, body: JSON.stringify({ id }) })
  if (!res.ok) throw new Error('delete_note '+res.status)
  return res.json()
}

export async function updateDeck(serverUrl, token, id, name){
  const u = new URL('/api/decks/update', serverUrl)
  const res = await fetch(u.toString(), { method:'POST', headers: { 'Content-Type':'application/json', 'X-Token': token }, body: JSON.stringify({ id, name }) })
  if (!res.ok) throw new Error('update_deck '+res.status)
  return res.json()
}

export async function deleteDeck(serverUrl, token, id){
  const u = new URL('/api/decks/delete', serverUrl)
  const res = await fetch(u.toString(), { method:'POST', headers: { 'Content-Type':'application/json', 'X-Token': token }, body: JSON.stringify({ id }) })
  if (!res.ok) throw new Error('delete_deck '+res.status)
  return res.json()
}

export async function createDeck(serverUrl, token, name){
  const u = new URL('/api/decks/create', serverUrl)
  const res = await fetch(u.toString(), { method:'POST', headers: { 'Content-Type':'application/json', 'X-Token': token }, body: JSON.stringify({ name }) })
  if (!res.ok) throw new Error('create_deck '+res.status)
  return res.json()
}
