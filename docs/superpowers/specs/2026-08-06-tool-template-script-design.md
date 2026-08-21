# 工具模板脚本驱动结果生成 — 设计文档

日期：2026-08-06
状态：已确认

## 背景与问题

当前平台的"文件上传→结果生成"流程是完全通用的：
- 工具作者上传工具时附带的"模板文件"只被保存并提供下载（`download_template`），**从不参与结果生成**。
- 使用工具时上传的数据文件，后端一律走固定逻辑（`ToolService.processUploadedFiles`）：
  - 上传的文件里含 `.py` → 执行**用户上传的** .py 脚本（任意用户代码在服务器上运行，存在安全风险）；
  - 否则 → 生成写死的通用周报（`generateWeeklyReport`）。

用户期望：工具作者上传的**脚本模板**应真正被运行，用脚本处理用户上传的数据；并可用**文档格式模板**（含占位符 `{{result}}`）作为最终输出版式。

## 目标

1. 工具可携带两个模板：**脚本模板(.py)** + **文档格式模板**（二者均可选）。
2. 使用工具上传数据时，后端**只运行工具的脚本模板**（不再执行用户上传的 .py）。
3. 若存在文档格式模板，最终结果 = 格式模板中 `{{result}}` 被替换为脚本输出（无脚本时替换为通用周报）；无 `{{result}}` 占位符时，结果内容追加到模板末尾。
4. 无任何模板的工具，行为与现状完全一致（向后兼容）。

## 数据模型

- `Tool` 实体新增字段 `formatTemplate`（String，默认 `""`），对应列 `format_template`。
- 语义调整：
  - `templateFile` → **脚本模板**（通常为 .py）
  - `formatTemplate` → **文档格式模板**（.txt/.md 等文本）
- 项目使用 `spring.jpa.hibernate.ddl-auto=update`（无 Flyway 依赖），实体加字段后 Hibernate 自动建列，无需迁移脚本。
- `docs/DEPLOY.md` / `V1__init_schema.sql` 中的建表参考若提及工具表列，可顺带补 `format_template`（仅文档，不强制）。

## 前端改动

### 上传工具页（`frontend/src/views/ToolUpload/index.vue`）

- 现有单个"上传文件"框改为两个文件框：
  1. **脚本模板(.py)** → `file` 字段
  2. **文档格式模板** → `format_file` 字段
- 两个文件框均允许不选（兼容只传一种或都不传）。
- 提交时 `FormData` 同时携带两个文件字段 + 现有文字字段。

### 管理页编辑（`frontend/src/views/Manage/index.vue` + 后端 update 接口）

- 编辑弹窗新增两个文件框（脚本模板、文档格式模板）+ 展示已上传模板文件名 + 允许清除。
- `PUT /api/tools/{id}/update` 由 `@RequestBody Map`（JSON）改为 **multipart**，携带：
  - 现有文字字段
  - 可选 `file`（新脚本模板，覆盖旧脚本模板）
  - 可选 `format_file`（新格式模板，覆盖旧格式模板）
  - 可选清除标记（如 `clear_template`、`clear_format`，值为 `"1"` 时清空对应字段）
- 前端编辑提交由 JSON 改为 FormData。

### 详情页（`frontend/src/views/Detail/index.vue`）

- 现有"下载模板"按钮按模板存在情况拆分：
  - 有脚本模板（`tool.templateFile` 非空）→ 显示"下载脚本模板" → `/api/tools/{id}/download_template`
  - 有格式模板（`tool.formatTemplate` 非空）→ 显示"下载格式模板" → `/api/tools/{id}/download_format_template`
- 上传文件、结果预览/下载、Python 输出展示流程不变。
- 工具信息接口（`toToolMap`）需返回 `formatTemplate` 字段供前端判断显示。

## 后端改动

### 新增/修改接口（`ToolController.java`）

- `POST /api/tools`（uploadTool）：新增可选 `@RequestParam(value="format_file", required=false) MultipartFile formatFile`，保存到 `formatTemplate`。
- `POST /api/tools/{id}/update`（updateTool）：从 JSON 改为 multipart，支持更换/清除脚本与格式模板。
- 新增 `GET /api/tools/{id}/download_format_template`：与现有 `download_template` 对称，下载格式模板。
- `toToolMap` 增加 `formatTemplate`。

### 处理流程（`ToolService.java`）

重写 `processUploadedFiles(Long toolId, Long userId, MultipartFile[] files)`：

1. 通过 `toolRepo.findById(toolId)` 取工具，读取脚本模板（`templateFile`）与格式模板（`formatTemplate`）。
2. 收集用户上传的数据文件（txt/md/log/json/csv/xlsx/xls + zip 解压内容），写入 `allContent`/`allDataFiles`。
   - **用户上传的 .py 不再执行**，仅作为普通数据文件收集。
3. 若存在脚本模板：
   - 将数据文件写入临时数据目录；
   - 执行 `python -u <脚本路径> <数据目录> <文件1> <文件2> ...`（Python 探测、环境变量、超时与输出截断逻辑由脚本执行组件统一提供，现位于 `ScriptRunnerService`；脚本来源为工具模板路径，数据传递仅用命令行参数，不依赖 stdin）；
   - stdout 为结果内容；脚本执行失败时结果内容带上错误说明。
4. 若无脚本模板：
   - 收集到内容 → 结果内容 = `generateWeeklyReport(allContent)`（现有逻辑）。
5. 若存在格式模板：
   - 读取模板文本；
   - 含 `{{result}}` → 全部替换为结果内容；
   - 不含占位符 → 结果内容追加到模板末尾；
   - 无格式模板 → 结果内容直接作为结果。
6. 结果写入 `resultDir`，返回 `FileProcessResult`（`result_file`、`processed_files`、可选 `python_output`）。

### 脚本模板执行细节

- Python 命令探测、`PYTHONIOENCODING=utf-8` / `PYTHONUTF8=1` 环境变量、超时与输出截断逻辑由 `ScriptRunnerService` 统一提供（见 `2026-08-21-script-runner-refactor-design.md`）。
- 数据文件统一写入临时目录，文件路径作为 argv 传入脚本：`python -u <script> <dataDir> [file1] [file2] ...`；**数据目录参数恒定传入**（即使无数据文件）。
- 脚本约定写入使用说明：`sys.argv[1]` 恒为数据目录，`sys.argv[2:]` 为文件路径列表（可为空），结果打印到 stdout。

### 兼容性

- 旧工具 `templateFile` 以 `.py` 结尾 → 视为脚本模板；否则视为格式模板（读取逻辑按此兜底判断）。
- 无模板工具 → 收集内容 → 通用周报，与现状一致。

## 安全

- 本设计移除了"随数据文件上传 `.py` 即被偶然执行"的路径，但**并未消除任意代码执行**：任何已登录用户仍可注册带 `.py` 脚本模板的工具，服务器会以服务器权限在其被使用时不加沙箱地执行该脚本。因此工具创建属于既定的可信用户边界，而非"无任意代码执行"。
- 脚本在独立临时工作目录内运行，沿用现有隔离方式。

## 测试

- 上传带"脚本模板 + 格式模板"的工具 → 使用它上传数据文件 → 结果 = 格式模板文本中 `{{result}}` 被脚本 stdout 替换。
- 仅脚本模板 → 结果 = 脚本 stdout。
- 仅格式模板 → 结果 = 模板中 `{{result}}` 被通用周报替换。
- 格式模板无 `{{result}}` → 结果 = 模板 + 追加的结果内容。
- 无模板 → 现有行为不变。
- 编辑工具更换/清除模板后，详情页下载按钮与处理行为随之更新。

## 涉及文件清单

后端：
- `backend-java/src/main/java/com/toolplatform/entity/Tool.java`
- `backend-java/src/main/java/com/toolplatform/controller/ToolController.java`
- `backend-java/src/main/java/com/toolplatform/service/ToolService.java`

前端：
- `frontend/src/views/ToolUpload/index.vue`
- `frontend/src/views/Manage/index.vue`
- `frontend/src/views/Detail/index.vue`

文档（可选）：
- `backend-java/src/main/resources/db/migration/V1__init_schema.sql`（补 `format_template` 列参考）
- `docs/DEPLOY.md`（如提及上传/模板接口，顺带更新）
