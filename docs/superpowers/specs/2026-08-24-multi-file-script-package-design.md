# 多文件脚本包与依赖预装 — 设计文档

日期：2026-08-24
状态：已确认

## 背景与问题

脚本模板当前只支持上传单个 `.py` 文件，真实场景存在三个缺口：

1. **多文件脚本**：作者的脚本往往不止一个文件（工具模块、`requirements.txt`），或带目录结构；单文件输入框重复选择会覆盖前次选择，无法表达"一组文件"。
2. **依赖安装**：第三方依赖无处安装，脚本 `import` 即失败。
3. **交互**：前端 `<input type="file">` 未开 `multiple`，一次只能选一个文件。

## 已确认的决策

| 决策点 | 结论 |
|---|---|
| 依赖安装时机 | 上传时预装到该工具独立 venv；失败则工具创建失败并返回 pip 输出尾部 |
| 打包方式 | 双模式：多选文件平铺上传，或单个 `.zip`（支持目录结构） |
| 入口确定 | 约定链自动探测：`main.py` > `app.py` > `run.py` > 根目录唯一 `.py`；全部落空则创建失败并提示 |

## 目标

1. 工具作者可一次性上传完整脚本项目（多文件或 zip 包，含目录结构与 `requirements.txt`）。
2. 带 `requirements.txt` 的包在创建时自动获得独立虚拟环境，执行时自动使用。
3. 单 `.py` 存量工具行为零变化。
4. 下载入口对包模式可用（拿到原始 zip 或服务端打包的 zip）。

## 非目标

- 文件夹拖拽直接上传（webkitdirectory）
- pip 私有镜像 / index-url 配置化（沿用机器默认 pip 配置）
- 跨工具依赖缓存、依赖版本锁定解析
- 执行期动态安装依赖
- 沙箱隔离（沿用既有信任模型：工具创建者可信）

## 数据模型

`Tool` 实体新增两列（`ddl-auto=update` 自动建列，无迁移脚本）：

- `packageDir`（String，默认 `""`）：非空表示使用脚本包模式，值为 `uploads/script_packages/` 下的相对目录名（即 `<toolId>`）
- `entryFile`（String，默认 `""`）：入口脚本在 `payload/` 内的相对路径

存量工具两列均为空且 `templateFile` 以 `.py` 结尾 → 继续走单文件路径。

## 存储布局

```
uploads/script_packages/<toolId>/
  ├─ payload/          # 完整脚本项目（解压或平铺落盘，保留目录结构）
  └─ original.zip      # 仅 zip 模式：原始上传包，供详情页下载
uploads/venvs/<toolId>/        # 该工具独立 venv（仅当存在 requirements.txt 时创建）
```

## 后端设计

### 新组件 `ScriptPackageService`

职责：把"一次上传"落成合法的工具脚本包 + 可用解释器环境。不感知 HTTP。

`public PackageInstallResult install(Long toolId, List<MultipartFile> files)`：

1. **模式判定**：恰好一个文件且以 `.zip` 结尾 → zip 模式；否则平铺模式。zip 与其他文件混传 → 拒绝（400 语义，见错误约定）。平铺模式内重名文件 → 拒绝并提示冲突文件名。
2. **扩展名防线**：成员（平铺文件名或 zip 条目名）命中可执行黑名单 `exe/dll/bat/cmd/ps1/msi/scr/com/jar` → 拒绝。其余扩展名放行。
3. **zip 解压防线**（本功能自带的必要安全措施）：
   - 条目名 normalize 后必须仍位于 payload 目录内（防 zip-slip）；拒绝绝对路径 / 盘符路径条目
   - 条目数 ≤ `MAX_PACKAGE_ENTRIES`(500)；解压后总字节数 ≤ `MAX_PACKAGE_BYTES`(200MB)
4. **入口探测**：在 payload 根依次找 `main.py`、`app.py`、`run.py`，再找根目录唯一 `.py`；全部落空 → 失败，提示：`无法确定入口脚本：请在包根目录提供 main.py / app.py / run.py，或确保只有一个根级 .py 文件`
5. **依赖安装**：payload 根存在 `requirements.txt` 时：
   - `<python> -m venv uploads/venvs/<toolId>`（python 取 `ScriptRunnerService.findPythonCommand()` 开放为 public 后的缓存探测结果，避免两份探测逻辑）
   - `<venv-python> -m pip install -r <requirements> --disable-pip-version-check`
   - 超时 `PIP_TIMEOUT_SECONDS`(300) 秒强杀；venv 解释器路径 Windows 取 `Scripts\python.exe`、类 Unix 取 `bin/python`
   - 失败 → 删除半成品 venv 与包目录，携带 pip 输出尾部（最多约 2000 字符）报错
   - 无 `requirements.txt` → 不建 venv，执行期回退系统 Python
6. **替换语义**：安装前若已存在旧包目录 / 旧 venv，先删除（整包替换）。
7. 返回 `PackageInstallResult { packageDir, entryFile, displayName }`；`displayName` 用于 `templateFile` 列展示（zip 原名或首个 .py 名）。

### Controller 改动（`ToolController.java`）

- `POST /api/tools` 与 `PUT /api/tools/{id}/update` 各新增可选参数 `files`（`MultipartFile[]`）。旧 `file` 参数保留：仅当 `files` 缺席时按现状处理（兼容老调用方）。
- `files` 存在 → 调用 `ScriptPackageService.install`，成功则写 `packageDir`/`entryFile`/`templateFile(展示名)`；失败返回 400，body `{"error": "<摘要>", "install_log": "<pip/解压输出尾部>"}`。
- `clear_template=1` → 同时删除包目录与 venv，清空三列。

### 执行链路（`ToolService.processUploadedFiles`）

脚本路径解析分支：

```java
if (tool.getPackageDir() != null && !tool.getPackageDir().isEmpty()) {
    scriptPath = packagesRoot.resolve(tool.getPackageDir()).resolve("payload").resolve(tool.getEntryFile());
    interpreter = resolveInterpreter(tool); // venv 优先，回退 findPythonCommand()
}
else if (templateFile.endsWith(".py")) { /* 现状不变 */ }
```

`ScriptRunnerService.run(scriptPath, dataFiles)` 本身零改动。

### 下载（`GET /api/tools/{id}/download_template`）

- 包模式且有 `original.zip` → 直接下发原包
- 包模式平铺 → 服务端将 `payload/` 现场打成 zip 下发
- 存量单文件 → 现状不变

## 前端设计

### 上传页（`frontend/src/views/ToolUpload/index.vue`）

- 脚本模板输入框：`multiple`，`accept=".py,.txt,.zip"`；选中后渲染文件 chips 列表（逐项可移除，可整体清空重选）
- 客户端校验：zip 只能单独选；黑名单扩展名即时提示
- 提交：`files.forEach(f => fd.append('files', f))`（不再用 `file` 字段）
- 说明文案更新：支持多文件/zip 包，含 requirements.txt 时自动安装依赖；执行约定 `sys.argv[1]`=数据目录、`sys.argv[2:]`=文件列表 保持不变

### 管理页编辑（`frontend/src/views/Manage/index.vue`)

- 脚本模板单选框替换为同样的多选控件；语义为**整包替换**（提示文案注明）
- `clear_template` 行为不变，后端负责连带清理 venv

### 详情页

- 无 UI 变更；下载按钮由后端分支保证拿到 zip

## 错误约定（用户可见文案）

- 混传拒绝：`zip 包必须单独上传，不能与其他文件同时选择`
- 扩展名拒绝：`不允许的可执行文件: <name>`
- 入口缺失：见上文第 4 步原文
- 依赖安装失败：`依赖安装失败，请检查 requirements.txt` + pip 输出尾部（`install_log` 字段与 toast 展示）

## 兼容性

- 存量单 `.py` 工具：上传/执行/下载全链路不变
- 旧客户端直调 API 用 `file` 参数：行为不变
- argv 执行契约不变：`sys.argv[1]`=数据目录、`sys.argv[2:]`=数据文件列表

## 测试

E2E（复用既有 API 验证流程）：

1. **存量兼容**：单 `.py` 工具创建→使用→输出正确（回归）
2. **平铺多选**：`main.py` + `utils.py` + `requirements.txt`（含一个小型纯轮子依赖，如 `six`）→ 创建成功、venv 生成、执行时 import 成功
3. **zip 结构**：zip 内含子目录与跨文件 import → 解压布局正确、入口探测正确、执行成功
4. **入口缺失**：两个无关根级 `.py` 且无约定名 → 400 + 指定提示文案
5. **依赖失败**：`requirements.txt` 含不存在的包名 → 400 + `install_log` 含 pip 报错尾部，且半成品目录被清理
6. **下载**：zip 模式下载得到原包；平铺模式下载得到合法 zip

## 涉及文件清单

后端：
- `backend-java/src/main/java/com/toolplatform/entity/Tool.java`（加两列）
- `backend-java/src/main/java/com/toolplatform/service/ScriptPackageService.java`（新增）
- `backend-java/src/main/java/com/toolplatform/service/ToolService.java`（执行分支、下载分支、清理逻辑、packagesRoot/venvsRoot 配置绑定）
- `backend-java/src/main/java/com/toolplatform/controller/ToolController.java`（files 参数、400 错误体、clear_template 连带清理）
- `backend-java/src/main/resources/application.properties`（`upload.script-package-dir`、`upload.venv-dir`）

前端：
- `frontend/src/views/ToolUpload/index.vue`
- `frontend/src/views/Manage/index.vue`

文档：
- `docs/DEPLOY.md`（新存储目录说明，如提及）
