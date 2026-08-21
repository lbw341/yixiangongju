# 脚本执行链路重构 — 设计文档

日期：2026-08-21
状态：已确认

## 背景与问题

脚本模板执行功能（见 `2026-08-06-tool-template-script-design.md`）已上线并完成端到端验证。执行链路代码存在以下质量问题：

1. **死代码与重复**：`ToolService.executePythonCode`（约 104 行）全项目无调用点，且与 `runScriptTemplate` 存在大量近重复的进程启动/输出捕获逻辑；文件名净化逻辑在两处各有一份且实现不一致。
2. **错误用返回值字符串伪装**：`runScriptTemplate` 把"未装 Python / 超时 / 非零退出"都编码进返回的 String，调用方无法以类型区分脚本输出与平台错误。
3. **Python 探测无缓存**：每次执行脚本都重新派生最多 3 个 `--version` 子进程探测解释器。
4. **临时目录泄漏**：`Files.createTempDirectory` 与 `deleteDirectory` 之间若抛异常（如 `pb.start()` 失败），工作目录残留。
5. **魔法数字散落**：5000 行 / 100 万字符 / 120 秒硬编码在读取循环里。
6. **行为缺陷①（argv 契约不一致）**：有数据文件时命令为 `python -u <script> <dataDir> <files...>`；无数据文件时 dataDir 完全不传，脚本内 `sys.argv[1]` 直接 IndexError。
7. **行为缺陷②（解码顺序错误）**：`decodeOutput` 先用 GBK 解码且依赖 `new String(bytes, GBK)` 抛异常回退——但该调用对非法序列从不抛异常（静默替换），纯 UTF-8 文本内容会被 GBK 误解产生乱码。

## 目标

1. 进程执行职责抽取为独立组件 `ScriptRunnerService`，`ToolService` 只保留业务编排。
2. 执行结果结构化（`ScriptRunResult`），平台错误与脚本输出可区分；对前端的响应文案保持不变。
3. 删除死代码、合并重复逻辑、缓存 Python 探测、临时目录 try/finally 清理、常量化上限。
4. 修正两个行为缺陷：argv 契约恒定化、UTF-8 优先解码。

## 非目标

- 不做安全加固（沙箱、路径穿越防护、上传扩展名白名单收紧）——留待后续独立设计。
- 不把超时/截断上限做成 application.properties 配置项。
- 不引入测试框架（pom.xml 无 junit/spring-boot-starter-test），验证走编译 + 手动 E2E。

## 新组件 ScriptRunnerService

位置：`backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java`

职责边界——只负责"运行一个脚本并收回结果"，不感知工具/模板业务：

- **Python 探测 + 缓存**：volatile 懒加载，进程内只探测一次；探测顺序不变（`python`/`python3`/`py` → 硬编码安装路径）。
- **进程执行**：`<python> -u <script绝对路径> <dataDir> <file1> <file2> ...`；工作目录为临时目录 `tool_script_*`；环境变量 `PYTHONIOENCODING=utf-8`、`PYTHONUTF8=1`；stderr 合并进 stdout。
- **输出捕获**：后台线程逐行读取，超过 `MAX_OUTPUT_LINES`(5000) 行或 `MAX_OUTPUT_CHARS`(1,000,000) 字符后丢弃余量并追加截断标记（沿用现有标记文案）。
- **超时**：`TIMEOUT_SECONDS`(120) 秒后 `destroyForcibly`；reader 线程 `join(2000)` 兜底，避免请求线程被挂住。
- **清理**：临时目录在 try/finally 中删除，任何异常路径不泄漏。

接口：

```java
public ScriptRunResult run(Path scriptFile, Map<String, byte[]> dataFiles)
```

```java
public class ScriptRunResult {
    boolean pythonFound;   // false = 服务端无可用 Python
    boolean timedOut;      // true = 超 120 秒被强杀
    int exitCode;          // 正常退出码；timedOut 时为 -1
    String output;         // 已截断的合并输出
}
```

常量：`MAX_OUTPUT_LINES = 5000`、`MAX_OUTPUT_CHARS = 1_000_000`、`TIMEOUT_SECONDS = 120`。

## ToolService 改动

- **删除** `executePythonCode`（158-261 行）与旧 `runScriptTemplate`（455-550 行）。
- 保留同名私有方法 `runScriptTemplate(Path, Map)` 作为薄编排层：调用 `scriptRunner.run()`，按 `ScriptRunResult` 状态组装**与现状完全一致的文案**：
  - `!pythonFound` → `"错误: 服务端未安装Python或Python未添加到环境变量"`
  - `timedOut` → `"执行超时 (超过120秒)\n" + output`
  - `exitCode != 0` → `"执行错误 (Exit code: N)\n" + output`
  - 否则 → `output`
- 文件名净化合并为单一 `sanitizeFileName(String)`（采用现 runScriptTemplate 版本：剥路径成分 → 替换 `[\\/:*?"<>|]` → 拦截空/`.`/`..`）。
- `processUploadedFiles` 对 `ScriptRunResult` 的消费方式随上述组装函数自然调整，`FileProcessResult` 结构不变。

## 行为修正

### ① argv 契约恒定化

命令**始终包含 dataDir**，即使没有数据文件：

```
python -u <script> <dataDir> [file1] [file2] ...
```

契约固定为：`sys.argv[1]` = 数据目录（恒存在），`sys.argv[2:]` = 数据文件绝对路径列表（可为空）。需同步更新 `2026-08-06-tool-template-script-design.md` 的"脚本模板执行细节"章节及使用说明约定。

### ② UTF-8 优先解码

`decodeOutput(byte[])` 改为：

1. UTF-8 严格解码（`CharsetDecoder` with `CodingErrorAction.REPORT`）；
2. 失败 → GBK；
3. 再失败 → GB18030 兜底（替换非法序列）。

影响范围：zip 内文本提取与普通文本文件的 `allContent` 收集。脚本 stdout 不受影响（已由环境变量强制 UTF-8）。

## 兼容性

- 前端响应字段与文案零变化（`message`/`result_file`/`processed_files`/`output`）。
- 已部署脚本中，正确使用 `argv[2:]` 遍历文件的脚本不受影响；仅当某脚本依赖"无文件时 argv 为空"这一旧行为时才会受影响（属对缺陷的依赖，视为应修复项）。

## 测试 / 验证

项目无测试框架，验证方式：

1. `mvn compile` 通过。
2. E2E 回归（复用既有 API 流程：登录 → 创建带 .py 模板的工具 → 上传触发执行）：
   - **正常路径**：脚本 + 1 个数据文件 → 断言 output 含预期统计行，result_file 可预览；
   - **新契约**：不传任何数据文件直接触发 → 断言脚本能读到 `argv[1]`（数据目录）且 `argv[2:]` 为空不报错；
   - **错误路径**：语法错误的 .py 模板 → 断言返回"执行错误 (Exit code: 1)"文案格式不变。

## 涉及文件清单

后端：
- `backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java`（新增）
- `backend-java/src/main/java/com/toolplatform/service/ToolService.java`（删代码、编排调整、decodeOutput 修正）

文档：
- `docs/superpowers/specs/2026-08-06-tool-template-script-design.md`（更新 argv 约定章节）
