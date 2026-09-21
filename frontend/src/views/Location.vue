<template>
  <div class="loc-page">
    <div class="head">
      <h2>货位占用热力</h2>
      <el-button type="primary" @click="showAdd = true">新增货位</el-button>
    </div>

    <div class="groups">
      <div v-for="g in groups" :key="g.cell.id" class="group">
        <div class="group-head">
          <span class="gname">{{ g.cell.code }} {{ g.cell.name }}</span>
          <span class="gmeta">{{ g.cell.tempZone }} · 占用 {{ g.used }}/{{ g.total }}</span>
        </div>
        <div class="heat">
          <div
            v-for="l in g.locs"
            :key="l.id"
            class="tile"
            :class="l.status === '占用' ? 'occ' : 'free'"
            :title="l.code + ' · ' + l.status"
          >
            <div class="tcode">{{ l.code }}</div>
            <div class="tstatus">{{ l.status }}</div>
          </div>
          <div v-if="g.locs.length === 0" class="empty">暂无货位</div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showAdd" title="新增货位" width="420px">
      <el-form label-width="90px">
        <el-form-item label="编号"><el-input v-model="add.code" placeholder="如 L-A03-1" /></el-form-item>
        <el-form-item label="归属库间">
          <el-select v-model="add.cellId" style="width:100%">
            <el-option v-for="c in cells" :key="c.id" :label="c.code+' '+c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="add.status" style="width:100%">
            <el-option label="空" value="空" />
            <el-option label="占用" value="占用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import http from '../api'

const cells = ref([])
const locations = ref([])
const showAdd = ref(false)
const add = ref({ code: '', cellId: null, status: '空' })

const groups = computed(() =>
  cells.value.map(c => {
    const locs = locations.value.filter(l => l.cellId === c.id)
    return {
      cell: c,
      locs,
      total: locs.length,
      used: locs.filter(l => l.status === '占用').length
    }
  })
)

async function load() {
  const [c, l] = await Promise.all([http.get('/cells'), http.get('/locations')])
  cells.value = c
  locations.value = l
}

async function create() {
  await http.post('/locations', { ...add.value })
  showAdd.value = false
  add.value = { code: '', cellId: null, status: '空' }
  load()
}

onMounted(load)
</script>

<style scoped>
.loc-page { padding: 4px; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.head h2 { margin: 0; }
.groups { display: flex; flex-direction: column; gap: 16px; }
.group { background: #fff; border-radius: 10px; padding: 14px 16px; box-shadow: 0 1px 8px rgba(0,0,0,0.05); }
.group-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 12px; }
.gname { font-weight: 700; }
.gmeta { color: #888; font-size: 13px; }
.heat { display: flex; flex-wrap: wrap; gap: 10px; }
.tile {
  width: 96px; height: 64px; border-radius: 8px; padding: 8px;
  display: flex; flex-direction: column; justify-content: center; color: #fff;
}
.tile.free { background: #d7eefb; color: #0277bd; border: 1px solid #b6ddef; }
.tile.occ { background: #0277bd; }
.tcode { font-weight: 700; font-size: 14px; }
.tstatus { font-size: 12px; opacity: 0.9; }
.empty { color: #aaa; font-size: 13px; padding: 8px; }
</style>
