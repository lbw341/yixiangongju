# 工具模板脚本驱动结果生成 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让工具作者上传的脚本模板真正参与结果生成：使用工具上传数据时，后端执行工具的脚本模板（`python -u <script> <数据目录> <文件1> <文件2> ...`），结果套入文档格式模板的 `{{result}}` 占位符。

**Architecture:** 后端 `Tool` 实体新增 `formatTemplate` 字段；`processUploadedFiles` 改为"优先执行工具脚本模板 → 否则通用周报 → 再套格式模板"。前端上传/编辑页提供两个模板文件框，详情页下载按钮按模板存在情况拆分。

**Tech Stack:** Spring Boot 3.2.5 (Java 21) / MySQL / Vue 3 + Vite。构建验证：后端 `mvn -q -f backend-java/pom.xml compile`，前端 `npm run build`（在 `frontend/` 下执行，产物输出到 `backend-java/src/main/resources/static/`）。

## Global Constraints

- 设计文档：`docs/superpowers/specs/2026-08-06-tool-template-script-design.md`
- 本项目**没有自动化测试框架**（`backend-java/src/test` 为空，前端无 vitest）。每个任务用 `mvn compile` / `npm run build` 作为通过门，最后做人工端到端验证。
- 脚本执行约定：`sys.argv[1]` = 数据目录，`sys.argv[2:]` = 文件路径列表，结果打印到 stdout。
- 格式模板约定：含 `{{result}}` 则整体替换；不含则结果内容追加到模板末尾。
- **不再执行用户上传的 .py**（安全变更），用户上传的 .py 仅作为普通数据收集。
- 兼容性：旧工具 `templateFile` 以 `.py` 结尾 → 当脚本模板；否则 → 当格式模板。
- 不新增任何依赖、不改数据库迁移（依赖 `spring.jpa.hibernate.ddl-auto=update` 自动建列）。

---

### Task 1: Tool 实体新增 formatTemplate 字段

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/entity/Tool.java`

**Interfaces:**
- Produces: `Tool.getFormatTemplate()` / `Tool.setFormatTemplate(String)` — 后续任务使用。

- [ ] **Step 1: 修改实体**

在 `Tool.java` 第 22 行 `private String templateFile = "";` 之后加一行字段：

```java
    private String formatTemplate = "";
```

在 `setTemplateFile`（第 52 行）之后加 getter/setter：

```java
    public String getFormatTemplate() { return formatTemplate; }
    public void setFormatTemplate(String formatTemplate) { this.formatTemplate = formatTemplate; }
```

- [ ] **Step 2: 编译验证**

Run: `mvn -q -f backend-java/pom.xml compile`
Expected: 无错误输出，退出码 0。

- [ ] **Step 3: Commit**

```bash
git add backend-java/src/main/java/com/toolplatform/entity/Tool.java
git commit -m "feat: Tool 实体新增 formatTemplate 字段"
```

---

### Task 2: 上传工具接口支持格式模板 + 工具信息返回 formatTemplate

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/controller/ToolController.java`

**Interfaces:**
- Consumes: `Tool.getFormatTemplate()`/`setFormatTemplate()`（Task 1）。
- Consumes: `toolService.saveTemplateFile(MultipartFile)`（已存在，`ToolService.java:512`）。
- Produces: `POST /api/tools` 新增可选字段 `format_file`（MultipartFile）。
- Produces: `GET /api/tools/{id}` 与 `/api/tools/my` 返回体新增 `formatTemplate` 字段。

- [ ] **Step 1: uploadTool 增加 format_file 参数**

把 `uploadTool` 方法签名（当前 `ToolController.java:236-239`）：

```java
    @PostMapping("")
    public ResponseEntity<?> uploadTool(HttpServletRequest request,
                                        @RequestParam Map<String, String> form,
                                        @RequestParam(value = "file", required = false) MultipartFile file) {
```

改为：

```java
    @PostMapping("")
    public ResponseEntity<?> uploadTool(HttpServletRequest request,
                                        @RequestParam Map<String, String> form,
                                        @RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam(value = "format_file", required = false) MultipartFile formatFile) {
```

- [ ] **Step 2: 在模板保存块后增加格式模板保存**

在 `uploadTool` 内（当前 `ToolController.java:271` 的 `}` 与 `toolRepo.save(tool);` 之间）插入：

```java
        if (formatFile != null && !formatFile.isEmpty()) {
            try {
                String uniqueName = toolService.saveTemplateFile(formatFile);
                tool.setFormatTemplate(uniqueName);
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "格式模板上传失败"));
            }
        }
```

- [ ] **Step 3: toToolMap 返回 formatTemplate**

在 `toToolMap`（当前 `ToolController.java:347-369`）中 `m.put("templateFile", t.getTemplateFile());` 之后加一行：

```java
        m.put("formatTemplate", t.getFormatTemplate());
```

- [ ] **Step 4: 编译验证**

Run: `mvn -q -f backend-java/pom.xml compile`
Expected: 无错误输出，退出码 0。

- [ ] **Step 5: Commit**

```bash
git add backend-java/src/main/java/com/toolplatform/controller/ToolController.java
git commit -m "feat: 上传工具接口支持格式模板字段"
```

---

### Task 3: 编辑接口改 multipart（支持更换/清除模板）+ 新增下载格式模板接口

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/controller/ToolController.java`

**Interfaces:**
- Consumes: `toolService.getTemplatePath(String)`（已存在，`ToolService.java:524`）、`toolService.updateDownloadStats(Long, Long)`、`toolService.saveTemplateFile(MultipartFile)`。
- Produces: `PUT /api/tools/{id}/update` 改为 multipart，接收表单字段 + 可选 `file`、`format_file`、`clear_template`、`clear_format`（值为 `"1"` 时清空对应模板）。
- Produces: `GET /api/tools/{id}/download_format_template`。

- [ ] **Step 1: 替换 updateTool 方法**

将整个 `updateTool`（当前 `ToolController.java:155-189`）替换为：

```java
    @PutMapping("/{id}/update")
    public ResponseEntity<?> updateTool(@PathVariable Long id, HttpServletRequest request,
                                        @RequestParam Map<String, String> form,
                                        @RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam(value = "format_file", required = false) MultipartFile formatFile) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var opt = toolRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));

        Tool tool = opt.get();
        if (!"admin".equals(u.getRole()) && !tool.getAuthorId().equals(u.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "无权修改此工具"));
        }

        String[] fields = {"name","type","category","keywords","description","department","contact_email","contact_phone","instructions","faq","status"};
        for (String f : fields) {
            if (form.containsKey(f)) {
                switch (f) {
                    case "name": tool.setName(form.get(f)); break;
                    case "type": tool.setType(form.get(f)); break;
                    case "category": tool.setCategory(form.get(f)); break;
                    case "keywords": tool.setKeywords(form.get(f)); break;
                    case "description": tool.setDescription(form.get(f)); break;
                    case "department": tool.setDepartment(form.get(f)); break;
                    case "contact_email": tool.setContactEmail(form.get(f)); break;
                    case "contact_phone": tool.setContactPhone(form.get(f)); break;
                    case "instructions": tool.setInstructions(form.get(f)); break;
                    case "faq": tool.setFaq(form.get(f)); break;
                    case "status": tool.setStatus(form.get(f)); break;
                }
            }
        }

        if (file != null && !file.isEmpty()) {
            try {
                tool.setTemplateFile(toolService.saveTemplateFile(file));
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "模板上传失败"));
            }
        }
        if ("1".equals(form.get("clear_template"))) {
            tool.setTemplateFile("");
        }

        if (formatFile != null && !formatFile.isEmpty()) {
            try {
                tool.setFormatTemplate(toolService.saveTemplateFile(formatFile));
            } catch (IOException e) {
                return ResponseEntity.status(500).body(Map.of("error", "格式模板上传失败"));
            }
        }
        if ("1".equals(form.get("clear_format"))) {
            tool.setFormatTemplate("");
        }

        tool.setUpdatedAt(LocalDateTime.now());
        toolRepo.save(tool);
        return ResponseEntity.ok(Map.of("message", "工具更新成功"));
    }
```

- [ ] **Step 2: 新增 download_format_template 接口**

在 `downloadTemplate` 方法（当前 `ToolController.java:82-113`）之后、`@PostMapping("/create")` 之前插入：

```java
    @GetMapping("/{id}/download_format_template")
    public ResponseEntity<?> downloadFormatTemplate(@PathVariable Long id, HttpServletRequest request) {
        User u = getCurrentUser(request);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        var tool = toolRepo.findById(id);
        if (tool.isEmpty()) return ResponseEntity.status(404).body(Map.of("error", "工具不存在"));
        Tool t = tool.get();
        if (t.getFormatTemplate() == null || t.getFormatTemplate().isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "该工具没有格式模板"));
        }

        Path filePath = toolService.getTemplatePath(t.getFormatTemplate());
        if (!Files.exists(filePath)) {
            return ResponseEntity.status(404).body(Map.of("error", "格式模板文件不存在"));
        }

        toolService.updateDownloadStats(id, u.getId());

        Resource resource = new FileSystemResource(filePath.toFile());
        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
        } catch (Exception e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + t.getFormatTemplate() + "\"")
                .contentLength(filePath.toFile().length())
                .body(resource);
    }
```

注意：`Resource`、`FileSystemResource`、`HttpHeaders`、`MediaType` 已在文件顶部 import（`ToolController.java:8-12`）。

- [ ] **Step 3: 编译验证**

Run: `mvn -q -f backend-java/pom.xml compile`
Expected: 无错误输出，退出码 0。

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/toolplatform/controller/ToolController.java
git commit -m "feat: 编辑接口支持更换/清除模板，新增下载格式模板接口"
```

---

### Task 4: ToolService 重写处理流程 + 新增 runScriptTemplate

**Files:**
- Modify: `backend-java/src/main/java/com/toolplatform/service/ToolService.java`

**Interfaces:**
- Consumes: `Tool.getTemplateFile()` / `getFormatTemplate()`（Task 1）、`toolRepo.findById`（已存在）、`extractZipContent`（已存在）、`generateWeeklyReport`（已存在）、`getTemplatePath`（已存在）、`findPythonCommand`（已存在）、`deleteDirectory`（已存在）。
- Produces: `processUploadedFiles(Long, Long, MultipartFile[])`（签名不变，逻辑重写）——行为：优先执行工具脚本模板，其次通用周报，再套格式模板。
- Produces: 新私有方法 `runScriptTemplate(Path scriptFile, Map<String, byte[]> dataFiles)` → `String`（脚本 stdout，含错误说明）。

- [ ] **Step 1: 重写 processUploadedFiles**

将整个 `processUploadedFiles`（当前 `ToolService.java:347-427`）替换为：

```java
    /**
     * 处理上传的文件
     * 优先执行工具的脚本模板，其次生成通用周报；结果套用格式模板输出
     */
    public FileProcessResult processUploadedFiles(Long toolId, Long userId, MultipartFile[] files) throws IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String resultName = "result_" + toolId + "_" + userId + "_" + timestamp + ".txt";
        Path resultPath = Paths.get(resultDir, resultName);

        updateCallStats(toolId);

        Tool tool = toolRepo.findById(toolId).orElse(null);

        StringBuilder allContent = new StringBuilder();
        List<String> processedFiles = new ArrayList<>();
        Map<String, byte[]> allDataFiles = new LinkedHashMap<>();

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();
            String ext = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase() : "";

            if (!ALLOWED_EXT.contains(ext)) {
                continue;
            }

            if ("zip".equalsIgnoreCase(ext)) {
                ZipExtractResult zipResult = extractZipContent(file.getInputStream());
                allContent.append(zipResult.getContent());
                processedFiles.addAll(zipResult.getProcessedFiles());
                allDataFiles.putAll(zipResult.getDataFiles());
                for (Map.Entry<String, String> py : zipResult.getPythonFiles().entrySet()) {
                    allDataFiles.put(py.getKey(), py.getValue().getBytes(StandardCharsets.UTF_8));
                }
            } else if ("xlsx".equalsIgnoreCase(ext) || "xls".equalsIgnoreCase(ext)) {
                byte[] fileBytes = file.getInputStream().readAllBytes();
                String excelContent = readExcelContent(new ByteArrayInputStream(fileBytes));
                allContent.append("===== Excel文件: ").append(originalName).append(" =====\n");
                allContent.append(excelContent).append("\n\n");
                processedFiles.add(originalName);
                allDataFiles.put(originalName, fileBytes);
            } else {
                byte[] fileBytes = file.getInputStream().readAllBytes();
                String fileContent = new String(fileBytes, StandardCharsets.UTF_8);
                allContent.append("===== 文件: ").append(originalName).append(" =====\n");
                allContent.append(fileContent).append("\n\n");
                processedFiles.add(originalName);
                allDataFiles.put(originalName, fileBytes);
            }
        }

        String resultContent = "";

        String scriptFile = null;
        String formatFile = null;
        if (tool != null) {
            String tf = tool.getTemplateFile();
            String ff = tool.getFormatTemplate();
            if (tf != null && tf.toLowerCase().endsWith(".py")) {
                scriptFile = tf;
                formatFile = (ff != null && !ff.isEmpty()) ? ff : null;
            } else {
                formatFile = (ff != null && !ff.isEmpty()) ? ff : ((tf != null && !tf.isEmpty()) ? tf : null);
            }
        }

        boolean scriptExecuted = false;
        if (scriptFile != null && Files.exists(getTemplatePath(scriptFile))) {
            try {
                resultContent = runScriptTemplate(getTemplatePath(scriptFile), allDataFiles);
                scriptExecuted = true;
            } catch (Exception e) {
                resultContent = "脚本执行失败: " + e.getMessage();
                scriptExecuted = true;
            }
        } else if (allContent.length() > 0) {
            resultContent = generateWeeklyReport(allContent.toString());
        }

        if (formatFile != null && Files.exists(getTemplatePath(formatFile))) {
            String format = Files.readString(getTemplatePath(formatFile), StandardCharsets.UTF_8);
            if (format.contains("{{result}}")) {
                resultContent = format.replace("{{result}}", resultContent);
            } else if (!resultContent.trim().isEmpty()) {
                resultContent = format + "\n" + resultContent;
            } else {
                resultContent = format;
            }
        }

        FileProcessResult result = new FileProcessResult();
        result.setResultName(resultName);
        result.setProcessedFiles(processedFiles);
        if (scriptExecuted) {
            result.setPythonOutput(resultContent);
            result.setPythonExecuted(true);
        }

        if (!resultContent.trim().isEmpty()) {
            Files.writeString(resultPath, resultContent, StandardCharsets.UTF_8);
        }

        return result;
    }
```

- [ ] **Step 2: 新增 runScriptTemplate 方法**

在 `processUploadedFiles` 方法结束后（当前约 `ToolService.java:427` 之后）插入：

```java
    /**
     * 运行工具的脚本模板
     * 约定: python -u <script> <数据目录> <文件1> <文件2> ...
     */
    private String runScriptTemplate(Path scriptFile, Map<String, byte[]> dataFiles) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory("tool_script_");
        Path dataDir = workDir.resolve("data");
        List<String> dataFilePaths = new ArrayList<>();

        if (dataFiles != null && !dataFiles.isEmpty()) {
            Files.createDirectories(dataDir);
            for (Map.Entry<String, byte[]> entry : dataFiles.entrySet()) {
                String originalName = entry.getKey();
                int lastSlash = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
                String fileName = lastSlash >= 0 ? originalName.substring(lastSlash + 1) : originalName;
                if (fileName.contains("/") || fileName.contains("\\") || fileName.contains(":")) {
                    fileName = fileName.replaceAll("[/:*?\"<>|]", "_");
                }
                Path dataFile = dataDir.resolve(fileName);
                Files.write(dataFile, entry.getValue());
                dataFilePaths.add(dataFile.toString());
            }
        }

        String pythonCmd = findPythonCommand();
        if (pythonCmd == null) {
            deleteDirectory(workDir.toFile());
            return "错误: 服务端未安装Python或Python未添加到环境变量";
        }

        List<String> command = new ArrayList<>();
        command.add(pythonCmd);
        command.add("-u");
        command.add(scriptFile.toString());
        if (!dataFilePaths.isEmpty()) {
            command.add(dataDir.toString());
            command.addAll(dataFilePaths);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(workDir.toFile());

        Map<String, String> env = pb.environment();
        env.put("PYTHONIOENCODING", "utf-8");
        env.put("PYTHONUTF8", "1");

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        InputStream rawStream = process.getInputStream();

        Thread readerThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(rawStream, StandardCharsets.UTF_8));
                String line;
                int lineCount = 0;
                long charCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 5000 && charCount < 1000000) {
                    output.append(line).append("\n");
                    lineCount++;
                    charCount += line.length();
                }
                if (lineCount >= 5000) {
                    output.append("\n[输出行数过多，已截断]\n");
                } else if (charCount >= 1000000) {
                    output.append("\n[输出内容过大，已截断]\n");
                }
            } catch (IOException e) {}
        });
        readerThread.start();

        boolean completed = process.waitFor(120, TimeUnit.SECONDS);
        readerThread.join(2000);

        if (!completed) {
            process.destroyForcibly();
            deleteDirectory(workDir.toFile());
            return "执行超时 (超过120秒)\n" + output.toString();
        }

        int exitCode = process.exitValue();
        deleteDirectory(workDir.toFile());

        if (exitCode != 0) {
            return "执行错误 (Exit code: " + exitCode + ")\n" + output.toString();
        }
        return output.toString();
    }
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -f backend-java/pom.xml compile`
Expected: 无错误输出，退出码 0。（`java.util.Map`、`Path`、`StandardCharsets`、`BufferedReader`、`InputStreamReader` 等均已被文件顶部 `java.util.*`/`java.io.*`/`java.nio.file.*`/`java.nio.charset.*` 覆盖。）

- [ ] **Step 4: Commit**

```bash
git add backend-java/src/main/java/com/toolplatform/service/ToolService.java
git commit -m "feat: 处理流程改为执行工具脚本模板并套用格式模板"
```

---

### Task 5: 上传工具页改为两个模板文件框

**Files:**
- Modify: `frontend/src/views/ToolUpload/index.vue`

**Interfaces:**
- Consumes: `POST /api/tools`（Task 2），字段 `file`（脚本模板）+ `format_file`（格式模板）。
- Produces: 脚本 `scriptFile`/`formatFile` ref，上传时按需 append 两个文件。

- [ ] **Step 1: 替换模板区 HTML**

将第 35-38 行的"上传文件"输入框：

```html
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">上传文件</label>
          <input type="file" @change="onFileChange" required class="w-full border border-gray-300 rounded-lg p-3">
        </div>
```

替换为：

```html
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">脚本模板 (.py)</label>
          <input type="file" accept=".py" @change="onScriptChange" class="w-full border border-gray-300 rounded-lg p-3">
          <p class="text-xs text-gray-400 mt-1">可选：Python 脚本，用于处理用户上传的数据。约定：sys.argv[1] 为数据目录，sys.argv[2:] 为文件路径列表，结果打印到 stdout</p>
        </div>
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">文档格式模板</label>
          <input type="file" accept=".txt,.md,.html,.log,.csv" @change="onFormatChange" class="w-full border border-gray-300 rounded-lg p-3">
          <p class="text-xs text-gray-400 mt-1">可选：结果输出版式，含 {{ '{{result}}' }} 占位符则替换为处理结果</p>
        </div>
```

（注意：Vue 模板中占位符用 `{{ '{{result}}' }}` 转义，避免被当作插值。）

- [ ] **Step 2: 修改 script 部分**

将第 66-98 行的 script 部分替换为：

```html
<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { request } from '../../api/request'
import { useToast } from '../../composables/useToast'

const router = useRouter()
const { showToast } = useToast()

const form = reactive({
  name: '', type: 'python', category: '规划', description: '', instructions: ''
})
const scriptFile = ref(null)
const formatFile = ref(null)
const uploading = ref(false)

function onScriptChange(e) {
  scriptFile.value = e.target.files[0] || null
}

function onFormatChange(e) {
  formatFile.value = e.target.files[0] || null
}

async function handleUpload() {
  if (!scriptFile.value && !formatFile.value) return showToast('请至少上传一个模板文件', 'error')
  uploading.value = true
  try {
    const fd = new FormData()
    if (scriptFile.value) fd.append('file', scriptFile.value)
    if (formatFile.value) fd.append('format_file', formatFile.value)
    fd.append('name', form.name)
    fd.append('type', form.type)
    fd.append('category', form.category)
    fd.append('description', form.description)
    fd.append('instructions', form.instructions)

    await request('/api/tools', { method: 'POST', body: fd })
    showToast('工具上传成功')
    Object.assign(form, { name: '', type: 'python', category: '规划', description: '', instructions: '' })
    scriptFile.value = null
    formatFile.value = null
    router.push('/manage')
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    uploading.value = false
  }
}
</script>
```

- [ ] **Step 3: 构建验证**

Run: `npm run build`（工作目录 `frontend/`）
Expected: 构建成功，`backend-java/src/main/resources/static/` 重新生成。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/ToolUpload/index.vue
git commit -m "feat: 上传工具页支持脚本模板与格式模板"
```

---

### Task 6: 管理页编辑弹窗支持更换/清除模板

**Files:**
- Modify: `frontend/src/views/Manage/index.vue`

**Interfaces:**
- Consumes: `PUT /api/tools/{id}/update`（Task 3，multipart，字段 `file`/`format_file`/`clear_template`/`clear_format`）。
- Produces: 编辑弹窗新增两个文件框 + 已上传模板文件名展示 + 清除勾选；`saveEdit` 改为 FormData。

- [ ] **Step 1: 编辑弹窗表单增加模板区**

在 `<textarea v-model="editForm.faq" ...></textarea>`（第 94-97 行）之后、按钮区（第 98 行）之前插入：

```html
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">脚本模板 (.py)</label>
            <div class="flex items-center gap-2">
              <input type="file" accept=".py" @change="e => editScriptFile = e.target.files[0] || null" class="w-full border border-gray-300 rounded-lg p-2">
              <span v-if="editing.templateFile" class="text-xs text-gray-500 whitespace-nowrap">{{ editing.templateFile }}</span>
            </div>
            <label class="flex items-center gap-2 text-sm text-gray-500 mt-1 cursor-pointer">
              <input type="checkbox" v-model="clearScript"> 清除脚本模板
            </label>
          </div>
          <div class="md:col-span-2">
            <label class="block text-sm font-medium text-gray-700 mb-1">文档格式模板</label>
            <div class="flex items-center gap-2">
              <input type="file" accept=".txt,.md,.html,.log,.csv" @change="e => editFormatFile = e.target.files[0] || null" class="w-full border border-gray-300 rounded-lg p-2">
              <span v-if="editing.formatTemplate" class="text-xs text-gray-500 whitespace-nowrap">{{ editing.formatTemplate }}</span>
            </div>
            <label class="flex items-center gap-2 text-sm text-gray-500 mt-1 cursor-pointer">
              <input type="checkbox" v-model="clearFormat"> 清除格式模板
            </label>
          </div>
```

- [ ] **Step 2: 修改 script 部分**

将第 110-156 行中相关部分替换：

在 `const editForm = reactive({})`（第 122 行）后加：

```js
const editScriptFile = ref(null)
const editFormatFile = ref(null)
const clearScript = ref(false)
const clearFormat = ref(false)
```

在 `openEdit` 中，`editing.value = tool`（第 141 行）之前加：

```js
  editScriptFile.value = null
  editFormatFile.value = null
  clearScript.value = false
  clearFormat.value = false
```

将 `saveEdit`（第 144-156 行）替换为：

```js
async function saveEdit() {
  saving.value = true
  try {
    const fd = new FormData()
    Object.entries(editForm).forEach(([k, v]) => fd.append(k, v ?? ''))
    if (editScriptFile.value) fd.append('file', editScriptFile.value)
    if (editFormatFile.value) fd.append('format_file', editFormatFile.value)
    if (clearScript.value) fd.append('clear_template', '1')
    if (clearFormat.value) fd.append('clear_format', '1')
    await request(`/api/tools/${editing.value.id}/update`, { method: 'PUT', body: fd })
    showToast('工具更新成功')
    editing.value = null
    loadMyTools()
  } catch (e) {
    showToast(e.message, 'error')
  } finally {
    saving.value = false
  }
}
```

- [ ] **Step 3: 构建验证**

Run: `npm run build`（工作目录 `frontend/`）
Expected: 构建成功。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/Manage/index.vue
git commit -m "feat: 管理页编辑支持更换/清除模板文件"
```

---

### Task 7: 详情页下载按钮拆分

**Files:**
- Modify: `frontend/src/views/Detail/index.vue`

**Interfaces:**
- Consumes: 工具信息接口返回的 `tool.templateFile`/`tool.formatTemplate`（Task 2 已返回）。
- Consumes: `GET /api/tools/{id}/download_template`、`GET /api/tools/{id}/download_format_template`（Task 3）。
- Produces: 模板按钮按存在情况显示；`downloadTemplate(kind)` 按 kind 选接口。

- [ ] **Step 1: 替换模板下载按钮区**

将第 46-48 行：

```html
              <button @click="downloadTemplate" class="w-full bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center justify-center gap-2">
                <i class="fas fa-download"></i> 下载模板
              </button>
```

替换为：

```html
              <button v-if="tool.templateFile" @click="downloadTemplate('template')" class="w-full bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center justify-center gap-2">
                <i class="fas fa-download"></i> 下载脚本模板
              </button>
              <button v-if="tool.formatTemplate" @click="downloadTemplate('format')" class="w-full bg-indigo-500 text-white font-bold py-3 px-4 rounded-lg hover:bg-indigo-600 transition flex items-center justify-center gap-2">
                <i class="fas fa-download"></i> 下载格式模板
              </button>
```

- [ ] **Step 2: 修改 downloadTemplate 函数**

将第 289-307 行的 `downloadTemplate` 替换为：

```js
async function downloadTemplate(kind) {
  if (!userStore.token) return showToast('请先登录', 'error')
  const url = kind === 'format'
    ? `/api/tools/${toolId.value}/download_format_template`
    : `/api/tools/${toolId.value}/download_template`
  try {
    const res = await fetch(url, {
      headers: { 'Authorization': `Bearer ${userStore.token}` }
    })
    if (!res.ok) { const d = await res.json(); throw new Error(d.error) }
    const blob = await res.blob()
    const disposition = res.headers.get('Content-Disposition')
    let filename = 'template'
    if (disposition) { const m = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/); if (m) filename = m[1].replace(/['"]/g, '') }
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = filename
    a.click()
    URL.revokeObjectURL(a.href)
    showToast('模板下载成功')
  } catch (e) { showToast(e.message, 'error') }
}
```

- [ ] **Step 3: 构建验证**

Run: `npm run build`（工作目录 `frontend/`）
Expected: 构建成功。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/Detail/index.vue
git commit -m "feat: 详情页拆分脚本模板/格式模板下载入口"
```

---

### Task 8: 端到端验证

**Files:** 无新文件，仅运行验证。

- [ ] **Step 1: 全量编译 + 构建**

Run: `mvn -q -f backend-java/pom.xml compile`
Expected: 退出码 0。

Run: `npm run build`（工作目录 `frontend/`）
Expected: 构建成功，static 产物更新。

- [ ] **Step 2: 启动后端并验证接口**

启动 MySQL（库 `tooldb`，用户名 `tooluser`/密码 `tooldb123`）后，运行：

```
mvn -f backend-java/pom.xml spring-boot:run
```

确认启动后（端口 5000），用 curl 验证（替换 `{token}` 为登录返回的 JWT）：

1. 创建脚本模板文件 `s.py`：
```python
import sys
data_dir = sys.argv[1]
files = sys.argv[2:]
lines = []
for f in files:
    with open(f, encoding='utf-8') as fp:
        lines.append(fp.read().strip())
print('脚本处理结果: ' + ';'.join(lines))
```
2. 创建格式模板 `fmt.txt`：
```
【工具结果】
{{result}}
—— 报告完毕
```
3. 上传工具（同时带两个模板）：
```
curl -s -X POST "http://localhost:5000/api/tools" -H "Authorization: Bearer {token}" -F "name=测试脚本工具" -F "type=python" -F "category=规划" -F "file=@s.py" -F "format_file=@fmt.txt"
```
Expected: 返回 `{"message":"工具创建成功","tool_id":N}`。

4. 使用该工具上传数据：
```
echo "完成需求分析" > data.txt
curl -s -X POST "http://localhost:5000/api/tools/N/upload" -H "Authorization: Bearer {token}" -F "file=@data.txt"
```
Expected: 返回 `result_file`；`GET /api/files/preview/{result_file}` 内容为：
```
【工具结果】
脚本处理结果: 完成需求分析
—— 报告完毕
```

5. 仅格式模板场景（上传工具不带 `file`，只带 `format_file`），使用工具上传数据，预览结果应为"通用周报内容替换 {{result}} 后"的格式模板。

6. 无模板工具 → 上传数据 → 结果与现有通用周报一致。

- [ ] **Step 3: 提交收尾（如有遗漏改动）**

```bash
git status
git add -A
git commit -m "chore: 工具模板脚本功能端到端验证"
```
（仅当存在未提交改动时执行；`git status` 干净则跳过。）

---

## Self-Review 记录

- **Spec 覆盖**：数据模型（Task 1）✓；上传接口 format_file（Task 2）✓；编辑换模板 + 下载格式模板接口（Task 3）✓；处理流程重写 + runScriptTemplate + 不执行用户 .py + {{result}} 替换 + 兼容逻辑（Task 4）✓；上传页两个文件框（Task 5）✓；管理页编辑（Task 6）✓；详情页下载拆分（Task 7）✓；端到端验证（Task 8）✓。
- **占位符**：无 TBD/TODO；每个代码步骤都给出完整代码。
- **类型一致**：`getFormatTemplate`/`setFormatTemplate`/`formatTemplate` 在 Task 1-4、6-7 中拼写一致；`download_template`/`download_format_template` 在 Task 3、7 一致；multipart 字段 `file`/`format_file`/`clear_template`/`clear_format` 在 Task 3、6 一致；`{{result}}` 占位符在 Task 4、5 一致（前端用 `{{ '{{result}}' }}` 转义）。
- **验证适配**：项目无测试框架，采用 `mvn compile` + `npm run build` + 人工 curl 验证。
