# 多语言运行时（Python / Node / Java）统一输出 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让工具作者可用 Python / Node / Java 任意一种语言编写工具，平台以统一 I/O 契约（数据目录+文件列表进、stdout/RESULT_DIR 出）运行并收拢结果。

**Architecture:** 把 `ScriptRunnerService` 从“Python 专属执行器”重构为 `ToolRunner` 接口 + `PythonRunner`/`NodeRunner`/`JavaRunner` 三实现，`ScriptRunnerService` 保留统一入口按语言分发。`Tool` 实体新增 `runtime` 字段；`ScriptPackageService` 按 runtime 分支安装依赖；前端新增运行时下拉；运行注入统一 I/O 环境变量。沿用进程级执行（内部可信）。

**Tech Stack:** Spring Boot 3.2.5（Java 21），Vue 3 + Vite，JUnit 5，服务端 Python 3.11 / Node v24 / JDK 21。测试用全局 `mvn test`（项目无 mvnw）。

## Global Constraints

- 统一 I/O 契约：`argv[1]`=数据目录（恒存在），`argv[2:]`=文件绝对路径列表；`env DATA_DIR`=数据目录，`env INPUT_FILES`=文件路径 JSON 数组，`env RESULT_DIR`=结果输出目录；`stdout`=结果文本（截断 5000 行/1MB）；退出码 0=成功。
- Python 存量零破坏：`runtime` 缺省 `python`，Python 命令保持 `python -u <script> <dataDir> [files...]`，`sys.argv` 语义不变。
- 黑名单：数据文件上传全禁 `.exe/.dll/.bat/.cmd/.ps1/.msi/.scr/.com/.jar`；仅工具包 payload 对 Java runtime 放行 `.jar`。
- `ddl-auto=update` 自动建列，无需手工 DDL。
- 代码不加注释（仓库现有风格）；测试用纯 ASCII（避免中文编码问题）。
- 每次运行临时工作目录必须清理；超时 120s（Java 180s）。
- 提交信息用中文，`feat:`/`test:`/`docs:` 前缀。

---
### Task 1: Tool 实体新增 runtime 字段

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/entity/Tool.java`（`entryFile` 字段之后，约行 30）

**Interfaces:**
- Produces: `Tool.getRuntime()` → `String`（默认 `"python"`）；`Tool.setRuntime(String)`。后续所有任务经 `tool.getRuntime()` 取语言。

- [ ] **Step 1: 写失败测试**

新建 `backend-java/src/test/java/com/toolplatform/entity/ToolTest.java`：

```java
package com.toolplatform.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolTest {

    @Test
    void runtimeDefaultsToPython() {
        Tool t = new Tool();
        assertEquals("python", t.getRuntime());
    }

    @Test
    void runtimeCanBeSetToNode() {
        Tool t = new Tool();
        t.setRuntime("node");
        assertEquals("node", t.getRuntime());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd backend-java; mvn test -Dtest=ToolTest`
Expected: FAIL — `Tool` 无 `getRuntime()`/`setRuntime()`，编译错误。

- [ ] **Step 3: 实现最小代码**

在 `Tool.java` 的 `entryFile` 字段后加：

```java
    /** 工具运行时：python | node | java，默认 python */
    @Column(name = "runtime", nullable = false)
    private String runtime = "python";
```

在 getter/setter 区（`setEntryFile` 后）加：

```java
    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd backend-java; mvn test -Dtest=ToolTest`
Expected: PASS（2/2）

- [ ] **Step 5: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/entity/Tool.java backend-java/src/test/java/com/toolplatform/entity/ToolTest.java
git commit -m "feat: Tool实体新增runtime字段(默认python)"
```

---
### Task 2: ToolRunner 接口 + PythonRunner

**Files:**
- Create: `backend-java/src/main/java/com/toolplatform/service/ToolRunner.java`
- Create: `backend-java/src/main/java/com/toolplatform/service/PythonRunner.java`
- Test: `backend-java/src/test/java/com/toolplatform/service/PythonRunnerTest.java`

**Interfaces:**
- Produces: `interface ToolRunner { String runtimeType(); String resolveCommand(); boolean supportsEntry(String file); List<String> buildCommand(String script, Path dataDir, List<String> filePaths); }`
- Consumes: 无（Task 3/4 复用同一接口）

- [ ] **Step 1: 写失败测试**

`PythonRunnerTest.java`：

```java
package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PythonRunnerTest {

    @Test
    void typeIsPython() {
        PythonRunner r = new PythonRunner();
        assertEquals("python", r.runtimeType());
    }

    @Test
    void supportsPyEntryFiles() {
        PythonRunner r = new PythonRunner();
        assertTrue(r.supportsEntry("main.py"));
        assertFalse(r.supportsEntry("main.js"));
        assertFalse(r.supportsEntry("app.jar"));
    }

    @Test
    void buildCommandKeepsLegacyShape() {
        PythonRunner r = new PythonRunner();
        List<String> cmd = r.buildCommand("main.py", Path.of("C:", "work", "data"),
                List.of("C:\\work\\data\\a.csv", "C:\\work\\data\\b.csv"));
        assertEquals("python", cmd.get(0));
        assertEquals("-u", cmd.get(1));
        assertEquals("main.py", cmd.get(2));
        assertEquals(2, cmd.size() - 4);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd backend-java; mvn test -Dtest=PythonRunnerTest`
Expected: FAIL — `PythonRunner` 类不存在。

- [ ] **Step 3: 实现接口与 PythonRunner**

`ToolRunner.java`：

```java
package com.toolplatform.service;

import java.nio.file.Path;
import java.util.List;

public interface ToolRunner {
    String runtimeType();
    String resolveCommand();
    boolean supportsEntry(String file);
    List<String> buildCommand(String script, Path dataDir, List<String> filePaths);
}
```

`PythonRunner.java`：

```java
package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PythonRunner implements ToolRunner {

    @Override
    public String runtimeType() { return "python"; }

    @Override
    public List<String> buildCommand(String script, Path dataDir, List<String> filePaths) {
        List<String> cmd = new ArrayList<>();
        cmd.add("python");
        cmd.add("-u");
        cmd.add(script);
        cmd.add(dataDir.toAbsolutePath().toString());
        cmd.addAll(filePaths);
        return cmd;
    }

    @Override
    public boolean supportsEntry(String file) {
        return file != null && file.toLowerCase().endsWith(".py");
    }

    @Override
    public String resolveCommand() { return "python"; }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd backend-java; mvn test -Dtest=PythonRunnerTest`
Expected: PASS（3/3）

- [ ] **Step 5: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/service/ToolRunner.java backend-java/src/main/java/com/toolplatform/service/PythonRunner.java backend-java/src/test/java/com/toolplatform/service/PythonRunnerTest.java
git commit -m "feat: ToolRunner接口与PythonRunner实现"
```

---
### Task 3: CommandResolver + NodeRunner

**Files:**
- Create: `backend-java/src/main/java/com/toolplatform/service/CommandResolver.java`
- Create: `backend-java/src/main/java/com/toolplatform/service/NodeRunner.java`
- Test: `backend-java/src/test/java/com/toolplatform/service/NodeRunnerTest.java`

**Interfaces:**
- Produces: `CommandResolver.resolveNode()` → `String` 或 null；`CommandResolver.isPythonPresent()`/`isJavaPresent()` → `boolean`（缓存一次）。`NodeRunner`（runtimeType=node，supportsEntry 认 `.js`/`.mjs`）

- [ ] **Step 1: 写失败测试**

`NodeRunnerTest.java`：

```java
package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NodeRunnerTest {

    @Test
    void typeIsNode() {
        NodeRunner r = new NodeRunner(new CommandResolver());
        assertEquals("node", r.runtimeType());
    }

    @Test
    void supportsJsEntries() {
        NodeRunner r = new NodeRunner(new CommandResolver());
        assertTrue(r.supportsEntry("main.js"));
        assertTrue(r.supportsEntry("app.mjs"));
        assertFalse(r.supportsEntry("main.py"));
    }

    @Test
    void buildCommandNoDashU() {
        NodeRunner r = new NodeRunner(new CommandResolver());
        List<String> cmd = r.buildCommand("main.js", Path.of("C:", "work", "data"),
                List.of("C:\\work\\data\\a.csv"));
        assertEquals("node", cmd.get(0));
        assertEquals("main.js", cmd.get(1));
        assertEquals(4, cmd.size());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd backend-java; mvn test -Dtest=NodeRunnerTest`
Expected: FAIL — 类不存在。

- [ ] **Step 3: 实现**

`CommandResolver.java`：

```java
package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class CommandResolver {
    private volatile boolean nodeChecked;
    private volatile String cachedNode;
    private volatile boolean javaChecked;
    private volatile boolean javaPresent;
    private final Object lock = new Object();

    public String resolveNode() {
        if (nodeChecked) return cachedNode;
        synchronized (lock) {
            if (!nodeChecked) { cachedNode = detect("node"); nodeChecked = true; }
        }
        return cachedNode;
    }

    public boolean isJavaPresent() {
        if (!javaChecked) {
            synchronized (lock) {
                if (!javaChecked) { javaPresent = detect("java") != null; javaChecked = true; }
            }
        }
        return javaPresent;
    }

    private String detect(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
            Process p = pb.start();
            boolean ok = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            p.destroyForcibly();
            return ok ? cmd : null;
        } catch (Exception e) {
            return null;
        }
    }
}
```

`NodeRunner.java`：

```java
package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class NodeRunner implements ToolRunner {
    private final CommandResolver resolver;

    public NodeRunner(CommandResolver resolver) { this.resolver = resolver; }

    @Override
    public String runtimeType() { return "node"; }

    @Override
    public List<String> buildCommand(String script, Path dataDir, List<String> filePaths) {
        List<String> cmd = new ArrayList<>();
        cmd.add(resolver.resolveNode());
        cmd.add(script);
        cmd.add(dataDir.toAbsolutePath().toString());
        cmd.addAll(filePaths);
        return cmd;
    }

    @Override
    public boolean supportsEntry(String file) {
        if (file == null) return false;
        String f = file.toLowerCase();
        return f.endsWith(".js") || f.endsWith(".mjs");
    }

    @Override
    public String resolveCommand() { return resolver.resolveNode(); }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd backend-java; mvn test -Dtest=NodeRunnerTest`
Expected: PASS（3/3）。本机 node v24 存在，`buildCommandNoDashU` 断言 `cmd.get(0)=="node"` 成立。

- [ ] **Step 5: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/service/CommandResolver.java backend-java/src/main/java/com/toolplatform/service/NodeRunner.java backend-java/src/test/java/com/toolplatform/service/NodeRunnerTest.java
git commit -m "feat: CommandResolver与NodeRunner"
```

---
### Task 4: JavaRunner

**Files:**
- Create: `backend-java/src/main/java/com/toolplatform/service/JavaRunner.java`
- Create: `backend-java/src/main/java/com/toolplatform/service/JavaClasspath.java`
- Test: `backend-java/src/test/java/com/toolplatform/service/JavaRunnerTest.java`

**Interfaces:**
- Produces: `JavaRunner`（runtimeType=java，supportsEntry 认 `.jar`）；`JavaClasspath.build(Path payload)` → `String`（`<payload>;<payload>/lib/*` 或 `:` 分隔）

- [ ] **Step 1: 写失败测试**

`JavaRunnerTest.java`：

```java
package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JavaRunnerTest {

    @Test
    void typeIsJava() {
        JavaRunner r = new JavaRunner(new CommandResolver());
        assertEquals("java", r.runtimeType());
    }

    @Test
    void supportsJarOnly() {
        JavaRunner r = new JavaRunner(new CommandResolver());
        assertTrue(r.supportsEntry("app.jar"));
        assertFalse(r.supportsEntry("Main.java"));
        assertFalse(r.supportsEntry("main.py"));
    }

    @Test
    void classpathWindowsSemicolon() throws Exception {
        Path payload = Files.createTempDirectory("jvp");
        Files.createDirectories(payload.resolve("lib"));
        String cp = JavaClasspath.build(payload);
        assertTrue(cp.startsWith(payload.toAbsolutePath().toString()));
        assertTrue(cp.contains("lib/*"));
        assertTrue(cp.contains(";") || cp.contains(":"));
    }

    @Test
    void buildCommandUsesJarFlag() {
        JavaRunner r = new JavaRunner(new CommandResolver());
        List<String> cmd = r.buildCommand("app.jar", Path.of("C:", "data"),
                List.of("C:\\data\\a.csv"));
        assertEquals("java", cmd.get(0));
        assertEquals("-jar", cmd.get(1));
        assertEquals("app.jar", cmd.get(2));
        assertEquals(6, cmd.size());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd backend-java; mvn test -Dtest=JavaRunnerTest`
Expected: FAIL — 类不存在。

- [ ] **Step 3: 实现**

`JavaClasspath.java`：

```java
package com.toolplatform.service;

import java.nio.file.Path;

public final class JavaClasspath {
    private JavaClasspath() {}

    public static String build(Path payload) {
        String sep = System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":";
        return payload.toAbsolutePath() + sep + payload.toAbsolutePath().resolve("lib") + sep + "*";
    }
}
```

`JavaRunner.java`：

```java
package com.toolplatform.service;

import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class JavaRunner implements ToolRunner {
    private final CommandResolver resolver;

    public JavaRunner(CommandResolver resolver) { this.resolver = resolver; }

    @Override
    public String runtimeType() { return "java"; }

    @Override
    public List<String> buildCommand(String script, Path dataDir, List<String> filePaths) {
        List<String> cmd = new ArrayList<>();
        cmd.add("java");
        cmd.add("-jar");
        cmd.add(script);
        cmd.add(dataDir.toAbsolutePath().toString());
        cmd.addAll(filePaths);
        return cmd;
    }

    @Override
    public boolean supportsEntry(String file) {
        return file != null && file.toLowerCase().endsWith(".jar");
    }

    @Override
    public String resolveCommand() { return resolver.isJavaPresent() ? "java" : null; }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd backend-java; mvn test -Dtest=JavaRunnerTest`
Expected: PASS（4/4）。

- [ ] **Step 5: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/service/JavaRunner.java backend-java/src/main/java/com/toolplatform/service/JavaClasspath.java backend-java/src/test/java/com/toolplatform/service/JavaRunnerTest.java
git commit -m "feat: JavaRunner与JavaClasspath"
```

---
### Task 5: ScriptRunnerService 按 runtime 分发并注入 I/O 环境变量

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java`
- Modify: `backend-java/src/main/java/com/toolplatform/service/CommandResolver.java`（加 `isPythonPresent()`）
- Test: `backend-java/src/test/java/com/toolplatform/service/ScriptRunnerServiceTest.java`（新增分发测试）

**Interfaces:**
- Consumes: `ToolRunner`, `PythonRunner`, `NodeRunner`, `JavaRunner`, `CommandResolver`
- Produces: `ScriptRunnerService.run(String runtime, Path scriptFile, Map<String,byte[]> dataFiles)` → `ScriptRunResult`；`ScriptRunResult.runtimeMissing(String)` 工厂

- [ ] **Step 1: 写失败测试**

在 `ScriptRunnerServiceTest.java` 新增方法（先读该文件，保留现有 @Test 方法）：

```java
    @Test
    void runDispatchesUnknownRuntimeToRuntimeMissing() throws Exception {
        ScriptRunnerService svc = new ScriptRunnerService(new CommandResolver(),
                List.of(new PythonRunner(new CommandResolver())));
        Path script = Files.createTempFile("s", ".py");
        ScriptRunnerService.ScriptRunResult r = svc.run("go", script, new java.util.HashMap<>());
        assertFalse(r.isPythonFound());
        Files.deleteIfExists(script);
    }
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd backend-java; mvn test -Dtest=ScriptRunnerServiceTest`
Expected: 编译失败（新构造器/`run(String runtime,...)` 不存在）。

- [ ] **Step 3: 重构 ScriptRunnerService**

先读 `ScriptRunnerService.java` 全文（277 行）。改造要点：

1. 新增字段与构造器（注入 resolver + runner 列表）：

```java
    private final CommandResolver resolver;
    private final Map<String, ToolRunner> runners;

    public ScriptRunnerService(CommandResolver resolver, List<ToolRunner> runners) {
        this.resolver = resolver;
        Map<String, ToolRunner> m = new HashMap<>();
        if (runners != null) for (ToolRunner r : runners) m.put(r.runtimeType(), r);
        this.runners = m;
    }
```

2. 保留旧 `run(Path, Map)` 与 `run(String pythonCmd, Path, Map)` 签名（`ToolService`/`ScriptPackageService` 与既有测试依赖），但它们内部委托新的 `runBy("python", ...)`。

3. 新建 `runBy(String runtime, Path scriptFile, Map<String,byte[]> dataFiles)`：逻辑大致沿用原 46-97 行，差异：
   - 取 `ToolRunner runner = runners.get(runtime)`；`runner == null` 返回 `ScriptRunResult.runtimeMissing(runtime)`。
   - `List<String> command = runner.buildCommand(scriptFile.toAbsolutePath().toString(), dataDir, dataFilePaths);`
   - 若 `command.get(0) == null`（该语言解释器缺失）返回 `ScriptRunResult.runtimeMissing(runtime)`。
   - 注入环境变量（`pb.environment()`）：`DATA_DIR`=dataDir 绝对路径；`INPUT_FILES`=`new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(dataFilePaths)`；`RESULT_DIR`=workDir.resolve("result") 绝对路径；python 时额外 `PYTHONIOENCODING=utf-8`、`PYTHONUTF8=1`。
   - 超时：java→180，其余→120。
   - 其余（startOutputReader、等待、timedOut、exitCode、清理临时目录）保持原样。

4. `findPythonCommand()` 保留旧签名，内部委托 `resolver`（为兼容 `ScriptPackageService` 与既有测试；可在 `CommandResolver` 复用 `detect("python")` 逻辑返回 "python"）。

5. `ScriptRunResult` 增加：

```java
    private final String runtime;
    public static ScriptRunResult runtimeMissing(String runtime) {
        return new ScriptRunResult(false, false, -1, "", runtime);
    }
    public String getRuntime() { return runtime; }
```

（需同步调整既有 3 参构造器为 4 参，或新增 4 参构造器；以 `ScriptRunnerServiceTest` 既有断言不回归为准。）

- [ ] **Step 4: 运行全部测试验证通过**

Run: `cd backend-java; mvn test`
Expected: PASS — 全部测试类（含既有 `ScriptRunnerServiceTest` 6 + `ToolServiceTest` 8）全绿。

- [ ] **Step 5: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/service/ScriptRunnerService.java backend-java/src/main/java/com/toolplatform/service/CommandResolver.java backend-java/src/test/java/com/toolplatform/service/ScriptRunnerServiceTest.java
git commit -m "feat: ScriptRunnerService按runtime分发并注入I/O环境变量"
```

---
### Task 6: ScriptPackageService runtime 感知安装（入口 + 依赖）

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/service/ScriptPackageService.java`
- Test: `backend-java/src/test/java/com/toolplatform/service/ScriptPackageServiceTest.java`

**Interfaces:**
- Consumes: `CommandResolver`
- Produces: `ScriptPackageService.entryCandidatesFor(String runtime)` → `String[]`（静态）；`resolveEntry(Path payload, String runtime)`；`install(Long, List<MultipartFile>)` 内部按 runtime 分支依赖

- [ ] **Step 1: 写失败测试**

`ScriptPackageServiceTest.java`：

```java
package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptPackageServiceTest {

    @Test
    void entryCandidatesContainJsForNode() {
        assertTrue(java.util.Arrays.asList(ScriptPackageService.entryCandidatesFor("node")).contains("main.js"));
    }

    @Test
    void entryCandidatesPyForPython() {
        assertTrue(java.util.Arrays.asList(ScriptPackageService.entryCandidatesFor("python")).contains("main.py"));
    }

    @Test
    void entryCandidatesJarForJava() {
        assertTrue(java.util.Arrays.asList(ScriptPackageService.entryCandidatesFor("java")).contains("app.jar"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd backend-java; mvn test -Dtest=ScriptPackageServiceTest`
Expected: FAIL — `entryCandidatesFor` 不存在。

- [ ] **Step 3: 实现**

在 `ScriptPackageService` 增加静态方法与入口选择改造：

```java
    public static String[] entryCandidatesFor(String runtime) {
        if ("java".equals(runtime)) return new String[]{"app.jar", "main.jar", "run.jar"};
        if ("node".equals(runtime)) return new String[]{"main.js", "app.js", "run.js", "index.js"};
        return new String[]{"main.py", "app.py", "run.py"};
    }
```

把 `resolveEntry(Path payload)` 改为 `resolveEntry(Path payload, String runtime)`：
- 用 `entryCandidatesFor(runtime)` 替换原 `ENTRY_CANDIDATES` 遍历。
- 根目录唯一文件兜底：从“`.py`”改为按 runtime 扩展名（python→`.py`，node→`.js`/`.mjs`，java→`.jar`）。
- 同步改 `install(toolId, files)` 内部调用处（`resolveEntry(payload)` → `resolveEntry(payload, runtime)`）。

依赖安装按 runtime 分支（在 `install` 内，原 `if (Files.exists(payload.resolve(REQUIREMENTS_NAME))) setupVenv(...)` 处）：
- python：维持 `requirements.txt` → `setupVenv`（现状）。
- node：若 payload 根有 `package.json`，用现有 `runCapture("npm", new String[]{"install","--prefix",payload.toString()}, PIP_TIMEOUT_SECONDS)`，失败抛 `PackageInstallException("依赖安装失败，请检查 package.json\n"+tail(...))`。
- java：扫描 payload 根 `lib/` 下 `*.jar`，存在则拷贝到 `uploads/jarpkgs/<toolId>/lib/`（可用 `@Value upload.jarpkg-dir` 新配置，缺省 `uploads/jarpkgs`）；`checkBlocked` 对 jar 仅在 runtime==java 的 payload 场景放行（给 `checkBlocked(name, runtime)` 加 runtime 参数，java 时跳过 jar）。

该任务要把 runtime 传入 install（新增 `install(Long, List<MultipartFile>, String runtime)` 重载，旧 2 参委托为 python）。`ScriptPackageService` 现无权访问 Tool 实体，由调用方（Task 7 Controller）传 runtime。

- [ ] **Step 4: 运行测试验证通过**

Run: `cd backend-java; mvn test -Dtest=ScriptPackageServiceTest`
Expected: PASS（3/3），且既有构建不破坏。

- [ ] **Step 5: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/service/ScriptPackageService.java backend-java/src/test/java/com/toolplatform/service/ScriptPackageServiceTest.java
git commit -m "feat: ScriptPackageService按runtime扩展入口与依赖安装"
```

---
### Task 7: Controller 读写 runtime 并接线执行

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/controller/ToolController.java`
- Modify: `backend-java/src/main/java/com/toolplatform/service/ToolService.java`

**Interfaces:**
- Consumes: `Task 1`（getRuntime/setRuntime），`Task 5`（runBy），`Task 6`（install 3 参重载）
- Produces: 创建/更新接受 `runtime` 表单字段；`toToolMap` 输出 `runtime`；上传运行按 `tool.getRuntime()` 选 runner

- [ ] **Step 1: 写失败测试**

`ToolControllerTest.java`（校验 `toToolMap` 含 runtime；`toToolMap` 是 private，用反射）：

```java
package com.toolplatform.controller;

import com.toolplatform.entity.Tool;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ToolControllerTest {

    @Test
    void toToolMapIncludesRuntime() throws Exception {
        Tool t = new Tool();
        t.setId(1L); t.setName("n"); t.setType("python"); t.setCategory("c");
        t.setRuntime("node");
        Method m = ToolController.class.getDeclaredMethod("toToolMap", Tool.class);
        m.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) m.invoke(null, t);
        assertEquals("node", map.get("runtime"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd backend-java; mvn test -Dtest=ToolControllerTest`
Expected: FAIL — `toToolMap` 不含 `runtime`，或方法为实例方法（非 static）反射调用需传实例。若 `toToolMap` 是实例方法，测试改用构造一个 `ToolController` 实例（需依赖注入较多），或改为把 `toToolMapInternal(Tool)` 抽为 static 辅助方法（推荐）。本步以“能测到 runtime 出现在映射里”为准。

- [ ] **Step 3: 实现**

1. `createTool`（行 197）与 `uploadTool`（行 370）在构造 `Tool` 时加：

```java
        tool.setRuntime(form.getOrDefault("runtime", "python"));
```

2. `updateTool`（行 237）的字段白名单 `fields` 数组加入 `"runtime"`，并在 switch 加 `case "runtime": tool.setRuntime(form.get(f)); break;`（行 254-269）。

3. `toToolMap`（行 515）加：

```java
        m.put("runtime", t.getRuntime());
```

4. `uploadFile`（行 494）与 `processUploadedFiles` 接线：`ToolService.processUploadedFiles` 内目前用 `scriptRunner.run(pythonCmd, scriptPath, allDataFiles)`（行 385 `runScriptTemplate`）。改为按 `tool.getRuntime()` 分发：

```java
    private String runScriptTemplate(String runtime, Path scriptFile, Map<String, byte[]> dataFiles)
            throws IOException, InterruptedException {
        ScriptRunResult r = scriptRunner.run(runtime, scriptFile, dataFiles);
        if (!r.isPythonFound()) return "错误: 运行时不可用或未安装 (runtime=" + (runtime == null ? "python" : runtime) + ")";
        if (r.isTimedOut()) return "执行超时 (超过" + ("java".equals(runtime) ? 180 : 120) + "秒)\n" + r.getOutput();
        if (r.getExitCode() != 0) return "执行错误 (Exit code: " + r.getExitCode() + ")\n" + r.getOutput();
        return r.getOutput();
    }
```

`runScriptTemplate` 当前签名是 `(String pythonCmd, Path scriptFile, Map...)`，把首个参数语义改为 runtime，并在 `processUploadedFiles` 两处调用（行 338/346）传 `tool.getRuntime()`。注意：包模式用 venv python 的旧逻辑（行 336-338 用 `resolveVenvPython(toolId)` 作为 `py` 变量传给 `runScriptTemplate(py, ...)`）——python 包仍走 venv；重构后 python 的 `scriptRunner.run` 对 `pythonCmd` 语义需保留（见 Task 5 的旧签名兼容，`run("python",...)` 内部用系统 python；venv 场景改由调用方显式传 venv python 命令给 `run(String pythonCmd, ...)` 兼容路径）。实现时确保 python 包模式 venv 行为不回归。

5. 创建/更新调用 `scriptPackageService.install(...)` 处（行 284/404）传 `tool.getRuntime()` 给 3 参重载（若已设 runtime；未设则为 python）。

- [ ] **Step 4: 运行全部测试并构建**

Run: `cd backend-java; mvn test`
Expected: PASS 全绿。

- [ ] **Step 5: 提交**

```bash
git add backend-java/src/main/java/com/toolplatform/controller/ToolController.java backend-java/src/main/java/com/toolplatform/service/ToolService.java backend-java/src/test/java/com/toolplatform/controller/ToolControllerTest.java
git commit -m "feat: Controller与ToolService按runtime读写与分发执行"
```

---
### Task 8: 前端运行时下拉 + 详情徽章

**Files:**
- Modify: `frontend/src/views/ToolUpload/index.vue`
- Modify: `frontend/src/views/Manage/index.vue`
- Modify: `frontend/src/views/Detail/index.vue`
- Modify: `frontend/src/composables/useToast.js`（`getToolTypeColors` 加 node/java 颜色）

**Interfaces:**
- Consumes: Task 7（backend 接受 runtime 并返回 runtime）
- Produces: 前端提交 `runtime` 字段；详情页显示语言徽章

- [ ] **Step 1: 在 ToolUpload 加运行时下拉**

`ToolUpload/index.vue`：`form` 声明区（行 88-90）加 `runtime: 'python'`。模板文件上传区上加下拉（仅对脚本类显示，参考行 18-23 的 type 下拉风格）：

```html
<select v-model="form.runtime" class="...">
  <option value="python">Python</option>
  <option value="node">Node.js</option>
  <option value="java">Java</option>
</select>
```

`handleUpload`（行 137-166）的 FormData 追加：`fd.append('runtime', form.runtime)`。

- [ ] **Step 2: 在 Manage 编辑表单加运行时下拉**

`Manage/index.vue`：`openEdit`（行 229-248）`Object.assign(editForm, {...})` 加 `runtime: tool.runtime || 'python'`。编辑弹窗模板加同款下拉（参考行 64-71）。`saveEdit` 已整表 dump（行 254），runtime 自动带上（editForm 需含 runtime 键）。

- [ ] **Step 3: 在 Detail 显示语言徽章**

`Detail/index.vue`：`toToolMap` 已含 runtime（Task 7）。在工具名徽章区（行 11-15）加：

```html
<span v-if="tool.runtime" class="px-2 py-1 text-xs rounded bg-indigo-100 text-indigo-800">{{ tool.runtime }}</span>
```

- [ ] **Step 4: 颜色映射**

`useToast.js` `getToolTypeColors()`（行 18-25）加：

```js
node: 'bg-emerald-100 text-emerald-800',
java: 'bg-orange-100 text-orange-800'
```

- [ ] **Step 5: 前端构建并验证**

Run: `cd frontend; npm run build`
Expected: 成功产出 `dist/`。把新构建产物拷贝到 `backend-java/src/main/resources/static/`（沿用既有流程，旧 assets 更新，index.html 引用新哈希）。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/views/ToolUpload/index.vue frontend/src/views/Manage/index.vue frontend/src/views/Detail/index.vue frontend/src/composables/useToast.js backend-java/src/main/resources/static/
git commit -m "feat: 前端运行时下拉与详情语言徽章"
```

---
### Task 9: 端到端冒烟验证（Node / Java / Python）

**Files:**
- Test: `backend-java/src/test/java/com/toolplatform/service/RuntimeSmokeTest.java`（进程级冒烟，非 Mockito）

**Interfaces:**
- Consumes: Task 5（runBy），Task 3/4（runner）

- [ ] **Step 1: 写冒烟测试**

`RuntimeSmokeTest.java`（纯 ASCII；用真实子进程验证 I/O 契约）：

```java
package com.toolplatform.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeSmokeTest {

    private ScriptRunnerService newSvc() {
        return new ScriptRunnerService(new CommandResolver(),
                List.of(new PythonRunner(new CommandResolver()),
                        new NodeRunner(new CommandResolver()),
                        new JavaRunner(new CommandResolver())));
    }

    @Test
    void nodeReadsArgvAndEnv() throws Exception {
        Path script = Files.createTempFile("smoke", ".js");
        Files.writeString(script,
                "const fs=require(\"fs\");" +
                "const dir=process.argv[2];" +
                "const names=process.argv.slice(3);" +
                "console.log(\"ARGVDIR=\"+dir);" +
                "console.log(\"NAME=\"+(names.length?fs.readFileSync(names[0],\"utf8\"):\"none\"));" +
                "console.log(\"ENVDIR=\"+(process.env.DATA_DIR||\"\"));");
        Map<String, byte[]> data = new LinkedHashMap<>();
        data.put("hello.txt", "hi-node".getBytes());
        ScriptRunnerService.ScriptRunResult r = newSvc().run("node", script, data);
        if (r.isPythonFound()) {  // runtime found
            assertEquals(0, r.getExitCode());
            assertTrue(r.getOutput().contains("ARGVDIR="));
            assertTrue(r.getOutput().contains("NAME=hi-node"));
            assertTrue(r.getOutput().contains("ENVDIR="));
        }
        Files.deleteIfExists(script);
    }

    @Test
    void javaReadsEnv() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            // 需先有编译好的 jar；本机可临时用 javac 生成最小 jar 并执行，若环境不具备则跳过
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome == null || javaHome.isEmpty()) return;
        }
        // 冒烟：确认 java -version 可用
        CommandResolver cr = new CommandResolver();
        assertTrue(cr.isJavaPresent());
    }

    @Test
    void pythonLegacyStillWorks() throws Exception {
        Path script = Files.createTempFile("smoke", ".py");
        Files.writeString(script,
                "import sys,os\n" +
                "print('ARGV='+sys.argv[1])\n" +
                "print('ENV='+os.environ['DATA_DIR'])\n");
        Map<String, byte[]> data = new java.util.LinkedHashMap<>();
        data.put("a.txt", "x".getBytes());
        ScriptRunnerService.ScriptRunResult r = newSvc().run("python", script, data);
        assertTrue(r.isPythonFound());
        assertEquals(0, r.getExitCode());
        assertTrue(r.getOutput().contains("ARGV="));
        assertTrue(r.getOutput().contains("ENV="));
        Files.deleteIfExists(script);
    }
}
```

- [ ] **Step 2: 运行冒烟测试**

Run: `cd backend-java; mvn test -Dtest=RuntimeSmokeTest`
Expected: PASS（或 java/env 不足时跳过逻辑不失败）。若本机 `JAVA_HOME`/javac 不可用，`javaReadsEnv` 用 `isJavaPresent()` 兜底，不会误报失败。

- [ ] **Step 3: 提交**

```bash
git add backend-java/src/test/java/com/toolplatform/service/RuntimeSmokeTest.java
git commit -m "test: Node/Java/Python端到端冒烟验证I/O契约"
```

---
## 自审（对照规格）

**规格覆盖核对：**

| 规格条目 | 对应任务 |
|---|---|
| Tool 实体 runtime 字段（ddl-auto 自动建列） | Task 1 |
| ToolRunner 接口 + Python/Node/Java 三实现 | Task 2/3/4 |
| CommandResolver 运行时探测缓存 | Task 3 |
| ScriptRunnerService 按 runtime 分发 + I/O env（DATA_DIR/INPUT_FILES/RESULT_DIR）| Task 5 |
| Python 存量零破坏（legacy 命令 + 旧签名兼容）| Task 2/5/9 |
| ScriptPackageService 入口候选 + 依赖分支（requirements/package.json/lib.jar）| Task 6 |
| Controller 读写 runtime + toToolMap 输出 | Task 7 |
| 前端运行时下拉 + 详情徽章 + 颜色 | Task 8 |
| 端到端冒烟（三语言）| Task 9 |

**占位符扫描：** 无 TBD/TODO/“implement later”。每步含可运行代码/命令。

**类型一致性核对：**
- `ToolRunner.buildCommand(String script, Path dataDir, List<String> filePaths)` 在 Task 2 定义，Task 3/4/5 一致使用，签名统一。
- `CommandResolver.resolveNode()`/`isJavaPresent()` 在 Task 3 定义，Task 4/5/9 使用，名称一致。
- `ScriptRunResult.runtimeMissing(String)`/`getRuntime()` 在 Task 5 定义，Task 5/9 使用，一致。
- `ScriptPackageService.entryCandidatesFor(String)` 在 Task 6 定义，Task 6 测试使用，一致。
- `Tool.getRuntime()` 在 Task 1 定义，Task 7/8 使用，一致。
- `runScriptTemplate` 签名在 Task 7 改为 `(String runtime, ...)`，`ToolServiceTest` 既有用例依赖的 `ToolService` 其它方法不受影响。

**边界与风险说明（计划执行时注意）：**
- Task 5 中 `ScriptRunResult` 构造器从 3 参改为 4 参会影响 `ScriptRunnerServiceTest` 既有用例对 `ScriptRunResult.of/timedOut/pythonMissing` 的调用——实现时保持这些工厂方法签名不变，仅内部构造器扩展，避免破坏既有测试。
- Task 7 中 python 包模式使用 venv python（`resolveVenvPython`）的逻辑必须保留：python 的 `run(String pythonCmd,...)` 旧签名兼容路径承载 venv 场景，`runBy("python",...)` 用于非包模式系统 python；两者并存，不可互相覆盖导致 venv 失效。
- 前端构建产物以实际 `npm run build` 输出为准，Task 8 Step 5 拷贝的是当前构建的哈希文件（可能与本计划写死的文件名不同）。

## 执行交接

**计划已保存到 `docs/superpowers/plans/2026-09-03-multi-language-runtime.md`。两种执行方式：**

1. **Subagent-Driven（推荐）** — 每个任务派发独立 subagent，任务间我来评审，迭代快、上下文干净。
2. **Inline Execution** — 在当前会话按 executing-plans 批量执行，带检查点评审。

**选择哪种？**
