<template>
  <div class="cell-page">
    <div class="left">
      <div class="left-head">
        <span>库间 / 货位 树</span>
        <el-button type="primary" size="small" @click="showAdd = true">新增库间</el-button>
      </div>
      <el-tree
        class="tree"
        :data="treeData"
        :props="{ label: 'label', children: 'children' }"
        node-key="id"
        default-expand-all
        @node-click="onNodeClick"
      />
    </div>

    <div class="right" v-if="selectedCell">
      <h3>库间详情（软删除演示）</h3>
      <el-form label-width="90px">
        <el-form-item label="编号"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="温区">
          <el-select v-model="form.tempZone" style="width:100%">
            <el-option label="冷冻" value="冷冻" />
            <el-option label="冷藏" value="冷藏" />
          </el-select>
        </el-form-item>
        <el-form-item label="容量(箱)"><el-input-number v-model="form.capacity" :min="0" /></el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="saveCell">保存修改</el-button>
          <el-button type="danger" @click="removeCell">软删除该库间</el-button>
        </el-form-item>
      </el-form>

      <div class="cap-box" v-if="selectedCap">
        <div class="cap-head">
          容量总览（保存后立即按同一口径刷新）
        </div>
        <div class="cap-line">
          当前容量 <b>{{ selectedCap.capacity }}</b> 箱 ·
          已在库 <b>{{ selectedCap.inStockQty }}</b> 箱 ·
          仍待入 <b>{{ selectedCap.pendingQty }}</b> 箱 ·
          已确认未核销预占 <b>{{ selectedCap.reservedQty }}</b> 箱
        </div>
        <div class="cap-line">
          容量变更下限：<b class="floor">{{ capacityFloor }}</b> 箱
          （= 在库 {{ selectedCap.inStockQty }} + 待入 {{ selectedCap.pendingQty }}
          + 已确认预占 {{ selectedCap.reservedQty }}）
        </div>
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="cap-rule"
          title="下限以后端在库间行锁内的实时汇总为准。把容量改到下限以下（或填负数、空值、绕过页面直连）都会被后端明确驳回，原容量、库存与预占状态保持不变；输入框范围限制仅为操作提示，不是防线。"
        />
        <el-alert
          v-if="selectedCap.defrostOngoing"
          type="error"
          :closable="false"
          show-icon
          class="cap-rule"
          title="该库间化霜占窗进行中：剩余可收箱数按 0（容量数字不变，预占不能确认），化霜结束不自动恢复。"
        />
        <div class="cap-line remain" v-else>
          当前剩余可收 <b>{{ selectedCap.remaining }}</b> 箱
          （容量 {{ selectedCap.capacity }} − 在库 {{ selectedCap.inStockQty }}
          − 待入 {{ selectedCap.pendingQty }} − 已预占 {{ selectedCap.reservedQty }}）
        </div>
      </div>

      <el-alert
        type="info"
        :closable="false"
        title="软删除后：该库间从树中消失（list 自动过滤 deleted=1），但其下货位仍可在「货位」页查询到。"
      />
      <p class="meta">创建：{{ selectedCell.createdAt }}　修改：{{ selectedCell.updatedAt }}</p>
    </div>
    <div class="right empty" v-else>
      <el-empty description="点击左侧库间查看详情" />
    </div>

    <el-dialog v-model="showAdd" title="新增库间" width="420px">
      <el-form label-width="90px">
        <el-form-item label="编号"><el-input v-model="add.code" placeholder="如 A库-07" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="add.name" /></el-form-item>
        <el-form-item label="温区">
          <el-select v-model="add.tempZone" style="width:100%">
            <el-option label="冷冻" value="冷冻" />
            <el-option label="冷藏" value="冷藏" />
          </el-select>
        </el-form-item>
        <el-form-item label="容量(箱)"><el-input-number v-model="add.capacity" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="createCell">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'

const cells = ref([])
const locations = ref([])
const capacities = ref([])
const selectedCell = ref(null)
const showAdd = ref(false)
const saving = ref(false)
const add = ref({ code: '', name: '', tempZone: '冷冻', capacity: 100 })
const form = ref({ code: '', name: '', tempZone: '冷冻', capacity: 0 })

const treeData = computed(() => {
  return cells.value.map(c => ({
    id: 'cell-' + c.id,
    type: 'cell',
    data: c,
    label: `${c.code} ${c.name}（${c.tempZone}·${c.capacity}箱）`,
    children: locations.value
      .filter(l => l.cellId === c.id)
      .map(l => ({
        id: 'loc-' + l.id,
        type: 'loc',
        data: l,
        label: `${l.code} [${l.status}]`
      }))
  }))
})

/** 当前选中库间的容量明细（与容量总览、预占页、批次页同一入口同一口径） */
const selectedCap = computed(() =>
  capacities.value.find(v => v.cellId === selectedCell.value?.id) || null)

/** 容量变更下限：在库 + 待入 + 已确认未核销预占 */
const capacityFloor = computed(() => {
  const v = selectedCap.value
  if (!v) return 0
  return v.inStockQty + v.pendingQty + v.reservedQty
})

async function load() {
  const [c, l, cap] = await Promise.all([
    http.get('/cells'),
    http.get('/locations'),
    http.get('/cells/capacity/overview')
  ])
  cells.value = c
  locations.value = l
  capacities.value = cap
  // 选中态也对齐后端最新值：保存成功或被并发动作驳回后，详情立即用新口径
  if (selectedCell.value) {
    const fresh = c.find(x => x.id === selectedCell.value.id)
    if (fresh) {
      selectedCell.value = fresh
      form.value = {
        code: fresh.code,
        name: fresh.name,
        tempZone: fresh.tempZone,
        capacity: fresh.capacity
      }
    } else {
      selectedCell.value = null
    }
  }
}

function onNodeClick(node) {
  if (node.type === 'cell') {
    selectedCell.value = node.data
    form.value = {
      code: node.data.code,
      name: node.data.name,
      tempZone: node.data.tempZone,
      capacity: node.data.capacity
    }
  }
}

async function saveCell() {
  saving.value = true
  try {
    // 容量是否合法（下限、负数、空值）以后端库间行锁内的实时口径为准：
    // 旧页面、绕过页面直连也由后端硬拦。成功或被驳回都整页刷新库间详情、
    // 容量总览与相关预占数字，绝不留下「提示成功但口径是旧的」的页面。
    await http.put('/cells/' + selectedCell.value.id, { ...form.value })
    ElMessage.success('保存成功，容量总览、预占页与批次页将统一按新容量口径计算')
  } finally {
    saving.value = false
    await load()
  }
}

async function removeCell() {
  await http.delete('/cells/' + selectedCell.value.id)
  selectedCell.value = null
  await load()
}

async function createCell() {
  await http.post('/cells', { ...add.value })
  showAdd.value = false
  add.value = { code: '', name: '', tempZone: '冷冻', capacity: 100 }
  await load()
}

onMounted(load)
</script>

<style scoped>
.cell-page { display: flex; gap: 16px; height: 100%; }
.left {
  width: 320px; background: #fff; border-radius: 10px; padding: 14px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05); display: flex; flex-direction: column;
}
.left-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; font-weight: 700; }
.tree { flex: 1; overflow: auto; }
.right {
  flex: 1; background: #fff; border-radius: 10px; padding: 18px;
  box-shadow: 0 2px 10px rgba(0,0,0,0.05);
}
.right.empty { display: flex; align-items: center; justify-content: center; }
.meta { color: #999; font-size: 12px; margin-top: 12px; }
.cap-box {
  border: 1px solid #d6e8f5;
  background: #f4fafe;
  border-radius: 8px;
  padding: 12px 14px;
  margin: 4px 0 14px;
}
.cap-head { font-weight: 700; color: #0277bd; margin-bottom: 8px; }
.cap-line { color: #444; font-size: 13px; line-height: 1.9; }
.cap-line b { color: #01579b; font-size: 14px; }
.cap-line .floor { color: #e65b2f; font-size: 16px; }
.cap-line.remain { margin-top: 6px; }
.cap-rule { margin-top: 8px; }
</style>
