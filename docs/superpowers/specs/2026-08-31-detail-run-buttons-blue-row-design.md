# 工具详情页按钮统一蓝色并排同一行 — 设计

日期：2026-08-31

## 背景

工具详情页「工具使用」区域中，下载模板、选择文件、选择文件夹、运行几个按钮样式不一（indigo 实心 / 白色描边），且运行按钮仅在选文件后才出现。用户希望这些按钮更显眼、颜色统一为蓝色、并排在同一行。

## 目标

- 「下载模板」「运行」等按钮更显眼
- 五个按钮（下载脚本模板、下载格式模板、选择文件、选择文件夹、运行）颜色统一为 indigo 蓝，与全站强调色一致
- 五个按钮并排在同一行（窄屏可换行）
- 运行按钮始终显示，未选文件时置灰禁用

## 改动范围

唯一源码文件：`frontend/src/views/Detail/index.vue`

不动：周报文字输入、进度条、结果产出、Chart、评价、联系方式等其它区域。

## 具体改动

### 布局

将工具使用-文件方式区域的按钮容器

```
<div class="border rounded-lg p-4 grid grid-cols-1 md:grid-cols-2 gap-4 items-center">
```

改为单行 flex：

```
<div class="border rounded-lg p-4 flex flex-wrap items-center gap-4">
```

所有按钮保留 `w-full sm:w-auto`（窄屏全宽、宽屏并排）。

### 按钮配色（统一 indigo）

| 按钮 | 原样式 | 新样式 |
| --- | --- | --- |
| 下载脚本模板 / 下载格式模板 | `bg-indigo-500 hover:bg-indigo-600` | `bg-indigo-500 hover:bg-indigo-600`（不变） |
| 选择文件（压缩包） / 选择文件夹 | `bg-white border border-gray-300 hover:bg-gray-50` | `bg-indigo-500 hover:bg-indigo-600 text-white` |
| 运行 | `bg-indigo-600 hover:bg-indigo-700` | `bg-indigo-600 hover:bg-indigo-700`（不变，主操作保持最饱和） |

统一后 token 组合：`w-full sm:w-auto bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition items-center justify-center inline-flex gap-2`；运行按钮用 `bg-indigo-600 hover:bg-indigo-700`。

### 运行按钮

- 移出 `v-if="pendingFiles.length"` 块，改为始终渲染，置于行尾
- `:disabled="running || !pendingFiles.length"`；未选文件或运行中均禁用
- 未选文件：`opacity-50 cursor-not-allowed` 置灰
- 已选文件：高亮可点
- 状态文案不变：`运行中...` / `运行`
- 点击仍调用 `runTool()`（内部已有登录与空文件校验，可作为兜底）

### 选中文件列表

文件已选列表（`pendingFiles` chips）保留在原容器下方；「运行」按钮从该区块移入顶部的统一按钮行后，列表区仅展示待运行文件 chips。

## 验收标准

1. 五个按钮均呈 indigo 蓝实心、白字
2. 宽屏下五个按钮在同一行
3. 运行按钮始终可见；未选文件时置灰不可点
4. 选中文件后运行按钮可点击；运行时显示「运行中...」
5. 其余功能（周报生成、预览、下载、评价等）不受影响