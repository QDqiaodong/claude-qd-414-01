<template>
  <el-container class="app">
    <el-header class="topbar">
      <div class="brand">❄ 冷库管理系统</div>
      <div
        class="mega"
        @mouseenter="open = true"
        @mouseleave="open = false"
      >
        <button class="mega-trigger" :class="{ active: open }" @click="open = !open">
          冷库管理 <span class="caret">▾</span>
        </button>
        <div v-show="open" class="mega-panel">
          <div class="mega-group" v-for="g in groups" :key="g.title">
            <div class="mega-group-title">{{ g.title }}</div>
            <router-link
              v-for="m in g.items"
              :key="m.path"
              :to="m.path"
              class="mega-item"
              @click="open = false"
            >
              <span class="dot" :style="{ background: m.color }"></span>
              {{ m.label }}
            </router-link>
          </div>
        </div>
      </div>
      <div class="spacer"></div>
      <div class="hint">鼠标悬停或点击「冷库管理」展开模块</div>
    </el-header>
    <el-main class="content">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'

const open = ref(false)
const groups = [
  {
    title: '基础档案',
    items: [
      { path: '/cells', label: '库间', color: '#0277bd' },
      { path: '/locations', label: '货位', color: '#039be5' }
    ]
  },
  {
    title: '运营业务',
    items: [
      { path: '/batches', label: '入库批次', color: '#0288d1' },
      { path: '/reservations', label: '入库预占', color: '#26a69a' },
      { path: '/defrost-windows', label: '化霜占窗', color: '#ef6c00' },
      { path: '/inspections', label: '巡检', color: '#01579b' }
    ]
  }
]
</script>

<style>
html, body, #app { margin: 0; height: 100%; }
.app { height: 100vh; }
.topbar {
  position: relative;
  display: flex;
  align-items: center;
  background: #0277bd;
  color: #fff;
  padding: 0 20px;
}
.brand { font-weight: 700; font-size: 18px; margin-right: 28px; white-space: nowrap; }
.mega { position: relative; }
.mega-trigger {
  background: rgba(255,255,255,0.12);
  color: #fff;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 15px;
  cursor: pointer;
}
.mega-trigger.active { background: rgba(255,255,255,0.25); }
.caret { font-size: 12px; }
.mega-panel {
  position: absolute;
  top: 46px;
  left: 0;
  display: flex;
  gap: 24px;
  background: #fff;
  color: #333;
  border-radius: 10px;
  box-shadow: 0 8px 30px rgba(0,0,0,0.18);
  padding: 16px 20px;
  z-index: 2000;
  min-width: 360px;
}
.mega-group { display: flex; flex-direction: column; min-width: 140px; }
.mega-group-title {
  font-size: 12px;
  color: #0277bd;
  font-weight: 700;
  margin-bottom: 8px;
  border-bottom: 1px solid #e3f0f7;
  padding-bottom: 4px;
}
.mega-item {
  display: flex;
  align-items: center;
  padding: 7px 10px;
  border-radius: 6px;
  color: #333;
  text-decoration: none;
  font-size: 14px;
}
.mega-item:hover { background: #e8f4fb; }
.mega-item.router-link-active { background: #e8f4fb; color: #0277bd; font-weight: 700; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 8px; }
.spacer { flex: 1; }
.hint { font-size: 12px; opacity: 0.8; white-space: nowrap; }
.content { background: #f5f8fa; padding: 20px; }
</style>
