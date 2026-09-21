<template>
  <div class="insp-page">
    <div class="head">
      <h2>巡检打卡</h2>
      <el-date-picker
        v-model="date"
        type="date"
        value-format="YYYY-MM-DD"
        @change="load"
        style="width: 180px"
      />
      <span class="progress-text">进度 {{ checkedCount }} / {{ rows.length }}</span>
      <el-progress :percentage="percent" :stroke-width="14" style="width: 240px" />
    </div>

    <div class="list">
      <div
        v-for="row in rows"
        :key="row.cell.id"
        class="row"
        :class="{ abnormal: isAbnormal(row), checked: row.checked }"
      >
        <el-checkbox
          v-model="row.checked"
          :disabled="isAbnormal(row)"
          @change="toggle(row)"
        />
        <div class="cell-name">
          {{ row.cell.code }} {{ row.cell.name }}
          <div class="zone">{{ row.cell.tempZone }}库标准 {{ rangeText(row.cell) }}</div>
        </div>
        <template v-if="row.checked">
          <div class="form">
            <el-select
              v-model="row.locationIds"
              multiple
              collapse-tags
              size="small"
              placeholder="点选货位（至少一个）"
              style="width: 170px"
            >
              <el-option v-for="loc in locsOf(row.cell.id)" :key="loc.id" :label="loc.code" :value="loc.id" />
            </el-select>
            <el-input-number
              v-model="row.measuredTemp"
              :precision="1"
              :step="0.5"
              size="small"
              placeholder="实测℃"
              style="width: 110px"
            />
            <el-select v-model="row.result" size="small" style="width: 92px">
              <el-option label="正常" value="正常" />
              <el-option label="异常" value="异常" />
            </el-select>
            <el-input v-model="row.note" size="small" placeholder="备注（选填）" style="width: 140px" />
            <el-input
              v-if="row.result === '异常'"
              v-model="row.handling"
              size="small"
              placeholder="处置意见（异常必填）"
              style="width: 170px"
            />
          </div>
          <div class="ops">
            <el-tag v-if="outOfRange(row)" type="danger" size="small" effect="dark">越界，须记异常</el-tag>
            <template v-if="row.ins && row.ins.result === '异常'">
              <el-tag v-if="row.ins.closed === 1" type="info" size="small">已闭环 {{ fmt(row.ins.closedAt) }}</el-tag>
              <template v-else>
                <el-tag type="danger" size="small">未闭环</el-tag>
                <el-button size="small" type="warning" @click="closeRow(row)">闭环</el-button>
              </template>
            </template>
            <el-button size="small" type="primary" @click="saveRow(row)">保存</el-button>
            <el-button v-if="row.ins && row.ins.result !== '异常'" size="small" @click="removeRow(row)">撤销</el-button>
            <el-button v-if="!row.ins" size="small" @click="row.checked = false">取消</el-button>
          </div>
        </template>
        <template v-else>
          <span class="muted">未打卡</span>
        </template>
      </div>
    </div>
    <el-alert
      type="info"
      :closable="false"
      class="tip"
      title="每单必须点货位、填实测温度；实测温度越出温区上下限（冷冻 -25.0 ~ -15.0℃、冷藏 0.0 ~ 8.0℃）必须记「异常」并写处置意见，否则后端当场驳回。异常单未闭环前一直开着，该库间在库批次一律不得出库；异常单只能闭环留痕，不能删除或改回正常。"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'

/** 温区上下限（℃），与后端 InspectionService 质检口径一致 */
const RANGES = { 冷冻: [-25.0, -15.0], 冷藏: [0.0, 8.0] }

const date = ref('2026-09-18')
const rows = ref([])
const locations = ref([])

const checkedCount = computed(() => rows.value.filter(r => r.ins).length)
const percent = computed(() =>
  rows.value.length ? Math.round((checkedCount.value / rows.value.length) * 100) : 0
)

function rangeText(cell) {
  const r = RANGES[cell.tempZone] || RANGES.冷藏
  return `${r[0].toFixed(1)} ~ ${r[1].toFixed(1)}℃`
}
function locsOf(cellId) { return locations.value.filter(l => l.cellId === cellId) }
function isAbnormal(row) { return !!row.ins && row.ins.result === '异常' }
function outOfRange(row) {
  if (row.measuredTemp === null || row.measuredTemp === undefined) return false
  const r = RANGES[row.cell.tempZone] || RANGES.冷藏
  return row.measuredTemp < r[0] || row.measuredTemp > r[1]
}
function fmt(s) { return s ? s.replace('T', ' ').slice(0, 16) : '' }

async function load() {
  const [cells, ins, locs] = await Promise.all([
    http.get('/cells'),
    http.get('/inspections?date=' + date.value),
    http.get('/locations')
  ])
  locations.value = locs
  rows.value = cells.map(cell => {
    const it = ins.find(i => i.cellId === cell.id)
    return {
      cell,
      ins: it || null,
      checked: !!it,
      result: it ? it.result : '正常',
      note: it ? (it.note || '') : '',
      handling: it ? (it.handling || '') : '',
      measuredTemp: it ? it.measuredTemp : null,
      locationIds: it ? [...(it.locationIds || [])] : []
    }
  })
}

/** 勾选只展开表单，点保存才真正开单；取消勾选且已开单（仅正常单能取消勾选）即撤销 */
async function toggle(row) {
  if (row.checked) return
  if (row.ins) {
    await removeRow(row)
  }
}

async function saveRow(row) {
  if (!row.locationIds.length) { ElMessage.error('巡检必须至少点选一个货位'); return }
  if (row.measuredTemp === null || row.measuredTemp === undefined) { ElMessage.error('必须填写实测温度'); return }
  if (outOfRange(row) && row.result !== '异常') { ElMessage.error('实测温度越界，必须记为「异常」并填写处置意见'); return }
  if (row.result === '异常' && !row.handling.trim()) { ElMessage.error('异常巡检必须填写处置意见'); return }
  const body = {
    cellId: row.cell.id,
    checkDate: date.value,
    result: row.result,
    note: row.note,
    measuredTemp: row.measuredTemp,
    locationIds: row.locationIds,
    handling: row.handling
  }
  if (!row.ins) {
    await http.post('/inspections', body)
  } else {
    await http.put('/inspections/' + row.ins.id, body)
  }
  await load()
}

async function removeRow(row) {
  if (row.ins) await http.delete('/inspections/' + row.ins.id)
  row.checked = false
  await load()
}

async function closeRow(row) {
  let closeNote = ''
  try {
    const res = await ElMessageBox.prompt('闭环说明（选填）', `闭环异常巡检单 #${row.ins.id}`, {
      confirmButtonText: '确认闭环',
      cancelButtonText: '取消',
      inputPlaceholder: '如：压缩机已修复，复测温度正常'
    })
    closeNote = res.value || ''
  } catch {
    return // 用户取消
  }
  try {
    await http.put('/inspections/' + row.ins.id + '/close', { closeNote })
    ElMessage.success('异常巡检单已闭环')
  } catch {
    // 后端驳回（如已被他人抢先闭环），拦截器已弹出原因
  }
  await load()
}

onMounted(load)
</script>

<style scoped>
.insp-page { padding: 4px; }
.head { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
.head h2 { margin: 0; }
.progress-text { color: #555; font-size: 14px; }
.list { display: flex; flex-direction: column; gap: 10px; }
.row {
  display: flex; align-items: center; gap: 14px; flex-wrap: wrap;
  background: #fff; border-radius: 8px; padding: 10px 16px;
  border-left: 4px solid #dcdfe6;
  box-shadow: 0 1px 6px rgba(0,0,0,0.04);
}
.row.checked { border-left-color: #67c23a; }
.row.abnormal { border-left-color: #f56c6c; background: #fef0f0; }
.cell-name { font-weight: 600; min-width: 200px; }
.zone { font-weight: 400; font-size: 12px; color: #909399; }
.form { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ops { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.muted { color: #aaa; font-size: 13px; }
.tip { margin-top: 16px; }
</style>
