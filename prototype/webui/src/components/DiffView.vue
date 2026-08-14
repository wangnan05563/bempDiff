<script setup>
import { computed, ref } from 'vue'
import { state } from '../store'

const STATUS_LABEL = { ADDED: '新增', DELETED: '删除', MODIFIED: '修改', UNCHANGED: '未变' }
const STATUS_CLS = { ADDED: 'text-bg-success', DELETED: 'text-bg-danger', MODIFIED: 'text-bg-warning', UNCHANGED: 'text-bg-secondary' }

const wrap = ref(true) // true=换行（默认，完整展示长行）；false=不换行（横向滚动，BCompare 风格）

const dec = computed(() => state.decompile)
const node = computed(() => {
  if (!state.job || !state.selectedKey) return null
  return state.job.tree.find(n => n.key === state.selectedKey) || null
})

// 把后端返回的 unified diffText 解析成左右对齐的行（含行号）。
// 单滚动容器 + 每行 4 列网格 => 左右天然同步滚动且逐行对齐（add/del 空白侧自动等高）。
const rows = computed(() => {
  const d = dec.value
  if (!d || !d.diffText) return []
  const out = []
  let leftNo = 0, rightNo = 0
  for (const raw of d.diffText.split('\n')) {
    if (raw.startsWith('@@') || raw.startsWith('\\ No newline') ||
        raw.startsWith('--- ') || raw.startsWith('+++ ')) continue
    if (raw.startsWith('-')) {
      leftNo++
      out.push({ type: 'del', left: '' + leftNo, leftText: raw.slice(1), right: '', rightText: '' })
    } else if (raw.startsWith('+')) {
      rightNo++
      out.push({ type: 'add', left: '', leftText: '', right: '' + rightNo, rightText: raw.slice(1) })
    } else if (raw.startsWith(' ')) {
      leftNo++; rightNo++
      out.push({ type: 'ctx', left: '' + leftNo, leftText: raw.slice(1), right: '' + rightNo, rightText: raw.slice(1) })
    } else {
      out.push({ type: 'ctx', left: '', leftText: raw, right: '', rightText: raw })
    }
  }
  return out
})
</script>

<template>
  <div class="col-center">
    <div class="filebar">
      <i class="bi bi-file-earmark-code"></i>
      <span class="path">{{ node ? node.key : '未选择文件' }}</span>
      <span v-if="node" class="badge" :class="STATUS_CLS[node.status]">{{ STATUS_LABEL[node.status] }}</span>
      <span class="badge text-bg-light border" v-if="node">{{ node.fileClass }}</span>
      <span class="ms-auto text-secondary me-2" style="font-size:.75rem">
        反编译引擎：{{ dec ? dec.engine : '—' }}
      </span>
      <button class="btn btn-sm btn-outline-secondary py-0 px-2" style="font-size:.72rem"
              @click="wrap = !wrap" :title="wrap ? '当前：自动换行，点击切换为不换行（横向滚动）' : '当前：不换行，点击切换为自动换行'">
        <i class="bi" :class="wrap ? 'bi-text-wrap' : 'bi-text-paragraph'"></i>
        {{ wrap ? '换行' : '不换行' }}
      </button>
    </div>

    <div class="diff-area" :class="{ nowrap: !wrap }" v-if="node && dec && dec.ok">
      <div class="diff-grid">
        <div v-for="(r, i) in rows" :key="i" class="row" :class="r.type">
          <span class="ln">{{ r.left }}</span><span class="code flex-1">{{ r.leftText }}</span>
          <span class="ln">{{ r.right }}</span><span class="code flex-1">{{ r.rightText }}</span>
        </div>
      </div>
    </div>

    <div class="diff-area center-empty" v-else-if="node && dec && !dec.ok">
      <div class="ico"><i class="bi bi-exclamation-triangle"></i></div>
      <div>该文件无法反编译（引擎：{{ dec.engine }}）</div>
      <div v-if="dec.diffText" class="text-start mt-2" style="white-space:pre-wrap;font-size:.8rem">{{ dec.diffText }}</div>
    </div>

    <div class="diff-area center-empty" v-else>
      <div class="ico"><i class="bi bi-columns-gap"></i></div>
      <div>从左侧差异树选择一个 <b>修改 / 新增 / 删除</b> 的文件查看双栏源码比对</div>
    </div>
  </div>
</template>

<style scoped>
.diff-area { overflow: auto; flex: 1 1 auto; min-height: 0; background: var(--bs-body-bg); }
.diff-area.nowrap .diff-grid { width: max-content; min-width: 100%; }
.diff-grid { display: block; }
.row {
  display: grid;
  grid-template-columns: 3.2rem 1fr 3.2rem 1fr;
  gap: 0;
  align-items: stretch;
  border-bottom: 1px solid var(--bs-border-color);
}
.row:hover { background: var(--bs-tertiary-bg); }
.row .ln {
  text-align: right; padding: 0 .4rem;
  color: var(--bs-secondary-color);
  background: var(--bs-tertiary-bg);
  user-select: none;
  border-right: 1px solid var(--bs-border-color);
}
.row .code {
  padding: 0 .6rem;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--bs-font-monospace);
  font-size: .82rem;
  line-height: 1.5;
}
.diff-area.nowrap .row .code { white-space: pre; }
.row.add { background: var(--bs-success-bg-subtle); }
.row.add .ln:last-of-type { border-left: 1px solid var(--bs-border-color); }
.row.del { background: var(--bs-danger-bg-subtle); }
</style>
