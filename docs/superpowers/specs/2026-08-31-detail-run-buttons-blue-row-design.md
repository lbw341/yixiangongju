# 工具详情页按钮统一蓝色并排同一行 + 下载/上传形式一致性 — 设计

日期：2026-08-31

## 背景

1. 工具详情页「工具使用」区域中，下载模板、选择文件、选择文件夹、运行几个按钮样式不一（indigo 实心 / 白色描边），且运行按钮仅在选文件后才出现。用户希望这些按钮更显眼、颜色统一为蓝色、并排在同一行。
2. 下载模板的形式（单文件 / 多文件 / 带目录的文件夹）与用户稍后上传回传的形式不一致：`ScriptRunnerService.run` 的 `sanitizeFileName` 会拍平路径只保留文件名，导致 zip/文件夹上传的子目录结构丢失，脚本无法按固定位置识别回传的模板文件。

## 目标

- 「下载模板」「运行」等按钮更显眼
- 五个按钮（下载脚本模板、下载格式模板、选择文件、选择文件夹、运行）颜色统一为 indigo 蓝，与全站强调色一致
- 五个按钮并排在同一行（窄屏可换行）
- 运行按钮始终显示，未选文件时置灰禁用
- 下载形式与上传回传形式一致：zip/文件夹上传保留目录结构，脚本可按位置读取回传的模板文件
- 前端在模板下载按钮与上传区提供形式提示

## 改动范围

- `frontend/src/views/Detail/index.vue`
- `backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java`
- `backend-java/src/main/java/com/toolplatform/service/ToolService.java`
- `backend-java/src/main/java/com/toolplatform/controller/ToolController.java`

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
| 选择文件（支持压缩包） / 选择文件夹 | `bg-white border border-gray-300 hover:bg-gray-50` | `bg-indigo-500 hover:bg-indigo-600 text-white` |
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

## 下载/上传形式一致性设计

### 现状（不一致点）

- **下载侧**：单模板工具下载 1 个单文件；脚本包模式下载 1 个 zip（管理员上传过 zip 则原样返回，保留目录结构；平铺则实时打包）。
- **上传侧**：`ToolService.processUploadedFiles` 收单文件/多文件/文件夹/zip；`extractZipContent` 只把 text/excel/py 条目放进 `allDataFiles`，且 `ScriptRunnerService.run` 用 `sanitizeFileName` 拍平路径 → 带子目录的 zip 回传后结构丢失。

### 后端：保留目录结构

**`ScriptRunnerService.run`**
- 数据文件按相对路径写入 `workDir/data/` 下：对上传名称按 `/`、`\` 拆组件，逐层用 `sanitizeFileName` 净化后 `resolve` 并创建父目录。
- 目录穿越防护：拒绝绝对路径、`..` 组件、盘符；normalize 后必须 `startsWith(dataDir)`，转义则退回按原名称拍平。
- 传给脚本的参数仍为绝对路径（`sys.argv[2:]` 不变）。

**`ToolService.processUploadedFiles` + `extractZipContent`**
- zip 上传：`allDataFiles` 收录 zip 内**全部条目**（含任意扩展名），保留条目相对路径；文本/Excel 条目继续用于 `allContent`（周报/预览）构建，逻辑不变。py 条目并入同一 `allDataFiles`，不再单独循环。
- 解压防护：条目数上限 500、解压后总字节上限 200MB，超限抛业务异常。
- **上传扩展名规则**：用户上传（非 zip 的直接/文件夹/多文件）从 `ALLOWED_EXT` 白名单改为**可执行文件黑名单**（同 `ScriptPackageService.BLOCKED_EXT`：exe/dll/bat/cmd/ps1/msi/scr/com/jar），保证任何模板文件类型（含 md/docx 等）都能原样回传并按位置读取；黑名单扩展名直接拒绝。文本/Excel 内容抽取仅用于预览与周报回退，不影响数据文件入库。
- 文件夹上传（webkitdirectory）：前端把相对路径写入上传文件名（Chrome 的 `webkitRelativePath`），后端按路径与其它处理一致；单文件上传行为不变。

**`ToolController.toToolMap`**
- 增加 `isPackage` 布尔字段（`packageDir` 非空为 true），供前端渲染形式提示。

### 前端：形式提示

- 「下载脚本模板」按钮文案动态化：package 模式 → `下载脚本模板（压缩包）`，单文件模式 → `下载脚本模板（单文件）`。
- package 模式下按钮附近提示：`模板为压缩包，回传上传请保留目录结构，脚本将按位置读取`。
- 上传区在 package 模式下显示同样提示小字。
- 文件选择框 `accept` 属性与可执行黑名单保持一致（移除 bat/cmd/ps1，避免可选的类型被后端拒绝）。

## 验收标准

1. 五个按钮均呈 indigo 蓝实心、白字
2. 宽屏下五个按钮在同一行
3. 运行按钮始终可见；未选文件时置灰不可点
4. 选中文件后运行按钮可点击；运行时显示「运行中...」
5. package 工具下载脚本模板得到 zip；将该 zip（含子目录）原样回传后，脚本收到的数据目录保留原相对路径结构，`data/xxx/yyy` 可被脚本按位置读取
6. 文件夹方式上传同样保留相对路径结构
7. 单文件工具下载/回传行为不变
8. 前端按钮与上传区展示与实际模板形式一致（压缩包/单文件）
9. 任意非可执行扩展名的文件（如 md/docx）经 zip 或文件夹回传后仍在数据目录相应位置；可执行黑名单扩展名（exe/dll/bat/cmd/ps1/msi/scr/com/jar）被拒绝
10. 其余功能（周报生成、预览、下载、评价等）不受影响