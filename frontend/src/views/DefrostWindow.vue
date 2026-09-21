<template>
  <div class="dw-page">
    <div class="head">
      <h2>化霜占窗</h2>
      <el-button type="primary" @click="openAdd">开新占窗</el-button>
    </div>

    <el-alert
      type="warning"
      :closable="false"
      class="rule"
      title="质检口径：冷冻库占窗不得短于 40 分钟，冷藏库不得短于 20 分钟；同一库间重叠时段只留一扇，后开的当场驳回。占窗进行中，该库间剩余可收箱数按 0。"
    />

    <el-table :data="rows" stripe class="tbl">
      <el-table-column label="库间" min-width="170">
        <template #default="{ row }">
          {{ cellLabel(row.cellId) }}
          <el-tag v-if="cellDeleted(row.cellId)" size="small" type="info">库间已软删</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时刻" min-width="150">
        <template #default="{ row }">{{ fmt(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="结束时刻" min-width="150">
        <template #default="{ row }">{{ fmt(row.endAt) }}</template>
      </el-table-column>
      <el-table-column label="时长" width="90">
        <template #default="{ row }">{{ duration(row) }} 分</template>
      </el-table-column>
      <el-table-column label="事由" prop="reason" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="stateType(row.state)" effect="dark">{{ row.state }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showAdd" title="开立化霜占窗" width="520px">
      <el-form label-width="92px">
        <el-form-item label="库间">
          <el-select v-model="add.cellId" style="width:100%" placeholder="选择未软删库间">
            <el-option
              v-for="c in cells"
              :key="c.id"
              :label="c.code + ' ' + c.name + '（' + c.tempZone + '）'"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="起止时刻">
          <el-date-picker
            v-model="add.range"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时刻"
            end-placeholder="结束时刻"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="占窗事由">
          <el-input v-model="add.reason" type="textarea" :rows="2" placeholder="如：蒸发器定期化霜" />
        </el-form-item>
        <el-alert
          v-if="pickedCell"
          type="info"
          :closable="false"
          :title="'该库间为' + pickedCell.tempZone + '，占窗不得短于 ' + minMinutes(pickedCell) + ' 分钟'"
        />
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="create">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import http from '../api'

const windows = ref([])
const cells = ref([])
const showAdd = ref(false)
const add = ref({ cellId: null, range: null, reason: '' })

const rows = computed(() =>
  [...windows.value].sort((a, b) => (a.startAt < b.startAt ? 1 : -1))
)

const cellMap = ref({})
function cellLabel(id) {
  const c = cellMap.value[id]
  return c ? c.code + ' ' + c.name : '库间#' + id
}
function cellDeleted(id) {
  return !cellMap.value[id]
}

const pickedCell = computed(() => cells.value.find(c => c.id === add.value.cellId))
function minMinutes(cell) {
  return cell.tempZone === '冷冻' ? 40 : 20
}

function fmt(s) {
  return s ? s.replace('T', ' ').slice(0, 16) : ''
}
function duration(row) {
  if (!row.startAt || !row.endAt) return 0
  return Math.round((new Date(row.endAt) - new Date(row.startAt)) / 60000)
}
function stateType(state) {
  if (state === '进行中') return 'danger'
  if (state === '已结束') return 'info'
  return 'warning'
}

async function load() {
  const [w, c] = await Promise.all([http.get('/defrost-windows'), http.get('/cells')])
  windows.value = w
  cells.value = c
  const m = {}
  c.forEach(x => { m[x.id] = x })
  cellMap.value = m
}

function openAdd() {
  add.value = { cellId: null, range: null, reason: '' }
  showAdd.value = true
}

async function create() {
  if (!add.value.cellId) return
  if (!add.value.range || add.value.range.length !== 2) return
  if (!add.value.reason || !add.value.reason.trim()) return
  await http.post('/defrost-windows', {
    cellId: add.value.cellId,
    startAt: add.value.range[0],
    endAt: add.value.range[1],
    reason: add.value.reason
  })
  showAdd.value = false
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该化霜占窗？', '提示', { type: 'warning' })
  await http.delete('/defrost-windows/' + row.id)
  await load()
}

onMounted(load)
</script>

<style scoped>
.dw-page { padding: 4px; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.head h2 { margin: 0; }
.rule { margin-bottom: 14px; }
.tbl { background: #fff; border-radius: 8px; }
</style>
