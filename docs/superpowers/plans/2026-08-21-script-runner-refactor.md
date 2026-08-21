# 脚本执行链路重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将脚本进程执行职责从 ToolService 抽取为独立的 ScriptRunnerService，删除死代码，修正 argv 契约与解码顺序两个行为缺陷。

**Architecture:** 新建 `ScriptRunnerService`（Python 探测缓存 + 进程生命周期 + 输出捕获/截断/超时 + 临时目录 try/finally 清理），返回结构化 `ScriptRunResult`；`ToolService.runScriptTemplate` 变为薄编排层，按结果状态组装与现状完全一致的用户文案。规格见 `docs/superpowers/specs/2026-08-21-script-runner-refactor-design.md`。

**Tech Stack:** Java 21, Spring Boot 3.2.5（无测试框架——验证走 `mvn compile` + 手动 E2E API 测试）

## Global Constraints

- 对前端的响应字段与文案零变化：`message`/`result_file`/`processed_files`/`output`；错误文案逐字保持：`错误: 服务端未安装Python或Python未添加到环境变量`、`执行超时 (超过120秒)`、`执行错误 (Exit code: N)`、`脚本执行失败: <msg>`
- 截断标记文案逐字保持：`[输出行数过多，已截断]`、`[输出内容过大，已截断]`
- 常量值：`MAX_OUTPUT_LINES = 5000`、`MAX_OUTPUT_CHARS = 1_000_000`、`TIMEOUT_SECONDS = 120`
- argv 契约恒定化：命令始终包含 dataDir，即使无数据文件
- 不做安全加固、不引入配置项、不引入测试框架（规格"非目标"）
- 提交信息用中文，风格随仓库现有历史（如 `refactor: ...`）

---

### Task 1: 新建 ScriptRunnerService

**Files:**
- Create: `backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java`

**Interfaces:**
- Consumes: 无（独立组件）
- Produces: `public ScriptRunResult run(Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException`；`ScriptRunResult` 含 `isPythonFound()` / `isTimedOut()` / `getExitCode()` / `getOutput()`

- [ ] **Step 1: 创建 ScriptRunnerService.java**

完整文件内容：

```java
package com.toolplatform.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 脚本执行服务
 * 只负责"运行一个脚本并收回结果"，不感知工具/模板业务。
 * 约定: <python> -u <script> <dataDir> [file1] [file2] ...
 * sys.argv[1] 恒为数据目录，sys.argv[2:] 为数据文件列表（可为空）。
 */
@Service
public class ScriptRunnerService {

    private static final int MAX_OUTPUT_LINES = 5000;
    private static final long MAX_OUTPUT_CHARS = 1_000_000;
    private static final int TIMEOUT_SECONDS = 120;

    private final Object pythonLookupLock = new Object();
    private volatile boolean pythonLookupDone;
    private volatile String cachedPythonCmd;

    /**
     * 运行脚本并捕获输出。数据文件写入临时目录后以绝对路径传给脚本。
     * 无论成功失败，临时工作目录都会被清理。
     */
    public ScriptRunResult run(Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("tool_script_");
        try {
            Path dataDir = workDir.resolve("data");
            Files.createDirectories(dataDir);
            List<String> dataFilePaths = new ArrayList<>();
            if (dataFiles != null) {
                for (Map.Entry<String, byte[]> entry : dataFiles.entrySet()) {
                    Path dataFile = dataDir.resolve(sanitizeFileName(entry.getKey()));
                    Files.write(dataFile, entry.getValue());
                    dataFilePaths.add(dataFile.toAbsolutePath().toString());
                }
            }

            String pythonCmd = findPythonCommand();
            if (pythonCmd == null) {
                return ScriptRunResult.pythonMissing();
            }

            List<String> command = new ArrayList<>();
            command.add(pythonCmd);
            command.add("-u");
            command.add(scriptFile.toAbsolutePath().toString());
            command.add(dataDir.toAbsolutePath().toString());
            command.addAll(dataFilePaths);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.directory(workDir.toFile());
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUTF8", "1");

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            Thread readerThread = startOutputReader(process.getInputStream(), output);

            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }
            readerThread.join(2000);

            if (!completed) {
                return ScriptRunResult.timedOut(output.toString());
            }
            return ScriptRunResult.of(process.exitValue(), output.toString());
        } finally {
            deleteDirectory(workDir.toFile());
        }
    }

    /**
     * 剥离路径成分并替换非法字符，拦截空名/./..
     */
    private String sanitizeFileName(String originalName) {
        int lastSlash = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
        String fileName = lastSlash >= 0 ? originalName.substring(lastSlash + 1) : originalName;
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (fileName.isEmpty() || ".".equals(fileName) || "..".equals(fileName)) {
            fileName = "file_" + Math.abs(originalName.hashCode());
        }
        return fileName;
    }

    /**
     * Python 解释器探测，进程内只探测一次（含否定结果缓存）
     */
    private String findPythonCommand() {
        if (pythonLookupDone) {
            return cachedPythonCmd;
        }
        synchronized (pythonLookupLock) {
            if (!pythonLookupDone) {
                cachedPythonCmd = detectPythonCommand();
                pythonLookupDone = true;
            }
        }
        return cachedPythonCmd;
    }

    private String detectPythonCommand() {
        String[] candidates = {"python", "python3", "py"};
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                Process p = pb.start();
                boolean ok = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
                p.destroyForcibly();
                if (ok) {
                    return cmd;
                }
            } catch (Exception e) {
                // 探测失败继续尝试下一个候选
            }
        }

        String[] paths = {
                "C:\\Python39\\python.exe",
                "C:\\Python310\\python.exe",
                "C:\\Python311\\python.exe",
                "C:\\Python312\\python.exe",
                "C:\\Program Files\\Python39\\python.exe",
                "C:\\Program Files\\Python310\\python.exe",
                "C:\\Program Files\\Python311\\python.exe",
                "C:\\Program Files\\Python312\\python.exe",
                "C:\\Program Files (x86)\\Python39\\python.exe",
                "C:\\Program Files (x86)\\Python310\\python.exe",
                "C:\\Users\\Administrator\\AppData\\Local\\Programs\\Python\\Python311\\python.exe",
                "C:\\Users\\Administrator\\AppData\\Local\\Programs\\Python\\Python312\\python.exe"
        };
        for (String path : paths) {
            if (new File(path).exists()) {
                return path;
            }
        }

        return null;
    }

    /**
     * 后台线程读取合并输出，超限截断。设为守护线程避免阻塞 JVM 退出。
     */
    private Thread startOutputReader(InputStream stream, StringBuilder output) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                int lineCount = 0;
                long charCount = 0;
                boolean linesTruncated = false;
                boolean charsTruncated = false;
                while ((line = reader.readLine()) != null) {
                    if (lineCount < MAX_OUTPUT_LINES && charCount < MAX_OUTPUT_CHARS) {
                        output.append(line).append('\n');
                        lineCount++;
                        charCount += line.length();
                    } else if (lineCount >= MAX_OUTPUT_LINES) {
                        linesTruncated = true;
                    } else {
                        charsTruncated = true;
                    }
                }
                if (linesTruncated) {
                    output.append("\n[输出行数过多，已截断]\n");
                } else if (charsTruncated) {
                    output.append("\n[输出内容过大，已截断]\n");
                }
            } catch (IOException e) {
                // 进程被强杀时流会异常关闭，属预期情况
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void deleteDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteDirectory(child);
            }
        }
        dir.delete();
    }

    /**
     * 脚本运行结果
     */
    public static class ScriptRunResult {
        private final boolean pythonFound;
        private final boolean timedOut;
        private final int exitCode;
        private final String output;

        private ScriptRunResult(boolean pythonFound, boolean timedOut, int exitCode, String output) {
            this.pythonFound = pythonFound;
            this.timedOut = timedOut;
            this.exitCode = exitCode;
            this.output = output;
        }

        public static ScriptRunResult pythonMissing() {
            return new ScriptRunResult(false, false, -1, "");
        }

        public static ScriptRunResult timedOut(String output) {
            return new ScriptRunResult(true, true, -1, output);
        }

        public static ScriptRunResult of(int exitCode, String output) {
            return new ScriptRunResult(true, false, exitCode, output);
        }

        public boolean isPythonFound() { return pythonFound; }
        public boolean isTimedOut() { return timedOut; }
        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`（workdir: `backend-java`）
Expected: BUILD SUCCESS，无编译错误

- [ ] **Step 3: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java
git commit -m "refactor: 新增 ScriptRunnerService 统一脚本进程执行"
```

---

### Task 2: ToolService 接入 ScriptRunnerService

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/service/ToolService.java`

**Interfaces:**
- Consumes: `ScriptRunnerService.run(Path, Map<String, byte[]>)` 与 `ScriptRunResult`（Task 1 定义）
- Produces: 行为不变的处理流程；`processUploadedFiles` 签名不变

- [ ] **Step 1: 构造器注入 ScriptRunnerService**

将字段声明区（原 37-45 行）改为：

```java
    private final ToolRepository toolRepo;
    private final DownloadStatRepository statRepo;
    private final UserToolUsageRepository usageRepo;
    private final ScriptRunnerService scriptRunner;

    public ToolService(ToolRepository toolRepo, DownloadStatRepository statRepo, UserToolUsageRepository usageRepo,
                       ScriptRunnerService scriptRunner) {
        this.toolRepo = toolRepo;
        this.statRepo = statRepo;
        this.usageRepo = usageRepo;
        this.scriptRunner = scriptRunner;
    }
```

并在 import 区新增（`ScriptRunResult` 为内部类，同包也需显式导入）：

```java
import com.toolplatform.service.ScriptRunnerService.ScriptRunResult;
```

- [ ] **Step 2: 删除 executePythonCode 方法**

删除整个 `executePythonCode(String code, Map<String, byte[]> dataFiles)` 方法（原 155-261 行，含其 Javadoc 注释）。全项目无调用点，已确认。

- [ ] **Step 3: 用薄编排层替换旧 runScriptTemplate**

删除整个旧 `runScriptTemplate(Path scriptFile, Map<String, byte[]> dataFiles)` 方法（原 451-550 行），在原位置写入：

```java
    /**
     * 运行工具的脚本模板并组装用户可见的结果文本
     * 约定: sys.argv[1] 恒为数据目录，sys.argv[2:] 为数据文件列表（可为空）
     */
    private String runScriptTemplate(Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        ScriptRunResult r = scriptRunner.run(scriptFile, dataFiles);
        if (!r.isPythonFound()) {
            return "错误: 服务端未安装Python或Python未添加到环境变量";
        }
        if (r.isTimedOut()) {
            return "执行超时 (超过120秒)\n" + r.getOutput();
        }
        if (r.getExitCode() != 0) {
            return "执行错误 (Exit code: " + r.getExitCode() + ")\n" + r.getOutput();
        }
        return r.getOutput();
    }
```

- [ ] **Step 4: 删除 ToolService 中不再使用的辅助方法**

删除以下成员（均已无调用点）：`findPythonCommand()`、`deleteDirectory(File)`。保留 `decodeTextContent`/`decodeOutput`（Task 2 Step 5 修改后者）。

同时清理 imports：删除不再被引用的 `java.util.concurrent.TimeUnit`（若 `Charset`/`ZipFile` 等仍被其他方法使用则保留对应 import）。

- [ ] **Step 5: 修正 decodeOutput 解码顺序**

将原 `decodeOutput(byte[])` 替换为 UTF-8 严格解码优先：

```java
    private String decodeOutput(byte[] bytes) {
        if (bytes.length == 0) return "";

        String utf8 = decodeStrict(bytes, StandardCharsets.UTF_8);
        if (utf8 != null) return utf8;

        String gbk = decodeStrict(bytes, Charset.forName("GBK"));
        if (gbk != null) return gbk;

        return new String(bytes, Charset.forName("GB18030"));
    }

    private String decodeStrict(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
```

新增 imports：`java.nio.ByteBuffer`、`java.nio.charset.CharacterCodingException`、`java.nio.charset.CodingErrorAction`。

- [ ] **Step 6: 编译验证**

Run: `mvn compile -q`（workdir: `backend-java`）
Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/service/ToolService.java
git commit -m "refactor: ToolService 接入 ScriptRunnerService，修复解码顺序与临时目录泄漏"
```

---

### Task 3: 更新旧设计文档的 argv 约定

**Files:**
- Modify: `docs/superpowers/specs/2026-08-06-tool-template-script-design.md`（"脚本模板执行细节"章节，约 89-93 行）

- [ ] **Step 1: 更新约定描述**

将该章节三条要点替换为：

```markdown
### 脚本模板执行细节

- Python 命令探测、`PYTHONIOENCODING=utf-8` / `PYTHONUTF8=1` 环境变量、超时与输出截断逻辑由 `ScriptRunnerService` 统一提供（见 `2026-08-21-script-runner-refactor-design.md`）。
- 数据文件统一写入临时目录，文件路径作为 argv 传入脚本：`python -u <script> <dataDir> [file1] [file2] ...`；**数据目录参数恒定传入**（即使无数据文件）。
- 脚本约定写入使用说明：`sys.argv[1]` 恒为数据目录，`sys.argv[2:]` 为文件路径列表（可为空），结果打印到 stdout。
```

- [ ] **Step 2: 提交**

```bash
git add docs/superpowers/specs/2026-08-06-tool-template-script-design.md
git commit -m "docs: 更新脚本 argv 约定为数据目录恒定传入"
```

---

### Task 4: E2E 回归验证

**Files:**
- 无代码改动（纯验证）；若发现缺陷，回到对应 Task 修复后重跑本任务

**Interfaces:**
- Consumes: 运行中的后端（端口 5000）、admin 账号、`POST /api/auth/login`、`POST /api/tools`、`PUT /api/tools/{id}/update`、`POST /api/tools/{id}/upload`、`GET /api/files/preview/{filename}`、`DELETE /api/tools/{id}`

前置：MySQL57 服务运行中；后端以 `mvn spring-boot:run` 启动（workdir `backend-java`，后台进程，等待端口 5000 监听）。

- [ ] **Step 1: 启动后端**

Run（PowerShell）: `Start-Process -FilePath "mvn.cmd" -ArgumentList "spring-boot:run","-q" -WorkingDirectory "backend-java" -WindowStyle Hidden`
然后轮询 `Get-NetTCPConnection -State Listen -LocalPort 5000` 直至监听（约 20-40 秒）。

- [ ] **Step 2: 登录获取 token**

Run: `Invoke-RestMethod -Uri "http://localhost:5000/api/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"admin","password":"123456"}'`
Expected: 返回含 `token` 字段

- [ ] **Step 3: 案例① 正常路径**

创建带 .py 模板的工具（multipart POST /api/tools，name/type/category 必填，file=无 BOM UTF-8 的测试脚本），脚本内容：

```python
import sys, os
data_dir = sys.argv[1]
files = sys.argv[2:]
total = 0
for f in files:
    p = os.path.join(data_dir, f)
    with open(p, 'r', encoding='utf-8', errors='replace') as fh:
        total += sum(1 for _ in fh)
print('SCRIPT_RUN_OK lines=%d files=%d' % (total, len(files)))
```

上传 1 个 3 行文本数据文件（POST /api/tools/{id}/upload）。
Expected: 响应 `output` 含 `SCRIPT_RUN_OK lines=3 files=1`；`result_file` 可通过 GET /api/files/preview/{filename} 预览且内容一致。

注意：写脚本文件必须用 `[System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))`（无 BOM），否则 Python 报 SyntaxError。

- [ ] **Step 4: 案例② 新契约（无数据文件）**

利用既有行为构造"零数据文件"场景：`processUploadedFiles` 对 `MultipartFile.isEmpty()` 为 true 的文件直接 `continue` 跳过，而 Spring 对 0 字节上传文件 isEmpty()==true。因此上传一个 **0 字节的 .txt** 即可通过校验并让所有文件被跳过 → `allDataFiles` 为空 → 脚本仍被执行。

第二个工具，脚本模板内容：

```python
import sys
print('ARGV_CONTRACT_OK argc=%d argv1_set=%s' % (len(sys.argv), len(sys.argv) > 1))
```

上传 1 个 0 字节 `.txt` 数据文件触发执行。
Expected: `output` 含 `ARGV_CONTRACT_OK argc=2 argv1_set=True`（argc=2 即：程序名 + 数据目录；重构前此处 argc=1，argv[1] 缺失）。

- [ ] **Step 5: 案例③ 错误路径**

第三个工具，脚本模板内容为 `def broken(:`（语法错误）。
Expected: 响应 `output` 以 `执行错误 (Exit code: 1)` 开头，格式与重构前一致。

- [ ] **Step 6: 清理并停止后端**

删除三个测试工具（DELETE /api/tools/{id}）、删除 `backend-java/uploads/templates` 下测试模板与 `uploads/results` 下 result_14_* 类测试结果文件、`taskkill` 后端进程树。

- [ ] **Step 7: 回归结论记录**

三个案例全部符合预期 → 在提交信息中注明已验证；任一失败 → 按 systematic-debugging 流程定位修复后重跑。

---

### Task 5: 推送 GitHub

- [ ] **Step 1: 检查待推送提交**

Run: `git log --oneline origin/master..master`（workdir: 项目根）
Expected: 列出设计文档提交 + Task 1-3 的实现提交

- [ ] **Step 2: 推送**

Run: `git push origin master`
Expected: 推送成功至 https://github.com/lbw341/yixiangongju.git
