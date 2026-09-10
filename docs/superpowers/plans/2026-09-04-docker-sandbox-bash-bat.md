# Docker 全限制沙箱 + bash/bat 运行时 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让上传作者的工具以 bash/sh + python/node/java 运行时在 Docker 全限制沙箱（断网、资源限制、只读输入、超时强杀）中真实运行，交付给第三方 Windows 服务器部署，供外部用户网页调用。

**Architecture:** 在现有 `ScriptRunnerService`+`ToolRunner` 统一 I/O 契约（argv[1]=dataDir、argv[2:]=文件、env DATA_DIR/INPUT_FILES/RESULT_DIR）之上，新增 `SandboxExecutionService`：当 `sandbox.enabled=true` 时，把“运行时命令构建（复用 ToolRunner.buildCommand）+ 环境注入”整体放进一个一次性 Linux 容器执行，而非本机子进程。容器全限制（`--network none`、`--memory`/`--cpus`、非 root、只读挂载输入、仅回传 result 目录），超时强杀。`sandbox.enabled=false` 时回退现有进程级运行（本地开发/无 Docker 验证用，部署文档明确告诫生产必须开启）。bash/bat 运行时接线补齐（半成品 BashRunner/BatRunner 已存在）。

**Tech Stack:** Spring Boot (Java 21), Docker CLI（运行方安装 Docker Desktop/WSL2）, Linux 容器镜像（含 bash/python3/node/jdk）, 现有 ToolRunner 契约, JUnit 5.

## Global Constraints

- **沙箱默认强制开启**：`sandbox.enabled` 默认 `true`；任何“绕过沙箱直接本机跑作者脚本”的路径都不得成为生产默认。`false` 仅限本地显式配置用于开发。
- **容器全限制**：`docker run --rm --network none --memory <X> --cpus <Y> --user <非root> --read-only 输入挂载`，超时强杀，仅取 RESULT_DIR。
- **网络关闭**：容器内不得联网。依赖（pip/npm）必须在**上传时**于宿主机预装，不入容器。
- **统一 I/O 契约不变**：所有运行时 `argv[1]=dataDir`、`argv[2:]=文件`；env `DATA_DIR`/`INPUT_FILES`(JSON)/`RESULT_DIR`；输出=stdout + RESULT_DIR。python vararg 语义（`run(String interpreter,...)`）保持 venv 能力。
- **安全边界**：`.bat/.cmd` 仅在 `bat` 运行时放行（`checkBlocked` 按 runtime 判定）；其它黑名单扩展名对一切运行时保持拦截。
- **本地验证边界（重要）**：本机无 Docker，故容器 E2E 不能本机跑。本地验证 = (1) bash/bat 真子进程 E2E；(2) Docker 命令构造单测（用假 `docker` shim 断言参数）；真实容器隔离 E2E 交付为 Dockerfile + `docker-run-smoke` 脚本，由接收方在装 Docker 的主机上执行。
- **交付物**：代码 + `Dockerfile`（Linux 镜像含 python3/node/jdk/bash）+ `sandbox.docker-image` 配置 + 部署文档（要求接盘方装 Docker Desktop/WSL2）+ `docker-run-smoke` 冒烟脚本。
- 后端构建/测试命令：`mvn -o test`（offline）。前端改完需 `npm run build` 产出到 `backend-java/src/main/resources/static`。
- 遵循现有代码约定：中文业务注释、`ToolRunner` 接口扩展、`@Value` 配置注入、JUnit5 测试风格（沿用 RuntimeSmokeTest / ScriptPackageServiceTest 模式）。

---

## 文件结构

**新建：**
- `backend-java/src/main/java/com/toolplatform/sandbox/DockerRunner.java` — 组装 `docker run` 命令、执行、超时/清理、仅回传 stdout+result。纯 CLI 门面，可注入 shim。
- `backend-java/src/main/java/com/toolplatform/sandbox/SandboxExecutionService.java` — 沙箱编排：准备 input/result 目录、把 ToolRunner 命令映射进容器、设置统一 env、调用 DockerRunner。
- `backend-java/src/test/java/com/toolplatform/sandbox/DockerRunnerTest.java` — 用假 `docker` shim 断言命令参数（network none/memory/cpus/user/mounts/timeout）。
- `backend-java/src/test/java/com/toolplatform/sandbox/SandboxExecutionServiceTest.java` — 命令映射 + env 契约单测。
- `backend-java/src/test/java/com/toolplatform/service/BashRunnerTest.java`、`BatRunnerTest.java` — 半成品 Runner 的补全测试（命令形态）。
- `Dockerfile`（仓库根或 `deploy/`）— Linux 沙箱镜像。
- `deploy/docker-run-smoke.sh` — 接盘方主机冒烟脚本（真实容器跑 python/node/bash + I/O 契约 + lib 测试）。
- `docs/部署与沙箱运维.md` — 接盘方部署（Docker Desktop/WSL2、镜像构建、`sandbox.*` 配置、生产必须开启沙箱）。

**修改：**
- `ScriptPackageService.java`：`entryCandidatesFor` + `entryExtMatches` 加 bash/bat；`checkBlocked` 放行 `.bat/.cmd`(bat)；`entryCandidatesFor` 的默认分支保持 python。
- `CommandResolver` / `BashRunner` / `BatRunner`（`service/`）：补全（若需）`resolveBash` 在 Linux 容器内 `detect("bash")` 优先路径，供容器内 launcher 使用；但**宿主进程级**仍需 Git Bash / ComSpec（保持现状）。
- `ToolService.java`：脚本包执行入口改为：`sandbox.enabled ? sandboxExecution.runBy/run(...) : scriptRunner.runBy/run(...)`。
- 前端 `ToolUpload/index.vue`、`Manage/index.vue`：runtime 下拉加 `bash`/`bat` 选项；`Detail/index.vue` + `useToast.js`：bash/bat 徽章颜色。
- `application.properties`：新增 `sandbox.*` 配置项。

---

### Task 1: bash/bat 运行时接线（上传与入口识别解冻）

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/service/ScriptPackageService.java` (`entryCandidatesFor`, `entryExtMatches`, `checkBlocked`)
- Test: `backend-java/src/test/java/com/toolplatform/service/ScriptPackageServiceTest.java`

**Interfaces:**
- Produces: `entryCandidatesFor("bash") -> ["main.sh","app.sh","run.sh","index.sh"]`；`entryCandidatesFor("bat") -> ["main.bat","app.bat","run.bat"]`；`entryExtMatches("x.sh","bash")==true`、`("x.bat","bat")==true`、`("x.cmd","bat")==true`；`checkBlocked` 对 `bat`/`cmd` 扩展名在 `runtime=="bat"` 时放行（其它运行时仍拦截）；`sh`/`bash`/`bat`/`cmd` 对各自 runtime 在 BLOCKED_EXT 判定中放行。

- [ ] **Step 1: 写失败测试** — 在 `ScriptPackageServiceTest.java` 加断言：`entryCandidatesFor("bash")` 含 `main.sh`；`entryCandidatesFor("bat")` 含 `main.bat`；`entryExtMatches` 对 `.sh`/`.bash`/`.bat`/`.cmd` 正确；`checkBlocked("a.bat","bat")` 不抛、`checkBlocked("a.bat","python")` 抛。
- [ ] **Step 2: 运行确认失败** — `mvn -o test -Dtest=ScriptPackageServiceTest`，预期新增用例 FAIL（当前仅 python/node/java）。
- [ ] **Step 3: 最小实现** — 扩展上述方法；`checkBlocked` 放行逻辑改为 `("bat".equals(ext)||"cmd".equals(ext)) && "bat".equals(runtime)`，`sh`/`bash`/`bat`/`cmd` 从 `BLOCKED_EXT` 判定中按 runtime 豁免。
- [ ] **Step 4: 运行确认通过** — `mvn -o test -Dtest=ScriptPackageServiceTest` PASS。
- [ ] **Step 5: Commit**（消息如 `feat: 支持 bash/bat 入口候选与 bat/cmd 放行`）

---

### Task 2: Bash/Bat 进程级 Runner 补全测试（本地可验证）

**Files:**
- Test: create `backend-java/src/test/java/com/toolplatform/service/BashRunnerTest.java`, `BatRunnerTest.java`
- Modify: `backend-java/src/main/java/com/toolplatform/service/BashRunner.java`, `BatRunner.java`, `CommandResolver.java`（如需修正）

**Interfaces:**
- Consumes: `BashRunner.buildCommand(script,dataDir,filePaths)`（`bash script dataDir files...`）；`BatRunner.buildCommand`（`cmd /c script dataDir files...`）；`CommandResolver.resolveBash()/resolveCmd()`。
- Produces: 稳定的进程级命令形态，供 Task 4 复用为容器内 launcher 的宿主侧参照，也是本地 E2E 验证对象。

- [ ] **Step 1: 写测试** — BashRunner: 断言 `buildCommand` 首元素为 bash 路径、`supportsEntry(".sh"/".bash")` 为 true、`.py` 为 false；BatRunner: 断言 `cmd.exe /c script` 形态、`supportsEntry(".bat"/".cmd")`。对 `.sh` 用本机 Git Bash（若存在）做一个真子进程 E2E（`RuntimeSmokeTest` 模式）读 dataDir/files 输出。
- [ ] **Step 2: 运行确认失败** — 新增用例当前 FAIL（BashRunner/BatRunner 无测试）。
- [ ] **Step 3: 最小实现** — 修正 Runner（确认 resolveBash/resolveCmd 返回路径；`-u` 不适用的推理对 bash/bat 直接 `script` 传参）。
- [ ] **Step 4: 运行确认通过** — 两个测试 PASS。
- [ ] **Step 5: Commit**

---

### Task 3: DockerRunner — `docker run` 全限制命令门面

**Files:**
- Create: `backend-java/src/main/java/com/toolplatform/sandbox/DockerRunner.java`
- Test: `backend-java/src/test/java/com/toolplatform/sandbox/DockerRunnerTest.java`

**Interfaces:**
- Produces: `Result run(List<String> containerFullCommand, Path workDir, Path resultDir, String runtime, long timeoutSeconds)`，返回 `exitCode/output`。内部用 `ProcessBuilder` 执行 `docker`，捕获合并输出、超时 `destroyForcibly`、只回传 stdout。

- [ ] **Step 1: 写失败测试** — 用假 `docker` shim：在临时目录放 `docker.cmd`/`docker`（Windows 用 `docker.bat`），`PATH` 前置注入，断言 DockerRunner 以正确 argv 调用它（`run`、`--rm`、`--network none`、`--memory`、`--cpus`、`--user`、`--mount` 等）。
- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: 最小实现** — `docker` 存在性检测 + `ProcessBuilder` 执行 + 超时强杀 + 输出捕获（复用 ScriptRunnerService 的输出捕获模式）。
- [ ] **Step 4: 运行确认通过**
- [ ] **Step 5: Commit**

---

### Task 4: SandboxExecutionService — 命令映射 + env 编排

**Files:**
- Create: `backend-java/src/main/java/com/toolplatform/sandbox/SandboxExecutionService.java`
- Test: `backend-java/src/test/java/com/toolplatform/sandbox/SandboxExecutionServiceTest.java`

**Interfaces:**
- Consumes: `ToolRunner.buildCommand(runtime, script, dataDir, filePaths)`；配置 `sandbox.*`；`DockerRunner`。
- Produces: `ScriptRunResult 形态 run(String runtime, Path scriptFile, Map<String,byte[]> dataFiles)`（复用 `ScriptRunnerService.ScriptRunResult` 的字段/工厂语义）。内部：建容器工作目录（input 只读挂载 + result 可写挂载）→ 把宿主 dataDir/resultDir 路径映射为容器内挂载点 → 在容器内重建统一 env（DATA_DIR/INPUT_FILES/RESULT_DIR）→ 组装 `docker run <全限制> <image> <launcher> <runtime> <容器内脚本路径> <containerDataDir> [files...]` → 调用 DockerRunner，时长为 `sandbox.timeout`。

- [ ] **Step 1: 写失败测试** — 断言生成的 docker argv 含 `--network none`、`--memory`、`--cpus`、`--user`、`--mount`（input 只读）、`--mount`（result 可写）、镜像名、以及容器内映射后的脚本/数据路径；env 含 DATA_DIR/INPUT_FILES/RESULT_DIR。用假 `launcher`（简单 shell 脚本回显 env）。
- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: 最小实现**
- [ ] **Step 4: 运行确认通过**
- [ ] **Step 5: Commit**

---

### Task 5: ToolService 接入（默认沙箱、可配置回退）

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/service/ToolService.java`（脚本包执行分支 331-343）
- Test: `backend-java/src/test/java/com/toolplatform/service/ToolServiceTest.java`

**Interfaces:**
- Consumes: `SandboxExecutionService.run(...)`（同名 python 语义）与 `runBy(...)`。
- Produces: 执行入口变为 `sandbox.enabled ? sandboxExecution : scriptRunner`。

- [ ] **Step 1: 写失败测试** — 注入一个 fake `SandboxExecutionService`，断言 `sandbox.enabled=true` 时走 sandbox、`false` 时走 scriptRunner；python venv 语义在两种模式下都保留（sandbox 内用宿主 venv 挂载）。「（已延期：容器内 venv 挂载暂缓，见 docs/部署与沙箱运维.md §5.2.1）」
- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: 最小实现** — 注入 `@Value("${sandbox.enabled:true}")` 开关 + `SandboxExecutionService`；`runScriptTemplatePython`/`runScriptTemplateByRuntime` 按开关分派。
- [ ] **Step 4: 运行确认通过**
- [ ] **Step 5: Commit**

---

### Task 6: 配置 + 前端下拉/徽章

**Files:**
- Modify: `backend-java/src/main/resources/application.properties`（加 `sandbox.enabled/memory/cpus/timeout/docker-image/run-as-user`）
- Modify: `frontend/src/views/ToolUpload/index.vue`、`Manage/index.vue`（runtime 下拉加 bash/bat）、`Detail/index.vue`、`composables/useToast.js`（徽章颜色）

- [ ] **Step 1: 前端 drop-down 加 `bash`、`bat` 选项**（python/node/java 之后）
- [ ] **Step 2: 徽章颜色** — bash/bat 用与 node/java 不同的浅色系映射
- [ ] **Step 3: 配置项加入 application.properties**（默认 enabled=true）
- [ ] **Step 4: `npm run build`** 产出 static，确认 Vite 打包成功
- [ ] **Step 5: Commit**

---

### Task 7: Dockerfile + 交付冒烟脚本 + 部署文档

**Files:**
- Create: `Dockerfile`（FROM 一个含 bash + python3 + node + jdk 的镜像，或用多阶段；创建非 root 用户 `sandboxuser`；拷贝 launcher 脚本）
- Create: `deploy/docker-run-smoke.sh`
- Create: `docs/部署与沙箱运维.md`

**Interfaces:**
- 容器内 launcher 约定：`launcher <runtime> <scriptInContainer> <dataDirInContainer> [files...]`，负责设置 DATA_DIR/INPUT_FILES/RESULT_DIR（RESULT_DIR=容器内 result 挂载点）并按 runtime 调用 `bash/python3/node/java + ToolRunner 命令形态`。

- [ ] **Step 1: 写 Dockerfile**（经实际可构建性校验——无 Docker 时至少做语法/文件核对）
- [ ] **Step 2: 写 `docker-run-smoke.sh`** — 构建镜像、依次跑 python/node/bash/java 四个 runtime 的真实容器、断言 I/O 契约（argv+env+RESULT_DIR）、以及一个“断网验证”（容器内 `curl/wget` 失败但我方命令成功）。
- [ ] **Step 3: 写部署文档** — 接盘方：装 Docker Desktop（WSL2）、`docker build`、配置 `sandbox.*`、生产必须 `sandbox.enabled=true`、本地开发可 `false`。
- [ ] **Step 4: 人工评审 Dockerfile/脚本/文档**（无 Docker 本地无法执行，做静态核对）
- [ ] **Step 5: Commit**

---

### Task 8: 全量回归 + 计划一致性核对

**Files:** （无新增）
- Run: `mvn -o test`（offline）— 期望全绿（含新增 Bash/Bat/Sandbox 单测；容器 E2E 交由交付冒烟脚本）
- Run: `npm run build` — 期望成功
- Review: 对照本计划 Global Constraints 逐项核对；尤其“沙箱默认强制开启”“容器全限制参数”“网络关闭”“本地验证边界”如实。

- [ ] **Step 1: `mvn -o test` 全绿**
- [ ] **Step 2: `npm run build` 成功**
- [ ] **Step 3: 逐条核对 Global Constraints 已满足（沙箱参数/默认开启/断网/统一契约/安全边界）**
- [ ] **Step 4: 记录本地验证边界（哪些只能靠交付冒烟脚本）**
- [ ] **Step 5: Commit（本次为验证性 commit，若无改动则跳过）**

---

## 实施后验证清单（交付方在装有 Docker 的主机执行）

- [ ] `deploy/docker-run-smoke.sh` 通过：python/node/java/bash 四个 runtime 在容器内真实跑 I/O 契约
- [ ] 容器内断网验证通过（`--network none` 生效）
- [ ] 非 root 用户生效（容器内 `id -u != 0`）
- [ ] 超时强杀生效（提交一个超时脚本，确认容器被 destroy）
- [ ] 仅 RESULT_DIR + stdout 回传，输入目录只读不回流宿主
