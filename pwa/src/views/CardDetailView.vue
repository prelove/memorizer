<template>
  <div>
    <h2>Card #{{ card?.cardId }}</h2>
    <div class="box">
      <div><b>Front:</b> {{ card?.front }}</div>
      <div><b>Back:</b> {{ card?.back }}</div>
      <div v-if="card?.reading"><b>Reading:</b> {{ card.reading }}</div>
      <div v-if="card?.pos"><b>POS:</b> {{ card.pos }}</div>
      <div v-if="examples.length"><b>Examples:</b>
        <ul>
          <li v-for="(ex,i) in examples" :key="i">{{ ex }}</li>
        </ul>
      </div>
      <div class="pill">Due: {{ fmtTs(card?.dueAt) }} · Reps: {{ card?.reps }} · Lapses: {{ card?.lapses }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getCardDetail } from '../services/db'

const route = useRoute()
const card = ref(null)
const examples = ref([])

onMounted(async () => {
  card.value = await getCardDetail(route.params.id)
  if (card.value && card.value.examples){
    if (Array.isArray(card.value.examples)) examples.value = card.value.examples
    else if (typeof card.value.examples === 'string') examples.value = card.value.examples.split(/\n|\r\n|;\s*/).filter(Boolean)
  }
})

function fmtTs(ts){
  if (!ts) return '-'
  try { return new Date(ts).toLocaleString() } catch { return String(ts) }
}
</script>

<style scoped>
.box{background:#2a2f34;padding:12px;border-radius:8px}
ul{margin:6px 0 0 16px}
</style>

