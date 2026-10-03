<script setup>
// 设置页「帮助文档」tab：集中展示功能模块的体系化说明。
// 布局：顶部（标题+版本+搜索）｜左侧目录导航｜右侧内容详情。
// 数据源为纯数据模块 lib/helpContent.js（层级化：分类→条目→结构化内容块），
// 检索/版本同步逻辑也集中在数据模块，便于单测，组件只做渲染与交互。
import { ref, computed, watch } from 'vue'
import {
  CATEGORIES, HELP_ENTRIES, searchHelp, resolveAppVersion, contentMatchesVersion,
  DOC_VERSION, PRODUCT_NAME
} from '../lib/helpContent'
import { setGuideButtonVisible, isGuideButtonVisible } from '../lib/guide'
import { t } from '../lib/i18n'

// 分类标题取 i18n：有 titleKey 走 t()，否则回退清单里的中文原文
const catTitle = (g) => (g && g.titleKey ? t(g.titleKey) : (g ? g.title : ''))

const query = ref('')
const activeId = ref(HELP_ENTRIES[0] ? HELP_ENTRIES[0].id : '')

const appVersion = resolveAppVersion()
const synced = computed(() => contentMatchesVersion(DOC_VERSION, appVersion))

// 引导入口开关：开启后显示左下角常驻「引导」按钮并立即唤起引导演示。
// 默认关闭（按钮隐藏），用户在此处显式开启/再关闭，符合「引导按钮默认隐藏」的要求。
const guideBtnOn = ref(isGuideButtonVisible())
function toggleGuideButton() {
  setGuideButtonVisible(!guideBtnOn.value)
  guideBtnOn.value = isGuideButtonVisible()
  if (guideBtnOn.value) {
    // 开启后立即唤起引导（GuideOverlay 监听同名事件）
    window.dispatchEvent(new Event('bempdiff:start-guide'))
  }
}

// 目录分组：无检索时全量分类；有检索时仅保留命中的分类与条目。
const groups = computed(() => {
  const q = query.value.trim()
  if (!q) {
    return CATEGORIES.map((c) => ({ ...c, entries: c.entries }))
  }
  const hitIds = new Set(searchHelp(q).map((h) => h.id))
  return CATEGORIES
    .map((c) => ({ ...c, entries: c.entries.filter((e) => hitIds.has(e.id)) }))
    .filter((c) => c.entries.length)
})

const activeEntry = computed(() =>
  HELP_ENTRIES.find((e) => e.id === activeId.value) || HELP_ENTRIES[0] || null)
const hitCount = computed(() => (query.value.trim() ? searchHelp(query.value).length : 0))

// 切换条目后内容区回到顶部，保证从长文底部点导航也能看到新条目开头。
const contentEl = ref(null)
watch(activeId, () => { if (contentEl.value) contentEl.value.scrollTop = 0 })
function scrollTop() { if (contentEl.value) contentEl.value.scrollTop = 0 }
</script>

<template>
  <div class="helpdoc">
    <!-- 顶部：标题 + 版本标注 + 搜索 -->
    <div class="helpdoc-head">
      <div class="d-flex align-items-center gap-2 flex-wrap">
        <i class="bi bi-question-circle-fill text-primary"></i>
        <span class="fw-semibold">{{ t('hd.title') }}</span>
        <span class="badge text-bg-light border" :title="t('hd.appVer')">{{ PRODUCT_NAME }} v{{ appVersion }}</span>
        <span class="badge text-bg-light border" :title="t('hd.docVer')">{{ t('hd.docVerShort') }} v{{ DOC_VERSION }}</span>
        <span v-if="!synced" class="badge text-bg-warning" :title="t('hd.staleTip')">{{ t('hd.stale') }}</span>
        <button class="btn btn-sm ms-auto" :class="guideBtnOn ? 'btn-primary' : 'btn-outline-secondary'"
                type="button" @click="toggleGuideButton"
                :title="t('hd.guideTip')">
          <i class="bi bi-life-preserver me-1"></i>{{ guideBtnOn ? t('hd.guideOn') : t('hd.guideOff') }}
        </button>
      </div>
      <div class="input-group input-group-sm helpdoc-search">
        <span class="input-group-text"><i class="bi bi-search"></i></span>
        <input class="form-control" type="text" v-model.trim="query"
               :placeholder="t('hd.searchPlaceholder')" :aria-label="t('hd.searchAria')">
        <button v-if="query" class="btn btn-outline-secondary" type="button" :title="t('hd.searchClear')"
                @click="query = ''"><i class="bi bi-x-lg"></i></button>
      </div>
    </div>

    <!-- 主体：左导航 + 右详情 -->
    <div class="helpdoc-body">
      <!-- 目录导航 -->
      <div class="helpdoc-nav">
        <template v-if="groups.length">
          <div v-for="g in groups" :key="g.key" class="hm-group">
            <div class="hm-group-title" :title="catTitle(g)">
              <i class="bi" :class="g.icon"></i> {{ catTitle(g) }}
              <span class="text-secondary hm-count">{{ g.entries.length }}</span>
            </div>
            <button v-for="e in g.entries" :key="e.id" type="button"
                    class="list-group-item list-group-item-action py-1 hm-item"
                    :class="{ active: activeId === e.id }"
                    :title="e.intro || e.title"
                    @click="activeId = e.id">
              <span class="hm-item-title">{{ e.title }}</span>
            </button>
          </div>
        </template>
        <div v-else class="text-secondary px-2 py-3 text-center" style="font-size:.78rem">
          {{ t('hd.noHit', { q: query }) }}
        </div>
      </div>

      <!-- 内容详情 -->
      <div class="helpdoc-content" ref="contentEl">
        <template v-if="activeEntry">
          <div class="d-flex align-items-center gap-2 mb-1">
            <span class="badge text-bg-light border">{{ activeEntry.categoryTitle }}</span>
            <span class="text-secondary" style="font-size:.72rem" v-if="query">{{ t('hd.hitCount', { n: hitCount }) }}</span>
          </div>
          <h5 class="hm-h">{{ activeEntry.title }}</h5>
          <p class="text-body-secondary hm-intro">{{ activeEntry.intro }}</p>

          <template v-for="(b, bi) in activeEntry.blocks" :key="bi">
            <p v-if="b.type === 'para'" class="mb-2">{{ b.text }}</p>
            <ul v-else-if="b.type === 'list'" class="mb-2">
              <li v-for="(it, ii) in b.items" :key="ii">{{ it }}</li>
            </ul>
            <ol v-else-if="b.type === 'steps'" class="mb-2 ps-3">
              <li v-for="(it, ii) in b.items" :key="ii">{{ it }}</li>
            </ol>
            <div v-else-if="b.type === 'note'"
                 class="alert alert-info py-1 px-2 mb-2 hm-hint">
              <i class="bi bi-info-circle-fill me-1"></i>{{ b.text }}
            </div>
            <div v-else-if="b.type === 'warn'"
                 class="alert alert-warning py-1 px-2 mb-2 hm-hint">
              <i class="bi bi-exclamation-triangle-fill me-1"></i>{{ b.text }}
            </div>
          </template>
        </template>
        <div v-else class="text-secondary py-4 text-center">{{ t('hd.pickEntry') }}</div>
      </div>
    </div>

    <!-- 回到顶部（便于长文导航） -->
    <button v-if="activeEntry && activeEntry.blocks.length" type="button"
            class="btn btn-sm btn-outline-secondary hm-backtop"
            :title="t('hd.backTop')" @click="scrollTop">
      <i class="bi bi-arrow-up"></i>
    </button>
  </div>
</template>

<style scoped>
.helpdoc {
  display: flex;
  flex-direction: column;
  position: relative; /* 供「回到顶部」按钮相对定位，避免锚到模态框外部 */
  height: 100%;
  min-height: 420px;
  gap: .5rem;
  color: var(--bs-body-color);
}
.helpdoc-head {
  border-bottom: 1px solid var(--bs-border-color);
  padding-bottom: .5rem;
  display: flex;
  flex-direction: column;
  gap: .4rem;
}
.helpdoc-search { max-width: 22rem; }
.helpdoc-body {
  display: flex;
  gap: .75rem;
  flex: 1 1 auto;
  min-height: 0;
}
.helpdoc-nav {
  width: 13rem;
  flex: 0 0 13rem;
  overflow-y: auto;
  border-right: 1px solid var(--bs-border-color);
  padding-right: .4rem;
  max-height: 520px;
}
.helpdoc-content {
  flex: 1 1 auto;
  min-width: 0;
  overflow-y: auto;
  max-height: 520px;
  font-size: .82rem;
  line-height: 1.55;
}
.hm-group-title {
  font-size: .72rem;
  font-weight: 600;
  color: var(--bs-secondary-color);
  padding: .35rem .25rem .15rem;
  text-transform: uppercase;
  letter-spacing: .02em;
}
.hm-count { font-weight: 400; font-size: .68rem; }
.hm-item {
  border: 0;
  border-left: 2px solid transparent;
  font-size: .8rem;
  color: var(--bs-body-color);
  background: transparent;
  text-align: left;
}
.hm-item.active,
.hm-item:hover {
  border-left-color: var(--bs-primary);
  background: var(--bs-primary-bg-subtle);
  color: var(--bs-primary);
}
.hm-item-title { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hm-h { font-size: 1rem; margin-bottom: .25rem; }
.hm-intro { font-size: .82rem; }
.hm-hint { font-size: .78rem; }
.hm-backtop {
  position: absolute;
  right: 1.25rem;
  bottom: 3.5rem;
  padding: 0 .45rem;
  line-height: 1.4;
  color: var(--bs-secondary-color);
}
</style>