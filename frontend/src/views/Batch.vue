<template>
  <div class="batch-page">
    <div class="head">
      <h2>入库批次</h2>
      <el-button type="primary" @click="showAdd = true">新增批次</el-button>
    </div>

    <el-alert
      type="warning"
      :closable="false"
      class="rule"
      title="待入批次不再是「待入即可入库」：库间化霜占窗进行中直接拦；否则必须有一张库间、货品名、箱数都对得上的「已确认未核销」预占顶用，入库即核销，不能一顶二。出库同样不是「在库就能出」：库间还有未闭环的异常巡检单时，在库批次一律不得出库。只有「待入」批次可以撤销删除；「在库」必须先按正常流程出库，「已出」永久保留追溯，后端同样硬拦。"
    />

    <div class="cards">
      <el-card v-for="b in batches" :key="b.id" class="card" :class="'st-'+b.status">
        <div class="card-top">
          <span class="cargo">{{ b.cargo }}</span>
          <el-tag :type="tagType(b.status)" effect="dark">{{ b.status }}</el-tag>
        </div>
        <div class="row">库间：{{ cellName(b.cellId) }}</div>
        <div class="row">数量：{{ b.qty }} 箱</div>
        <div class="row">入库日期：{{ b.batchDate }}</div>
        <template v-if="b.status === '待入'">
          <el-alert
            v-if="isDefrosting(b.cellId)"
            type="error" :closable="false" show-icon
            title="该库间化霜占窗进行中，剩余按 0，不能入库"
            class="block"
          />
          <el-alert
            v-else-if="usableList(b).length === 0"
            type="info" :closable="false" show-icon
            title="没有对得上（库间/货品/箱数）的已确认预占，须先去「入库预占」开立并确认"
            class="block"
          />
        </template>
        <template v-if="b.status === '在库'">
          <el-alert
            v-if="hasOpenAbnormal(b.cellId)"
            type="error" :closable="false" show-icon
            title="该库间还有未闭环的异常巡检单，在库批次一律不得出库，请先到「巡检」闭环"
            class="block"
          />
        </template>
        <div class="actions">
          <el-button
            v-if="b.status === '待入'"
            type="success" size="small"
            :disabled="isDefrosting(b.cellId) || usableList(b).length === 0"
            @click="askStockIn(b)"
          >按预占入库</el-button>
          <el-button
            v-else-if="b.status === '在库'"
            type="warning" size="small"
            :disabled="hasOpenAbnormal(b.cellId)"
            @click="stockOut(b)">出库</el-button>
          <el-button
            v-else size="small" disabled>已出库</el-button>
          <el-button
            v-if="b.status === '待入'"
            size="small" type="danger" plain
            @click="remove(b)"
          >撤销</el-button>
          <span v-else-if="b.status === '在库'" class="muted">在库不可删除，请先出库</span>
          <span v-else class="muted">已出留痕，不可删除</span>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="showAdd" title="新增入库批次" width="440px">
      <el-form label-width="90px">
        <el-form-item label="归属库间">
          <el-select v-model="add.cellId" style="width:100%">
            <el-option v-for="c in cells" :key="c.id" :label="c.code+' '+c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="货品名"><el-input v-model="add.cargo" /></el-form-item>
        <el-form-item label="数量(箱)"><el-input-number v-model="add.qty" :min="1" /></el-form-item>
        <el-form-item label="入库日期"><el-date-picker v-model="add.batchDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPick" title="选择顶用预占" width="560px">
      <p class="pick-tip">
        批次：{{ picked?.cargo }} / {{ picked?.qty }} 箱 / {{ picked ? cellName(picked.cellId) : '' }}
      </p>
      <el-radio-group v-model="pickedReservationId" class="pick-group">
        <el-radio
          v-for="r in pickedUsable"
          :key="r.id"
          :value="r.id"
          class="pick-item"
        >
          预占 #{{ r.id }}：{{ r.cargo }} / {{ r.qty }} 箱 / 计划 {{ r.planDate }}
          （确认于 {{ fmt(r.confirmedAt) }}）
        </el-radio>
      </el-radio-group>
      <el-alert
        v-if="pickedUsable.length === 0"
        type="warning" :closable="false"
        title="没有对得上的可用预占（被化霜盖过的旧预占不会出现，需要重开新预占）"
      />
      <template #footer>
        <el-button @click="showPick = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!pickedReservationId"
          @click="doStockIn"
        >确认入库并核销预占</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import http from '../api'

const batches = ref([])
const cells = ref([])
const reservations = ref([])
const capacities = ref([])
const openAbnormals = ref([])
const showAdd = ref(false)
const add = ref({ cellId: null, cargo: '', qty: 1, batchDate: '' })

const showPick = ref(false)
const picked = ref(null)
const pickedUsable = ref([])
const pickedReservationId = ref(null)

const cellMap = ref({})
function cellName(id) { return cellMap.value[id] || ('库间#' + id) }

function tagType(status) {
  if (status === '在库') return 'success'
  if (status === '已出') return 'info'
  return 'warning'
}

const capMap = ref({})
function isDefrosting(cellId) {
  return !!capMap.value[cellId]?.defrostOngoing
}

/** 有未闭环异常巡检单的库间：在库批次一律不得出库（前端预拦，后端出库口照样硬校验） */
const blockedCells = computed(() => new Set(openAbnormals.value.map(i => i.cellId)))
function hasOpenAbnormal(cellId) {
  return blockedCells.value.has(cellId)
}

/** 与批次对得上、且没被化霜盖过的已确认未核销预占（前端预筛，后端仍会硬校验） */
function usableList(b) {
  return reservations.value.filter(r =>
    r.status === '已确认'
    && r.cellId === b.cellId
    && r.cargo === b.cargo
    && r.qty === b.qty
    && !isStale(r)
  )
}

/** 确认之后该库间若又出现过一扇结束时刻更晚的化霜占窗，旧预占即被盖过 */
function isStale(r) {
  return windows.value.some(w =>
    w.cellId === r.cellId
    && r.confirmedAt
    && w.endAt > r.confirmedAt
  )
}

const windows = ref([])
function fmt(s) {
  return s ? s.replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  const [b, c, r, w, cap, oa] = await Promise.all([
    http.get('/batches'),
    http.get('/cells'),
    http.get('/reservations'),
    http.get('/defrost-windows'),
    http.get('/cells/capacity/overview'),
    http.get('/inspections/open-abnormal')
  ])
  batches.value = b
  cells.value = c
  reservations.value = r
  windows.value = w
  capacities.value = cap
  openAbnormals.value = oa
  const m = {}
  c.forEach(x => { m[x.id] = x.code + ' ' + x.name })
  cellMap.value = m
  const cm = {}
  cap.forEach(v => { cm[v.cellId] = v })
  capMap.value = cm
}

function askStockIn(b) {
  picked.value = b
  pickedUsable.value = usableList(b)
  pickedReservationId.value = pickedUsable.value[0]?.id || null
  showPick.value = true
}

async function doStockIn() {
  // 入库 / 撤销并发时，入库可能已被别人先撤销或批次状态已变：
  // 无论成功还是被驳回都整页刷新到最新状态（容量、预占状态一并对齐最终结果）
  try {
    await http.put('/batches/' + picked.value.id + '/stock-in', {
      reservationId: pickedReservationId.value
    })
  } finally {
    showPick.value = false
    await load()
  }
}

async function stockOut(b) {
  try {
    await http.put('/batches/' + b.id + '/stock-out')
  } finally {
    await load()
  }
}

async function remove(b) {
  // 页面只对「待入」开放撤销入口；旧页面状态过期 / 直接发请求时后端照样硬拦，
  // 被驳回后刷新成最新状态，避免还显示着旧状态和旧容量数字
  await ElMessageBox.confirm(
    '仅未入库的「待入」批次可以撤销；撤销后该笔登记消失，对应已确认预占仍保持可用。是否继续？',
    '撤销待入批次', { type: 'warning', confirmButtonText: '撤销' })
  try {
    await http.delete('/batches/' + b.id)
  } finally {
    await load()
  }
}
async function create() {
  await http.post('/batches', { ...add.value })
  showAdd.value = false
  add.value = { cellId: null, cargo: '', qty: 1, batchDate: '' }
  load()
}

onMounted(load)
</script>

<style scoped>
.batch-page { padding: 4px; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.head h2 { margin: 0; }
.rule { margin-bottom: 14px; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 16px; }
.card { border-top: 4px solid #0277bd; }
.card.st-在库 { border-top-color: #67c23a; }
.card.st-已出 { border-top-color: #909399; }
.card.st-待入 { border-top-color: #e6a23c; }
.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.cargo { font-weight: 700; font-size: 16px; }
.row { color: #555; font-size: 13px; line-height: 1.9; }
.block { margin: 6px 0 2px; }
.actions { margin-top: 12px; display: flex; gap: 8px; align-items: center; }
.muted { color: #aaa; font-size: 12px; }
.pick-tip { margin: 0 0 12px; font-weight: 700; }
.pick-group { display: flex; flex-direction: column; gap: 10px; }
.pick-item { margin-right: 0; height: auto; white-space: normal; }
</style>
