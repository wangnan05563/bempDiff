// 拖拽「循环填写老包/新包」状态机（纯逻辑，便于单测）。
//
// 状态流转：
//   路径均未填           → 等待「老包」
//   老包已填、新包为空   → 等待「新包」
//   两个均已填（或再拖） → 回到等待「老包」
// 用 self 计数器实现严格的「奇数次填老包、偶数次填新包」，避免仅按填充态推导导致
// 双路径都填后永远指向老包的缺陷。
//
// 用法：
//   const cycle = createDropCycle(() => state.oldPath, () => state.newPath)
//   cycle.peek()  // 查看本次应拖入的包类型（供遮罩提示文案）
//   cycle.claim() // 取出本次目标并推进到下一次；成功落地后才调用

/**
 * @param {()=>string} getOldPath  读取当前老包路径（空串视为未填）
 * @param {()=>string} getNewPath  读取当前新包路径（空串视为未填）
 * @returns {{peek:()=>('old'|'new'), claim:()=>('old'|'new')}}
 */
export function createDropCycle(getOldPath, getNewPath) {
  // null=尚未校准：首次按填充态推导基准；之后每次 take 推进一个奇偶位
  let seq = null

  function calibrate() {
    // 老包空 → 从 0(老包) 开始；老包有、新包空 → 从 1(新包) 开始；都填 → 重置回 0(老包)
    seq = !getOldPath() ? 0 : (!getNewPath() ? 1 : 0)
  }

  function ensure() {
    if (seq === null) calibrate()
  }

  /** 查看下一次应拖入的包类型（不改状态，供遮罩提示）。 */
  function peek() {
    ensure()
    return seq % 2 === 0 ? 'old' : 'new'
  }

  /** 取出本次目标并推进奇偶位（下一次自动切换为另一包类型）。 */
  function claim() {
    ensure()
    const cur = seq % 2 === 0 ? 'old' : 'new'
    seq = (seq !== null ? seq : 0) + 1
    return cur
  }

  return { peek, claim }
}