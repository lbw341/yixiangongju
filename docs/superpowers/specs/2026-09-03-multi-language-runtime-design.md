# 多语言运行时（Python / Node / Java 统一输出）— 设计文档

日期：2026-09-03
状态：已确认（设计认可）

## 背景与问题

平台当前脚本执行层硬绑定 Python：`ScriptRunnerService` 只探测/调用 Python 解释器，`ScriptPackageService` 只识别 `main.py/app.py/run.py` 入口与 `requirements.txt` 依赖。结果：**不同作者只能用 Python 写工具**。

需求：不同作者可用不同编程语言（Python / Node.js / Java）编写工具，收入平台后要求**统一输出模式**——平台对用户透明、以同一套输入输出契约运行工具、收拢结果。

已核实服务端环境具备现成运行时（无需部署 Docker）：
- Python 3.11.4（含 pandas 3.0.5 + openpyxl）
- Node v24.18.0 + npm 11.16.0
- JDK 21（java）

没有 Docker、Go、gcc → 本期采用**进程级执行**（内部可信模型），不用容器沙箱。

## 已确认的决策

| 决策点 | 结论 |
|---|---|
| 支持语言 | Python + Node.js + Java（JVM）；Go/C++/Shell 本期不支持 |
| 统一输出契约 | 文件进/文件出：数据目录 + 文件列表传工具；工具写结果到 RESULT_DIR 或 stdout；平台统一收拢生成 result 文件与多文件打包 |
| 信任与隔离 | 内部可信，进程级执行（复用现有超时/输出上限/临时目录清理，不引入 Docker） |
| 运行时声明 | 平台配置 `runtime` 字段（python/node/java），依赖按约定自动识别（requirements.txt / package.json / lib/*.jar） |
| 实施方案 | 方案 A：运行时策略抽象（ToolRunner 接口 + 各语言实现） |
| 结果落盘 | 运行后扫描 RESULT_DIR，存在输出文件则打包进 uploads/results/；否则回退 stdout 文本 |
| Java 依赖 | 仅支持包内 `lib/*.jar` 拷贝到 classpath；本期不支持 Maven/Gradle 联网构建 |

## 目标

1. 工具作者可用 Python / Node / Java 任意一种写工具，统一经受同一套 I/O 契约。
2. 平台以统一方式探测运行时、安装依赖、执行、收拢结果；运维对语言差异透明。
3. 存量 Python 工具零行为变化（`runtime` 缺省 python，argv 语义不变）。
4. 每种语言至少一个端到端冒烟（Python 沿用现有 tool50；新增 Node/Java 冒烟）。

## 非目标

- Go / C++ / Shell / Ruby / PHP 等其它语言（预留 ToolRunner 扩展点即可接入）
- Docker / 容器沙箱隔离（未来演进目标，本期在 ToolRunner 接口预留容器化扩展位）
- Java 源码 `.java` 即时编译运行（`javac`）——本期 Java 仅支持已编译 `jar` 入口
- Java Maven/Gradle 依赖解析（仅 lib/*.jar 本地拷贝；Service 端未装 Maven）
- pip / npm 私有镜像 index 配置化（沿用机器默认配置）
- 执行期动态安装依赖（保持上传时预装模型）

## 数据模型

`Tool` 实体新增 `runtime` 列（`ddl-auto=update` 自动建列，无迁移脚本）：

- `runtime`（String，默认 `"python"`）取值 `python` | `node` | `java`

存量工具缺省 `python`，行为与现在完全一致。

API：
- `POST /api/tools` 请求体可携带 `runtime`，缺省 `python`（字段非必填）
- `PUT /api/tools/{id}/update` 可改 `runtime`
- 列表 / 详情响应均含 `runtime`

前端：创建/编辑表单新增**运行时下拉**（Python/Node/Java）；工具详情页显示语言徽章。数据文件上传的 accept 与语言无关，维持现状。

## 存储布局

```
uploads/script_packages/<toolId>/payload/**   # 通用：所有语言共用（原 python 专用）
uploads/venvs/<toolId>/                        # python：已有，不变
uploads/nodepkgs/<toolId>/                     # node：npm install 产物（或直接留在 payload 内 node_modules）
uploads/jarpkgs/<toolId>/                      # java：lib/*.jar 拷贝的 classpath 产物
uploads/results/                               # 现有：运行结果（stdout 文本 / 打包结果）
```

Java 的 classpath 组装：`<payload>;<payload>/lib/*`（Windows）或 `<payload>:<payload>/lib/*`（类 Unix）；jar 入口则 `java -jar <payload>/<entry.jar>`。

## 统一 I/O 契约（三语一致，纯增量，不破坏存量）

```
argv[1]           = DATA_DIR（数据目录绝对路径，恒存在，即使无文件）
argv[2:]          = 数据文件绝对路径列表（可为空）
env DATA_DIR      = 同 argv[1]
env INPUT_FILES   = 数据文件绝对路径的 JSON 数组
env RESULT_DIR    = 结果输出目录（工具推荐写入结果文件于此）
stdout            = 结果文本（平台统一截断到 5000 行 / 1MB）
exit 0 = 成功；非 0 = 失败（失败时 stdout 前若干行作为错误信息）
```

对现有 Python 工具的破坏性：**零**。Python runner 的命令构建保持 `python -u <script> <dataDir> [files...]` 原样，仅额外注入 3 个环境变量，`sys.argv` 语义不变。

### 各语言命令

| runtime | 入口文件 | 命令（示意） |
|---|---|---|
| python | `main.py/app.py/run.py` 或根唯一 `.py` | `python -u <script> <dataDir> [files...]`（保持不变） |
| node | `main.js/app.js/run.js` 或根唯一 `.js/.mjs` | `node <script> <dataDir> [files...]` |
| java | `<entry.jar>`（本期唯一） | `java -cp "<payload>;<payload>/lib/*" <mainClass>` 或 `java -jar <entry.jar> <dataDir> [files...]` |

## 后端设计

### 新抽象 `ToolRunner`（运行时策略）

新建 `ToolRunner` 接口 + `PythonRunner` / `NodeRunner` / `JavaRunner` 实现；`ScriptRunnerService` 保留对外 `run()` 入口，变为按 `runtime` 分发。

```
interface ToolRunner
  String runtimeType()                       // "python" | "node" | "java"
  ResolvedCommand resolveCommand()           // 探测解释器，进程内缓存一次（含否定），复用现有 pythonLookupLock 模式
  List<String> buildCommand(Path script, Path dataDir, List<String> filePaths)
  boolean supportsEntry(String file)         // 该运行时认可的入口文件
```

`ScriptRunnerService.run(runtime, scriptFile, dataFiles)`：
- 依 runtime 分发到对应 runner
- 仍统一走现有超时(120s，Java 可略放宽)/输出上限(5000行/1MB)/临时目录清理骨架
- 保留 `startOutputReader` 截断逻辑与 `ProcessBuilder` 骨架不变，只把"命令组装"交给 runner

### 结果收拢（`ToolService`）

运行结束后：
1. 若 `RESULT_DIR` 内存在输出文件 → 打包（多文件）写入 `uploads/results/`，支持详情页打包下载/预览（沿用现有 FileController 的 download/preview 通道，需扩展为多文件下载）。
2. 否则 → 回退现有行为：stdout 文本即为结果，写入 `result_<toolId>_<userId>_<ts>.txt`。

### 依赖安装（`ScriptPackageService`）

按 `runtime` 分支，复用现有 `runCapture`/超时/日志尾部截断(LOG_TAIL_CHARS=2000)骨架：

| runtime | 依赖清单约定 | 安装命令 | 产物 |
|---|---|---|---|
| python | `requirements.txt` | venv + `pip install -r`（现有路径不变） | `uploads/venvs/<toolId>` |
| node | `package.json` | `npm install --prefix <payload>` | payload 内 node_modules（或 `uploads/nodepkgs/<toolId>`） |
| java | `lib/*.jar` | 合法 jar 校验 + 拷贝到 classpath 产物 | `uploads/jarpkgs/<toolId>` |

安装失败 → 删除半成品产物，携带命令输出尾部报错（语义与现有 pip 一致）。

前端入口候选 `ENTRY_CANDIDATES` 从 `main.py/app.py/run.py` 扩展为含各语言入口；或按 runtime 分别给候选集。

### 安全边界（沿用「内部可信，进程级」）

- **数据文件上传**（`ToolService.isBlockedFileName`）维持现状：`.exe/.dll/.bat/.cmd/.ps1/.msi/.scr/.com/.jar` 全部仍禁（数据只是数据）。
- **工具包 payload**：仅放行"对应当前 runtime 的产物"（如 java 的 `.jar`）；`.exe/.dll/.bat/.cmd/.ps1` 等其余可执行仍禁。
- 运行仍走进程级：超时、输出 5000 行/1MB 截断、临时目录每轮清理——沿用现有。

## Controller 改动（`ToolController.java`）

- 创建/更新请求体读取并落库 `runtime`（缺省 python）。
- 上传/运行不改路由，内部依 `tool.runtime` 选择 runner。
- 结果下载在结果文件为多文件打包时，支持新下载通道（打包 zip）。

## 前端改动

- 创建/编辑表单：运行时下拉（python/node/java），写入 `runtime`。
- 工具详情：显示语言徽章；`isPackage` 展示逻辑不变；上传 accept 不变。
- 运行结果：多文件结果打包下载入口。

## 测试

- `PythonRunner` / `NodeRunner` / `JavaRunner` 各自的探测与命令组装单测（仿 `ScriptRunnerServiceTest` 纯 ASCII 风格）。
- 各语言一个端到端冒烟：Node 工具的 `process.argv` + 写 RESULT_DIR；Java 的 `System.getenv("DATA_DIR")` 冒烟；Python 沿用 tool50。
- 依赖安装单测：python venv（现有）、node npm install（网络不允许则跳过）、java lib 拷贝校验。

## 未来演进（预留，不在本期范围）

- ToolRunner 容器化扩展：为每种语言定义镜像，runner 改调 docker run 而非本地进程。
- 新增语言：只需实现一个 ToolRunner + 一种依赖安装分支。
