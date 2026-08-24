# 多文件脚本包与依赖预装实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 工具脚本模板支持多文件平铺或单 zip 包上传（含目录结构与 requirements.txt），创建时自动预装独立 venv 依赖，执行时自动使用；存量单 .py 工具零变化。

**Architecture:** 新增 `ScriptPackageService` 负责"一次上传 → 合法脚本包 + 可用环境"的全过程（模式判定、安全解压、入口探测、venv 预装）；Controller 增加 `files` 参数并调整保存顺序；`ToolService` 执行分支按 `packageDir` 区分新旧模式并解析 venv 解释器；前端两个页面的文件控件升级为多选 chips。规格见 `docs/superpowers/specs/2026-08-24-multi-file-script-package-design.md`。

**Tech Stack:** Java 21, Spring Boot 3.2.5, Vue 3；无测试框架——后端验证 `mvn compile -q`，前端验证 `npm run build`，最终 E2E 走真实 API。

## Global Constraints

- 存量单 `.py` 工具全链路行为零变化；旧 API 单 `file` 参数调用方兼容
- 执行契约不变：`sys.argv[1]`=数据目录、`sys.argv[2:]`=文件列表（可空）
- 错误文案逐字：`zip 包必须单独上传，不能与其他文件同时选择`、`无法确定入口脚本：请在包根目录提供 main.py / app.py / run.py，或确保只有一个根级 .py 文件`、`依赖安装失败，请检查 requirements.txt`、`不允许的可执行文件: <name>`
- 常量：`MAX_PACKAGE_ENTRIES = 500`、`MAX_PACKAGE_BYTES = 200MB`、`PIP_TIMEOUT_SECONDS = 300`、`VENV_TIMEOUT_SECONDS = 120`、日志尾部 ≤2000 字符
- 黑名单扩展名：exe/dll/bat/cmd/ps1/msi/scr/com/jar
- 已批准偏差：规格原文称"ScriptRunnerService 无需改动"，实际需新增解释器覆盖重载 `run(String pythonCmd, Path, Map)`（venv 解释器注入的唯一干净途径）；Task 3 中同步修订该句规格
- 提交信息中文，风格随仓库历史

---

### Task 1: 数据模型、配置与 Python 探测开放

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/entity/Tool.java`
- Modify: `backend-java/src/main/resources/application.properties`
- Modify: `backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java`

**Interfaces:**
- Produces: `Tool.getPackageDir()/setPackageDir(String)`、`Tool.getEntryFile()/setEntryFile(String)`；配置键 `upload.script-package-dir`、`upload.venv-dir`；`ScriptRunnerService.findPythonCommand()` 为 public

- [ ] **Step 1: Tool 实体加两列**

在 `templateFile` 字段声明后追加（含 JPA 注解风格与现有字段一致；先读实体文件确认现有注解风格再写）：

```java
    /** 脚本包目录名（uploads/script_packages/ 下），非空表示脚本包模式 */
    @Column(name = "package_dir")
    private String packageDir = "";

    /** 入口脚本在 payload 内的相对路径 */
    @Column(name = "entry_file")
    private String entryFile = "";
```

并为二者补 getter/setter（跟随现有 getter/setter 排版）。

- [ ] **Step 2: 配置项**

application.properties 追加：

```properties
upload.script-package-dir=uploads/script_packages
upload.venv-dir=uploads/venvs
```

- [ ] **Step 3: findPythonCommand 开放**

`ScriptRunnerService.findPythonCommand()` 可见性 `private` → `public`，javadoc 补一句"供脚本包依赖安装复用"。其余不动。

- [ ] **Step 4: 编译 + 确认 uploads 忽略状态**

Run: `mvn compile -q`（workdir `backend-java`）→ BUILD SUCCESS
Run: `git check-ignore backend-java/uploads/script_packages/x backend-java/uploads/venvs/x`（workdir 项目根）
若未被忽略：在 `.gitignore` 追加 `backend-java/uploads/`（或确认现有规则覆盖）。

- [ ] **Step 5: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/entity/Tool.java backend-java/src/main/resources/application.properties backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java .gitignore
git commit -m "feat: Tool 实体增加脚本包字段与存储配置"
```

---

### Task 2: ScriptPackageService 组件

**Files:**
- Create: `backend-java/src/main/java/com/toolplatform/service/ScriptPackageService.java`

**Interfaces:**
- Consumes: `ScriptRunnerService.findPythonCommand()`（Task 1）
- Produces:
  - `public PackageInstallResult install(Long toolId, List<MultipartFile> files) throws IOException, InterruptedException`（失败抛 `PackageInstallException extends IOException`，message 即用户可见文案）
  - `public Path resolvePayload(Long toolId)`、`public Path resolveOriginalZip(Long toolId)`（可能不存在）、`public Path resolveVenvPython(Long toolId)`（可能不存在）、`public void deleteArtifacts(Long toolId)`、`public void zipPayloadTo(Long toolId, OutputStream out) throws IOException`
  - `public static class PackageInstallResult { getPackageDir() / getEntryFile() / getDisplayName() }`

- [ ] **Step 1: 创建完整组件**

```java
package com.toolplatform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 工具脚本包服务
 * 把一次上传落成合法的脚本包目录与可用解释器环境（zip 或平铺两种模式）。
 * 目录布局: uploads/script_packages/<toolId>/payload/** 与 original.zip；uploads/venvs/<toolId>
 */
@Service
public class ScriptPackageService {

    /** 平铺/压缩包成员一律禁止的可执行扩展名 */
    private static final Set<String> BLOCKED_EXT = Set.of("exe", "dll", "bat", "cmd", "ps1", "msi", "scr", "com", "jar");
    private static final String[] ENTRY_CANDIDATES = {"main.py", "app.py", "run.py"};
    private static final String REQUIREMENTS_NAME = "requirements.txt";
    private static final int MAX_PACKAGE_ENTRIES = 500;
    private static final long MAX_PACKAGE_BYTES = 200L * 1024 * 1024;
    private static final int PIP_TIMEOUT_SECONDS = 300;
    private static final int VENV_TIMEOUT_SECONDS = 120;
    private static final int LOG_TAIL_CHARS = 2000;

    @Value("${upload.script-package-dir}")
    private String scriptPackageDir;

    @Value("${upload.venv-dir}")
    private String venvDir;

    private final ScriptRunnerService scriptRunner;

    public ScriptPackageService(ScriptRunnerService scriptRunner) {
        this.scriptRunner = scriptRunner;
    }

    /** 安装失败异常，message 即用户可见文案 */
    public static class PackageInstallException extends IOException {
        public PackageInstallException(String message) { super(message); }
    }

    /** 安装结果 */
    public static class PackageInstallResult {
        private final String packageDir;
        private final String entryFile;
        private final String displayName;

        public PackageInstallResult(String packageDir, String entryFile, String displayName) {
            this.packageDir = packageDir;
            this.entryFile = entryFile;
            this.displayName = displayName;
        }

        public String getPackageDir() { return packageDir; }
        public String getEntryFile() { return entryFile; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * 整包替换式安装：先清理旧产物，落盘、校验、探测入口、按需装依赖。
     * 任一步失败则清理半成品并抛出 PackageInstallException。
     */
    public PackageInstallResult install(Long toolId, List<MultipartFile> files) throws IOException, InterruptedException {
        boolean zipMode = files.size() == 1 && files.get(0).getOriginalFilename() != null
                && files.get(0).getOriginalFilename().toLowerCase().endsWith(".zip");
        if (!zipMode) {
            for (MultipartFile f : files) {
                String n = f.getOriginalFilename() == null ? "" : f.getOriginalFilename();
                if (n.toLowerCase().endsWith(".zip")) {
                    throw new PackageInstallException("zip 包必须单独上传，不能与其他文件同时选择");
                }
            }
        }

        Path pkgRoot = Paths.get(scriptPackageDir, String.valueOf(toolId));
        Path payload = pkgRoot.resolve("payload");
        deleteArtifacts(toolId);
        Files.createDirectories(payload);

        try {
            String displayName;
            if (zipMode) {
                MultipartFile zip = files.get(0);
                String originalName = zip.getOriginalFilename() == null ? "script.zip" : baseName(zip.getOriginalFilename());
                Files.copy(zip.getInputStream(), pkgRoot.resolve("original.zip"), StandardCopyOption.REPLACE_EXISTING);
                extractZipSafe(pkgRoot.resolve("original.zip"), payload);
                displayName = originalName;
            } else {
                Set<String> seen = new HashSet<>();
                for (MultipartFile f : files) {
                    String name = baseName(f.getOriginalFilename() == null ? "" : f.getOriginalFilename());
                    checkBlocked(name);
                    if (!seen.add(name.toLowerCase())) {
                        throw new PackageInstallException("存在重名文件: " + name);
                    }
                    Files.copy(f.getInputStream(), payload.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
                displayName = firstPyIn(payload);
            }

            String entryFile = resolveEntry(payload);
            if (entryFile == null) {
                throw new PackageInstallException("无法确定入口脚本：请在包根目录提供 main.py / app.py / run.py，或确保只有一个根级 .py 文件");
            }

            if (Files.exists(payload.resolve(REQUIREMENTS_NAME))) {
                setupVenv(toolId, payload.resolve(REQUIREMENTS_NAME));
            }

            return new PackageInstallResult(String.valueOf(toolId), entryFile, displayName);
        } catch (IOException | InterruptedException e) {
            deleteArtifacts(toolId);
            throw e;
        }
    }

    public Path resolvePayload(Long toolId) {
        return Paths.get(scriptPackageDir, String.valueOf(toolId), "payload");
    }

    public Path resolveOriginalZip(Long toolId) {
        return Paths.get(scriptPackageDir, String.valueOf(toolId), "original.zip");
    }

    public Path resolveVenvPython(Long toolId) {
        Path win = Paths.get(venvDir, String.valueOf(toolId), "Scripts", "python.exe");
        if (Files.exists(win)) return win;
        return Paths.get(venvDir, String.valueOf(toolId), "bin", "python");
    }

    /** 删除该工具的脚本包目录与 venv（幂等） */
    public void deleteArtifacts(Long toolId) {
        deleteRecursively(Paths.get(scriptPackageDir, String.valueOf(toolId)).toFile());
        deleteRecursively(Paths.get(venvDir, String.valueOf(toolId)).toFile());
    }

    /** 将 payload 现场打包为 zip 写入输出流（平铺模式下载用） */
    public void zipPayloadTo(Long toolId, OutputStream out) throws IOException {
        Path payload = resolvePayload(toolId);
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            Files.walk(payload).filter(Files::isRegularFile).forEach(p -> {
                try {
                    zos.putNextEntry(new ZipEntry(payload.relativize(p).toString().replace('\\', '/')));
                    Files.copy(p, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private void extractZipSafe(Path zipFile, Path payload) throws IOException {
        Exception last = null;
        for (Charset cs : List.of(StandardCharsets.UTF_8, Charset.forName("GB18030"))) {
            try {
                doExtract(zipFile, payload, cs);
                return;
            } catch (PackageInstallException e) {
                throw (PackageInstallException) e;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new PackageInstallException("无法解析 zip 包: " + (last != null ? last.getMessage() : "未知错误"));
    }

    private void doExtract(Path zipFile, Path payload, Charset cs) throws IOException {
        int count = 0;
        long total = 0;
        try (ZipFile zf = new ZipFile(zipFile.toFile(), cs)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                count++;
                if (count > MAX_PACKAGE_ENTRIES) throw new PackageInstallException("包内文件数超过上限 (" + MAX_PACKAGE_ENTRIES + ")");
                Path target = safeResolve(payload, e.getName());
                checkBlocked(target.getFileName().toString());
                total += Math.max(e.getSize(), 0);
                if (total > MAX_PACKAGE_BYTES) throw new PackageInstallException("解压后总大小超过上限 (200MB)");
                Files.createDirectories(target.getParent());
                try (InputStream is = zf.getInputStream(e)) {
                    Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Path safeResolve(Path payload, String entryName) throws PackageInstallException {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            throw new PackageInstallException("非法的压缩包路径: " + entryName);
        }
        Path target = payload.normalize().resolve(normalized).normalize();
        if (!target.startsWith(payload.normalize())) {
            throw new PackageInstallException("非法的压缩包路径: " + entryName);
        }
        return target;
    }

    private String resolveEntry(Path payload) throws IOException {
        for (String cand : ENTRY_CANDIDATES) {
            if (Files.isRegularFile(payload.resolve(cand))) return cand;
        }
        List<String> roots = new ArrayList<>();
        try (var stream = Files.list(payload)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String name = p.getFileName().toString();
                if (Files.isRegularFile(p) && name.toLowerCase().endsWith(".py")) roots.add(name);
            }
        }
        return roots.size() == 1 ? roots.get(0) : null;
    }

    private void setupVenv(Long toolId, Path requirements) throws IOException, InterruptedException {
        String python = scriptRunner.findPythonCommand();
        if (python == null) {
            throw new PackageInstallException("错误: 服务端未安装Python或Python未添加到环境变量");
        }
        Path venvPath = Paths.get(venvDir, String.valueOf(toolId));
        RunResult venvRes = runCapture(python, new String[]{"-m", "venv", venvPath.toAbsolutePath().toString()}, VENV_TIMEOUT_SECONDS);
        if (!venvRes.completed || venvRes.exitCode != 0) {
            throw new PackageInstallException("虚拟环境创建失败\n" + tail(venvRes.output));
        }
        Path venvPy = resolveVenvPython(toolId);
        RunResult pipRes = runCapture(venvPy.toAbsolutePath().toString(),
                new String[]{"-m", "pip", "install", "-r", requirements.toAbsolutePath().toString(), "--disable-pip-version-check"},
                PIP_TIMEOUT_SECONDS);
        if (!pipRes.completed) {
            throw new PackageInstallException("依赖安装超时 (" + PIP_TIMEOUT_SECONDS + "秒)\n" + tail(pipRes.output));
        }
        if (pipRes.exitCode != 0) {
            throw new PackageInstallException("依赖安装失败，请检查 requirements.txt\n" + tail(pipRes.output));
        }
    }

    private static class RunResult {
        final boolean completed;
        final int exitCode;
        final String output;
        RunResult(boolean completed, int exitCode, String output) {
            this.completed = completed;
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private RunResult runCapture(String cmd, String[] args, int timeoutSeconds) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(cmd);
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (var reader2 = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader2.readLine()) != null) {
                    output.append(line).append('\n');
                }
            } catch (IOException ignored) {
            }
        });
        reader.setDaemon(true);
        reader.start();
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        reader.join(2000);
        return new RunResult(completed, completed ? process.exitValue() : -1, output.toString());
    }

    private void checkBlocked(String fileName) throws PackageInstallException {
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
        if (BLOCKED_EXT.contains(ext)) {
            throw new PackageInstallException("不允许的可执行文件: " + fileName);
        }
    }

    private String firstPyIn(Path payload) throws IOException {
        try (var stream = Files.list(payload)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String name = p.getFileName().toString();
                if (Files.isRegularFile(p) && name.toLowerCase().endsWith(".py")) return name;
            }
        }
        return "script";
    }

    private String baseName(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private String tail(String s) {
        if (s == null) return "";
        return s.length() <= LOG_TAIL_CHARS ? s : s.substring(s.length() - LOG_TAIL_CHARS);
    }

    private void deleteRecursively(java.io.File dir) {
        java.io.File[] children = dir.listFiles();
        if (children != null) {
            for (java.io.File child : children) deleteRecursively(child);
        }
        dir.delete();
    }
}
```

说明：`UncheckedIOException` 需补 import `java.io.UncheckedIOException`。

- [ ] **Step 2: 编译**

Run: `mvn compile -q`（workdir `backend-java`）→ BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/service/ScriptPackageService.java
git commit -m "feat: 新增 ScriptPackageService 脚本包安装与环境预装"
```

---

### Task 3: Controller 与 ToolService 接线

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/controller/ToolController.java`
- Modify: `backend-java/src/main/java/com/toolplatform/service/ToolService.java`
- Modify: `backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java`
- Modify: `docs/superpowers/specs/2026-08-24-multi-file-script-package-design.md`

**Interfaces:**
- Consumes: `ScriptPackageService`（Task 2 全部 public 方法）
- Produces: `POST /api/tools` 与 `PUT /api/tools/{id}/update` 的 `files` 参数；400 错误体 `{error, install_log}`；执行分支 venv 解释器注入

- [ ] **Step 1: ScriptRunnerService 解释器覆盖重载**

将现有 `run(Path scriptFile, Map<String, byte[]> dataFiles)` 改造为委托：

```java
    public ScriptRunResult run(Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        return run(null, scriptFile, dataFiles);
    }

    /**
     * 显式指定解释器（pythonCmd 为 null 时自动探测缓存结果）
     */
    public ScriptRunResult run(String pythonCmd, Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        // 原 run 方法体整体移入此处；仅一处差异：
        // 将 "String pythonCmd = findPythonCommand();" 改为
        //   String resolved = pythonCmd != null ? pythonCmd : findPythonCommand();
        //   后续用 resolved 构建命令；resolved == null 时仍返回 ScriptRunResult.pythonMissing()
    }
```

- [ ] **Step 2: ToolService 注入与执行分支**

构造器追加 `ScriptPackageService scriptPackageService` 参数与字段（排版随现有）。

`processUploadedFiles` 中原脚本判定块：

```java
        boolean scriptExecuted = false;
        if (tool != null && tool.getPackageDir() != null && !tool.getPackageDir().isEmpty()) {
            Path scriptPath = scriptPackageService.resolvePayload(tool.getId()).resolve(tool.getEntryFile());
            if (Files.exists(scriptPath)) {
                try {
                    Path venvPy = scriptPackageService.resolveVenvPython(tool.getId());
                    String py = Files.exists(venvPy) ? venvPy.toAbsolutePath().toString() : null;
                    resultContent = runScriptTemplate(py, scriptPath, allDataFiles);
                    scriptExecuted = true;
                } catch (Exception e) {
                    resultContent = "脚本执行失败: " + e.getMessage();
                    scriptExecuted = true;
                }
            } else if (allContent.length() > 0) {
                resultContent = generateWeeklyReport(allContent.toString());
            }
        } else if (scriptFile != null && Files.exists(getTemplatePath(scriptFile))) {
            // ……原有单文件分支保持不变……
```

对应地，薄编排层签名改为：

```java
    private String runScriptTemplate(String pythonCmd, Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        ScriptRunResult r = scriptRunner.run(pythonCmd, scriptFile, dataFiles);
        // ……后续组装逻辑与现状完全一致……
```

单文件分支调用处传 `pythonCmd = null`。

- [ ] **Step 3: ToolController 上传接口**

`POST /api/tools`（uploadTool）：新增参数

```java
@RequestParam(value = "files", required = false) MultipartFile[] files
```

重排顺序：**先** `toolRepo.save(tool)` 拿到 id → 若 `files` 非空调用安装 → 成功则回填三列后再 `save`；失败则 `toolRepo.delete(tool)` 并返回：

```java
return ResponseEntity.status(400).body(Map.of(
        "error", firstLine(e.getMessage()),
        "install_log", e.getMessage() == null ? "" : e.getMessage()));
```

`files` 缺席时保留现有 `file`/`format_file` 处理不变。辅助私有方法：

```java
    private String firstLine(String s) {
        if (s == null) return "";
        int i = s.indexOf('\n');
        return i >= 0 ? s.substring(0, i) : s;
    }
```

`PUT /api/tools/{id}/update`：同样加 `files` 参数；非空 → `install(tool.getId(), ...)` 成功回填三列（失败直接 400，无需删行）；`clear_template=1` 分支追加 `scriptPackageService.deleteArtifacts(id)` 并清空 `packageDir`/`entryFile`。

`DELETE /api/tools/{id}`：删除成功前调用 `scriptPackageService.deleteArtifacts(id)`。

`GET /api/tools/{id}/download_template`：方法开头插入包模式分支——

```java
        if (t.getPackageDir() != null && !t.getPackageDir().isEmpty()) {
            Path orig = toolService.resolveOriginalZip(id);   // 经 ToolService 转发或在控制器注入 ScriptPackageService，取项目现状更顺者
            if (Files.exists(orig)) {
                Resource res = new FileSystemResource(orig); // zip 类型与 Content-Disposition 用 t.getTemplateFile()
                // ……与现有下载分支同样的 ResponseEntity 组装……
            } else {
                // StreamingResponseBody 或 byte[]：toolService.zipPayloadTo(id, out)
                // 文件名: (工具名或 templateFile 去掉扩展名) + ".zip"
            }
        }
```

（转发方式二选一以代码现状最小改动为准，保持一致性即可。）

- [ ] **Step 4: 规格文档同步**

`docs/superpowers/specs/2026-08-24-multi-file-script-package-design.md` 的"`ScriptRunnerService.run(scriptPath, dataFiles)` 本身零改动。"改为：
"`ScriptRunnerService` 仅新增解释器覆盖重载 `run(String pythonCmd, Path, Map)`（null 时走既有缓存探测），用于注入 venv 解释器；其余零改动。"

- [ ] **Step 5: 编译**

Run: `mvn compile -q`（workdir `backend-java`）→ BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/controller/ToolController.java backend-java/src/main/java/com/toolplatform/service/ToolService.java backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java docs/superpowers/specs/2026-08-24-multi-file-script-package-design.md
git commit -m "feat: 上传/更新接口接入脚本包安装，执行链路支持 venv 解释器"
```

---

### Task 4: 前端上传页多选控件

**Files:**
- Modify: `frontend/src/views/ToolUpload/index.vue`

- [ ] **Step 1: 改造脚本模板控件**

模板区第 36-39 行替换为：

```vue
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">脚本模板（多选或单个 zip 包）</label>
          <input type="file" accept=".py,.txt,.zip" multiple @change="onScriptChange" class="w-full border border-gray-300 rounded-lg p-3">
          <ul v-if="scriptFiles.length" class="mt-2 flex flex-wrap gap-2">
            <li v-for="(f, i) in scriptFiles" :key="f.name + i" class="inline-flex items-center gap-1 bg-indigo-50 text-indigo-700 text-xs px-2 py-1 rounded">
              {{ f.name }}
              <button type="button" @click="removeScriptFile(i)" class="text-indigo-400 hover:text-red-500">&times;</button>
            </li>
          </ul>
          <p class="text-xs text-gray-400 mt-1">可选：多个 .py/.txt 文件或单个 .zip（支持文件夹结构）。含 requirements.txt 时创建工具会自动安装依赖。约定：sys.argv[1] 为数据目录，sys.argv[2:] 为文件路径列表，结果打印到 stdout</p>
        </div>
```

脚本区改造：

```js
const BLOCKED_EXT = ['exe', 'dll', 'bat', 'cmd', 'ps1', 'msi', 'scr', 'com', 'jar']
const scriptFiles = ref([])

function onScriptChange(e) {
  const picked = Array.from(e.target.files || [])
  e.target.value = ''
  if (!picked.length) return
  const zips = picked.filter(f => f.name.toLowerCase().endsWith('.zip'))
  if (zips.length > 1 || (zips.length === 1 && picked.length > 1)) {
    return showToast('zip 包必须单独上传，不能与其他文件同时选择', 'error')
  }
  const dup = picked.find((f, i) => picked.findIndex(g => g.name.toLowerCase() === f.name.toLowerCase()) !== i)
    || scriptFiles.value.find(f => picked.some(g => g.name.toLowerCase() === f.name.toLowerCase()))
  if (dup) return showToast('存在重名文件: ' + dup.name, 'error')
  const bad = picked.find(f => BLOCKED_EXT.some(ext => f.name.toLowerCase().endsWith('.' + ext)))
  if (bad) return showToast('不允许的可执行文件: ' + bad.name, 'error')
  scriptFiles.value.push(...picked)
}

function removeScriptFile(i) {
  scriptFiles.value.splice(i, 1)
}
```

`handleUpload` 相应改为：

```js
  if (!scriptFiles.value.length && !formatFile.value) return showToast('请至少上传一个模板文件', 'error')
  ...
  scriptFiles.value.forEach(f => fd.append('files', f))
  ...
  scriptFiles.value = []
```

（原 `scriptFile` ref 删除。）

- [ ] **Step 2: 构建**

Run: `npm run build`（workdir `frontend`）→ 成功无报错

- [ ] **Step 3: 提交**

```bash
git add frontend/src/views/ToolUpload/index.vue
git commit -m "feat: 上传页脚本模板支持多选与 zip 包"
```

---

### Task 5: 前端管理页编辑控件

**Files:**
- Modify: `frontend/src/views/Manage/index.vue`

- [ ] **Step 1: 替换脚本模板单选框**

读文件定位现有脚本模板输入框（约 101 行）与其 ref（约 143 行）、提交逻辑（约 177-181 行）：

- 输入框改 `multiple accept=".py,.txt,.zip"` + chips 列表（复用 Task 4 的交互与校验函数，含"zip 必须单独"与黑名单提示）
- 提交 FormData：`files.forEach(f => fd.append('files', f))`；不再 append 单 `file`
- 弹窗打开时清空已选列表；旁边注明"重新上传将整包替换"

- [ ] **Step 2: 构建**

Run: `npm run build`（workdir `frontend`）→ 成功

- [ ] **Step 3: 提交**

```bash
git add frontend/src/views/Manage/index.vue
git commit -m "feat: 管理页编辑支持整包更换脚本模板"
```

---

### Task 6: E2E 回归验证（六案例）

**Files:**
- 无代码改动；发现缺陷回到对应任务修复后重跑

**Interfaces:**
- Consumes: 运行中的后端、admin 账号、`POST /api/auth/login|tools`、`PUT /api/tools/{id}/update`、`POST /api/tools/{id}/upload`、`GET /api/files/preview/{filename}`、`GET /api/tools/{id}/download_template`、`DELETE /api/tools/{id}`

前置：MySQL57 运行中；后端 `Start-Process mvn.cmd spring-boot:run -WorkingDirectory backend-java -WindowStyle Hidden`，轮询端口 5000；登录 admin/123456 取 token。PowerShell 5.1；`.py/.txt` 一律 `[System.IO.File]::WriteAllText($p,$c,(New-Object System.Text.UTF8Encoding($false)))` 免 BOM；multipart 用手工 boundary + MemoryStream（参考仓库既往验证脚本模式）。

- [ ] **案例① 存量兼容**：旧式单 `file` 字段传一个 .py 创建工具 → 上传数据 → output 正确；删除工具
- [ ] **案例② 平铺多选**：multipart 多个 `files` 部分：main.py（`import six; print('DEPS_OK', six.__version__ if hasattr(six,'__version__') else 'ok')`; 读 argv 打印文件数）+ utils.py + requirements.txt(`six`) → 创建 201；磁盘存在 `uploads/venvs/<id>/Scripts/python.exe`；上传数据 → output 含 DEPS_OK；`download_template` 得到合法 zip（解压后含 main.py/utils.py/requirements.txt）；删除工具 → venv 目录随之消失
- [ ] **案例③ zip 结构**：Compress-Archive 打包 main.py（子包 `pkg/helper.py`，main 里 `from pkg.helper import hello` 打印 HELLO_OK）+ 空 requirements 不带 → 创建 201 → 上传数据 → HELLO_OK；download_template 得到原始 zip（字节对比）
- [ ] **案例④ 入口缺失**：两个无关根级 .py（a.py/b.py 无约定名）→ 400 且 error 为约定文案逐字匹配
- [ ] **案例⑤ 依赖失败**：requirements.txt 内容 `definitely-not-a-real-pkg-xyz==1.0` → 400、error 以 `依赖安装失败` 开头、install_log 含 pip 报错；且 `uploads/script_packages/<id>` 与 `uploads/venvs/<id>` 均不存在（半成品已清理）；数据库中该工具行也已回删
- [ ] **案例⑥ 清理语义**：update 接口 `clear_template=1` → 包目录与 venv 被删、三列清空
- 全部 PASS 后停止后端（taskkill /T /F 端口 5000 进程树），报告写入 `.superpowers/sdd/mp-task-6-report.md`

---

### Task 7: 最终全分支审查

- [ ] 生成审查包（base=abf98e5 前一提交，即本计划首个实现提交的父提交），派发最终审查子代理（requesting-code-review/code-reviewer.md 模板），重点：兼容分支正确性、安装失败的资源清理、zip-slip 防线完整性、前端校验与后端约束一致
- [ ] Critical/Important 发现派单个修复子代理，复核后进入 Task 8

### Task 8: 推送 GitHub

- [ ] `git push origin master`（用户明确要求推送）
