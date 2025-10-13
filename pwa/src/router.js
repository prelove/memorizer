import { createRouter, createWebHashHistory } from 'vue-router'
import DeckListView from './views/DeckListView.vue'
import ConnectView from './views/ConnectView.vue'
import CardView from './views/CardView.vue'
import NotesListView from './views/NotesListView.vue'
import CardDetailView from './views/CardDetailView.vue'

const routes = [
  { path: '/', component: DeckListView },
  { path: '/connect', component: ConnectView },
  { path: '/deck/:id', component: CardView, props: true },
  { path: '/deck/:id/notes', component: NotesListView, props: true },
  { path: '/card/:id', component: CardDetailView, props: true }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
