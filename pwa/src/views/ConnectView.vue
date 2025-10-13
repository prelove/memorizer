<template>
  <div>
    <h2>Connect to Desktop</h2>
    <p class="pill">Enter server URL and token (or scan QR)</p>
    <div style="display:flex; flex-direction:column; gap:10px; max-width:520px">
      <input v-model="serverUrl" @input="persistDraft" placeholder="Server URL (e.g., http://192.168.1.10:7070)" />
      <input v-model="token" @input="persistDraft" placeholder="Pairing Token" />
      <div class="controls">
        <button class="btn" type="button" @click.prevent="verify">Verify</button>
        <button class="btn" type="button" @click.prevent="saveAndContinue" :disabled="!verified">Save</button>
        <button class="btn" type="button" @click.prevent="toggleScan">{{ scanning? 'Stop Scan' : 'Scan QR' }}</button>
        <button class="btn" type="button" v-show="scanning" @click.prevent="switchToServerDecode">Use server decode</button>
      </div>
      <div v-if="message" class="pill">{{ message }}</div>
      <div v-show="scanning" style="width:320px;height:300px;border:1px solid #121417;border-radius:8px;overflow:hidden;position:relative">
        <video ref="videoEl" autoplay playsinline muted width="320" height="240" style="width:320px;height:240px;object-fit:cover"></video>
        <canvas ref="canvasEl" width="320" height="240" style="display:none"></canvas>
        <div class="pill" style="position:absolute;bottom:28px;left:8px;right:8px;text-align:center">Point camera at QR</div>
        <div class="controls" style="position:absolute;bottom:4px;left:8px;right:8px;display:flex;justify-content:center;gap:8px">
          <input ref="fileEl" type="file" accept="image/*" capture="environment" style="display:none" @change="onPickImage" />
          <button class="btn" type="button" :disabled="picking" @click.prevent="pickImage">{{ picking? 'Opening camera...' : 'Scan from photo' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { verifyPairing, decodeQrImage } from '../services/api'
import { savePairingConfig, getPairingConfig } from '../services/db'
import { useRouter } from 'vue-router'

const serverUrl = ref('')
const token = ref('')
const message = ref('')
const verified = ref(false)
const router = useRouter()
const scanning = ref(false)
const videoEl = ref(null)
const canvasEl = ref(null)
const fileEl = ref(null)
const picking = ref(false)
let detector = null
let stream = null
let rafId = 0
let serverScanTimer = 0
const useServerDecode = ref(false)
let lastServerDecodeAt = 0

onMounted(async () => {
  try{
    const cfg = await getPairingConfig()
    if (cfg.serverUrl) serverUrl.value = cfg.serverUrl
    if (cfg.token) token.value = cfg.token
    // draft fallback if not yet saved
    if (!serverUrl.value){ try{ const s = localStorage.getItem('connect_serverUrl'); if (s) serverUrl.value = s } catch(_){} }
    if (!token.value){ try{ const t = localStorage.getItem('connect_token'); if (t) token.value = t } catch(_){} }
  }catch(_){/* ignore */}
})

async function verify(){
  message.value = 'Verifying...'
  verified.value = false
  try{
    const ok = await verifyPairing(serverUrl.value.trim(), token.value.trim())
    verified.value = ok
    message.value = ok ? 'Verified ✓' : 'Invalid token'
  }catch(e){
    message.value = 'Error: '+ e.message
  }
}

async function saveAndContinue(){
  try{
    await savePairingConfig(serverUrl.value.trim(), token.value.trim())
    message.value = 'Saved ✓'
    router.push('/')
  }catch(e){
    message.value = 'Save failed: '+e.message
  }
}

function persistDraft(){
  try{
    localStorage.setItem('connect_serverUrl', serverUrl.value||'')
    localStorage.setItem('connect_token', token.value||'')
  }catch(_){ }
}

function parseQrPayload(text){
  try{
    // Accept either JSON {server, token} or raw URL
    const obj = JSON.parse(text)
    if (obj && obj.server) serverUrl.value = obj.server
    if (obj && obj.token) token.value = obj.token
    return true
  }catch(_){
    try {
      const u = new URL(text)
      if (u.protocol === 'http:' || u.protocol === 'https:'){
        serverUrl.value = u.origin
        const t = u.searchParams.get('token')
        if (t) token.value = t
        return true
      }
    } catch(_2) {}
  }
  return false
}

async function toggleScan(){
  if (scanning.value){
    stopScan()
    scanning.value = false
    return
  }
  scanning.value = true
  try{
    if ('BarcodeDetector' in window){
      useServerDecode.value = false
      detector = new window.BarcodeDetector({ formats: ['qr_code'] })
      stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
      videoEl.value.srcObject = stream
      await videoEl.value.play()
      scanLoop()
      // fallback to server decode if no result in 8 seconds
      setTimeout(() => { if (scanning.value && !useServerDecode.value) switchToServerDecode() }, 8000)
    } else {
      switchToServerDecode()
    }
  }catch(e){ message.value = 'QR start error: '+ e.message }
}

function stopScan(){
  try { if (rafId) cancelAnimationFrame(rafId); rafId=0 } catch(_){ }
  try { if (videoEl.value){ videoEl.value.pause(); videoEl.value.srcObject = null } } catch(_){ }
  try { if (stream){ stream.getTracks().forEach(t=>t.stop()); stream=null } } catch(_){ }
  try { if (serverScanTimer){ clearTimeout(serverScanTimer); serverScanTimer=0 } } catch(_){ }
}

async function scanLoop(){
  if (!scanning.value || !detector || !videoEl.value) return
  try {
    const ctx = canvasEl.value.getContext('2d')
    ctx.drawImage(videoEl.value, 0, 0, canvasEl.value.width, canvasEl.value.height)
    const bitmap = await createImageBitmap(canvasEl.value)
    const codes = await detector.detect(bitmap)
    if (codes && codes.length){
      const val = codes[0].rawValue || codes[0].rawValue || ''
      if (val && parseQrPayload(val)){
        message.value = 'QR read ✓ — verify to continue'
        stopScan()
        scanning.value = false
        return
      }
    }
  } catch (_) {
    // ignore single frame errors
  }
  rafId = requestAnimationFrame(scanLoop)
}

function switchToServerDecode(){
  useServerDecode.value = true
  ;(async () => {
    try{
      if (!serverUrl.value) {
        if (location && location.pathname && location.pathname.indexOf('/pwa/') === 0) {
          serverUrl.value = location.origin
          persistDraft()
        } else {
          message.value = 'Enter Server URL first (e.g., https://<desktop-ip>:7070)'
          return
        }
      }
      if (!stream){
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        videoEl.value.srcObject = stream
        await videoEl.value.play()
      }
      serverDecodeLoop()
    } catch (e) { message.value = 'Server decode error: '+ e.message }
  })()
}

function serverDecodeLoop(){
  if (!scanning.value || !useServerDecode.value) return
  const now = Date.now()
  if (now - lastServerDecodeAt < 600){ serverScanTimer = setTimeout(serverDecodeLoop, 100); return }
  lastServerDecodeAt = now
  try{
    const ctx = canvasEl.value.getContext('2d')
    ctx.drawImage(videoEl.value, 0, 0, canvasEl.value.width, canvasEl.value.height)
    const dataUrl = canvasEl.value.toDataURL('image/jpeg', 0.7)
    decodeQrImage(serverUrl.value || location.origin, dataUrl)
      .then(out => {
        const text = out && out.text
        if (text && parseQrPayload(text)){
          message.value = 'QR read ✓ — verify to continue'
          stopScan(); scanning.value = false
        } else {
          serverScanTimer = setTimeout(serverDecodeLoop, 160)
        }
      })
      .catch(() => { serverScanTimer = setTimeout(serverDecodeLoop, 250) })
  } catch (_) { serverScanTimer = setTimeout(serverDecodeLoop, 250) }
}

function pickImage(){
  try{
    if (!serverUrl.value) {
      if (location && location.pathname && location.pathname.indexOf('/pwa/') === 0) {
        serverUrl.value = location.origin
      } else {
        message.value = 'Enter Server URL first (e.g., https://<desktop-ip>:7070)'
        return
      }
    }
    picking.value = true
    // Reset value so selecting the same photo triggers change
    if (fileEl.value) fileEl.value.value = ''
    fileEl.value && fileEl.value.click()
  } catch(_){ }
}

async function onPickImage(ev){
  try{
    const f = ev.target.files && ev.target.files[0]
    if (!f){ picking.value = false; return }
    const reader = new FileReader()
    reader.onload = async () => {
      try{
        const dataUrl = reader.result
        message.value = 'Decoding photo...'
        const out = await decodeQrImage(serverUrl.value || (location.origin), dataUrl)
        const text = out && out.text
        if (text && parseQrPayload(text)){
          message.value = 'Photo QR ✓ — verify to continue'
          stopScan(); scanning.value = false
        } else {
          message.value = 'No QR detected in photo'
        }
      } catch(e){ message.value = 'Decode failed: '+ e.message }
      finally { try { if (fileEl.value) fileEl.value.value = '' } catch(_){} picking.value = false }
    }
    reader.readAsDataURL(f)
  } catch(e){ message.value = 'Read error: '+ e.message; try { if (fileEl.value) fileEl.value.value = '' } catch(_){} picking.value = false }
}
</script>

<style scoped>
input{background:var(--panel);color:var(--fg);border:1px solid var(--border);border-radius:6px;padding:10px}
</style>
