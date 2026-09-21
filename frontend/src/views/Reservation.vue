<template>
  <div class="rs-page">
    <div class="head">
      <h2>入库预占</h2>
      <el-button type="primary" @click="openAdd">开新预占</el-button>
    </div>

    <el-alert
      type="warning"
      :closable="false"
      class="rule"
      title="剩余口径：库间容量 − 在库箱数 − 仍待入箱数 − 已确认未核销预占箱数；化霜占窗进行中的库间剩余直接按 0，预占不能确认。两张预占抢最后一段余量时只确认得成一张，被挤掉的会显示当时还剩多少箱。"
    />

    <div class="cap-cards">
      <el-card
        v-for="v in capacities"
        :key="v.cellId"
        class="cap-card"
        :class="{ frozen: v.defrostOngoing }"
      >
        <div class="cap-title">{{ v.code }} {{ v.name }}</div>
        <div class="cap-zone">{{ v.tempZone }} · 容量 {{ v.capacity }}</div>
        <div class="cap-num">
          {{ v.remaining }}
          <span class="unit">箱可收</span>
          <el-tag v-if="v.defrostOngoing" type="danger" effect="dark" size="small">化霜中</el-tag>
        </div>
        <div class="cap-detail">
          在库 {{ v.inStockQty }} · 待入 {{ v.pendingQty }} · 已预占 {{ v.reservedQty }}
        </div>
      </el-card>
    </div>

    <h3 class="sub">预占单</h3>
    <el-table :data="reservations" stripe class="tbl">
      <el-table-column label="库间" min-width="170">
        <template #default="{ row }">
          {{ cellLabel(row.cellId) }}
          <el-tag v-if="cellDeleted(row.cellId)" size="small" type="info">库间已软删</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="货品" prop="cargo" min-width="120" />
      <el-table-column label="箱数" width="80">
        <template #default="{ row }">{{ row.qty }}</template>
      </el-table-column>
      <el-table-column label="计划入库日" prop="planDate" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="tagType(row.status)" effect="dark">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="确认/核销时间" min-width="150">
        <template #default="{ row }">
          <span v-if="row.status === '已核销'">{{ fmt(row.writtenOffAt) }}（批次#{{ row.batchId }}）</span>
          <span v-else-if="row.status === '已确认'">{{ fmt(row.confirmedAt) }}</span>
          <span v-else class="muted">未确认</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170">
        <template #default="{ row }">
          <el-button
            v-if="row.status === '待确认'"
            type="success" size="small" @click="confirmRow(row)"
          >确认</el-button>
          <el-button
            v-if="row.status !== '已核销'"
            size="small" type="danger" plain @click="remove(row)"
          >删除</el-button>
          <span v-else class="muted">已核销留痕</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showAdd" title="开立入库预占" width="460px">
      <el-form label-width="96px">
        <el-form-item label="库间">
          <el-select v-model="add.cellId" style="width:100%" placeholder="选择库间（软删库间不能新开）">
            <el-option
              v-for="c in cells"
              :key="c.id"
              :label="c.code + ' ' + c.name + '（' + c.tempZone + '）'"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="货品名">
          <el-input v-model="add.cargo" placeholder="须与入库批次货品名完全一致" />
        </el-form-item>
        <el-form-item label="箱数">
          <el-input-number v-model="add.qty" :min="1" />
        </el-form-item>
        <el-form-item label="计划入库日">
          <el-date-picker v-model="add.planDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="开立后为「待确认」，不占剩余箱数；点确认时才按剩余口径校验。"
        />
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="create">开立</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import http from '../api'

const reservations = ref([])
const cells = ref([])
const capacities = ref([])
const showAdd = ref(false)
const add = ref({ cellId: null, cargo: '', qty: 1, planDate: '' })

const cellMap = ref({})
function cellLabel(id) {
  const c = cellMap.value[id]
  return c ? c.code + ' ' + c.name : '库间#' + id
}
function cellDeleted(id) {
  return !cellMap.value[id]
}

function fmt(s) {
  return s ? s.replace('T', ' ').slice(0, 16) : ''
}
function tagType(status) {
  if (status === '已确认') return 'success'
  if (status === '已核销') return 'info'
  return 'warning'
}

async function load() {
  const [r, c, cap] = await Promise.all([
    http.get('/reservations'),
    http.get('/cells'),
    http.get('/cells/capacity/overview')
  ])
  reservations.value = r
  cells.value = c
  capacities.value = cap
  const m = {}
  c.forEach(x => { m[x.id] = x })
  cellMap.value = m
}

function openAdd() {
  add.value = { cellId: null, cargo: '', qty: 1, planDate: '' }
  showAdd.value = true
}

async function create() {
  await http.post('/reservations', { ...add.value })
  showAdd.value = false
  await load()
}

async function confirmRow(row) {
  // 被挤掉时后端会在 message 里说清当时还剩多少箱，拦截器已弹错
  await http.put('/reservations/' + row.id + '/confirm')
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该预占？已核销预占会保留留痕。', '提示', { type: 'warning' })
  await http.delete('/reservations/' + row.id)
  await load()
}

onMounted(load)
</script>

<style scoped>
.rs-page { padding: 4px; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.head h2 { margin: 0; }
.rule { margin-bottom: 14px; }
.cap-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; margin-bottom: 18px; }
.cap-card { border-top: 4px solid #0277bd; }
.cap-card.frozen { border-top-color: #f56c6c; background: #fef0f0; }
.cap-title { font-weight: 700; font-size: 14px; }
.cap-zone { color: #888; font-size: 12px; margin: 2px 0 8px; }
.cap-num { font-size: 26px; font-weight: 700; color: #0277bd; display: flex; align-items: center; gap: 8px; }
.cap-card.frozen .cap-num { color: #f56c6c; }
.unit { font-size: 12px; font-weight: 400; color: #777; }
.cap-detail { color: #888; font-size: 12px; margin-top: 6px; }
.sub { margin: 4px 0 10px; font-size: 15px; }
.tbl { background: #fff; border-radius: 8px; }
.muted { color: #aaa; font-size: 12px; }
</style>
