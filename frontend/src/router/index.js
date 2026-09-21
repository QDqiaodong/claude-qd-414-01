import { createRouter, createWebHistory } from 'vue-router'
import Cell from '../views/Cell.vue'
import Location from '../views/Location.vue'
import Batch from '../views/Batch.vue'
import Inspection from '../views/Inspection.vue'
import DefrostWindow from '../views/DefrostWindow.vue'
import Reservation from '../views/Reservation.vue'

const routes = [
  { path: '/', redirect: '/cells' },
  { path: '/cells', name: '库间', component: Cell },
  { path: '/locations', name: '货位', component: Location },
  { path: '/batches', name: '入库批次', component: Batch },
  { path: '/defrost-windows', name: '化霜占窗', component: DefrostWindow },
  { path: '/reservations', name: '入库预占', component: Reservation },
  { path: '/inspections', name: '巡检', component: Inspection }
]

export default createRouter({ history: createWebHistory(), routes })
