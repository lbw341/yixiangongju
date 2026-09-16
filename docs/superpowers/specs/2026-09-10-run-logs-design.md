# 运行日志（管理员查看用户运行工具的日志）Design Spec

> 日期：2026-09-10　分支：feature/run-logs（基于 feature/docker-sandbox）
> 状态：待评审

## 一、背景与目标

平台让用户在线运行作者上传的工具（python/node/java/bash/bat，当前默认走 Docker 全限制沙箱，本地开发可回退进程级）。目前每次运行只落 3 处聚合数据：

- `Tool.calls`——工具累计调用次数（无用户维度）
- `DownloadStat`——工具按日计数（无用户维度）
- `UserToolUsage`——按用户累计（只随下载更新，**不随运行更新**）

也就是说：**没有任何"谁、何时、运行了哪个工具、结果如何、输出了什么"的逐次记录**。管理员排障（"用户说工具跑挂了"）、审计（谁在滥用某工具）时无据可查。

**目标**：管理员可查看每次用户运行工具的完整日志（审计字段 + 脚本 stdout/stderr 全文），支持按用户 / 工具 / 时间 / 结果状态筛选，并按保留天数自动清理。

## 二、范围

**做：**
1. 新实体 `RunLog`（表 `run_logs`）+ Repository
2. `RunLogService`：`record`（埋点调用）、分页筛选查询、详情、过期清理
3. `RunLogController`：`GET /api/run-logs`（筛选+分页）、`GET /api/run-logs/{id}`（详情），仅 admin
4. `ToolService.processUploadedFiles()` 埋点：拿到 `ScriptRunResult` 后写一条日志；记录失败绝不影响主流程
5. `@EnableScheduling` + `@Scheduled` 每日 03:30 清理过期记录
6. 前端“运行日志”页 + 详情弹窗 + 菜单/路由 + api 封装
7. 单测（RunLogService / ToolService 埋点 / RunLogController）+ 全量回归

**不做（非目标）：**
- 工具作者查看自己工具的运行日志（后续版本可按"作者可见"扩展）
- 日志导出 / 下载 / 归档表 / 按日分区
- 磁盘文件级日志（统一走 DB TEXT）
- 非 admin 的任何查看入口

## 三、数据模型

约定沿用本仓库现状（无 `@ManyToOne`、外键裸 Long、`@GeneratedValue(IDENTITY)`、snake_case 表名、`LocalDateTime createdAt` 内联初始化）。

**实体 `RunLog`（表 `run_logs`）**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long (IDENTITY) | 主键 |
| toolId | Long | 工具 ID |
| toolName | String | 工具名冗余（列表/筛选免联表） |
| userId | Long | 运行者 ID |
| username | String | 运行者用户名冗余 |
| nickname | String | 运行者昵称冗余（管理员常按昵称回忆） |
| runtime | String | python/node/java/bash/bat |
| sandboxUsed | boolean | 本次是否走 Docker 沙箱（false=进程级回退） |
| exitCode | int | 退出码（超时/运行时缺失为 -1） |
| timedOut | boolean | 是否超时 |
| status | String | SUCCESS / FAILED / TIMEOUT / RUNTIME_MISSING |
| inputFileNames | String | 输入文件名逗号连接（截断 ≤500 字符） |
| output | TEXT | 脚本 stdout+stderr 全文（入口已截断 5000 行/1MB） |
| createdAt | LocalDateTime | 运行时间（与记录时间一致） |

**status 推导规则（`RunLogService` 内，输入 `ScriptRunResult`）：**

```
timedOut                    → TIMEOUT
!pythonFound                → RUNTIME_MISSING
exitCode == 0               → SUCCESS
否则                        → FAILED
```

**索引：**

```java
@Table(name = "run_logs", indexes = {
    @Index(name = "idx_runlog_created", columnList = "createdAt"),
    @Index(name = "idx_runlog_tool",   columnList = "toolId"),
    @Index(name = "idx_runlog_user",   columnList = "userId"),
    @Index(name = "idx_runlog_status", columnList = "status")
})
```

## 四、服务层

### 4.1 `RunLogService`

```java
public class RunLogService {
    private final RunLogRepository repository;
    private final int retentionDays; // @Value("${runlog.retention-days:90}")

    // 埋点入口：由 ToolService 调用，try/catch 包裹于调用侧
    public void record(Long toolId, String toolName, Long userId,
                       String username, String nickname,
                       String runtime, boolean sandboxUsed,
                       ScriptRunnerService.ScriptRunResult result,
                       List<String> inputFileNames);

    // 列表：按 createdAt desc 分页，列表 DTO 不含 output
    public PageResult<RunLogSummary> query(RunLogQuery query, int page, int size);

    public RunLogDetail getDetail(long id); // 不存在抛 NotFound

    public int cleanupExpired(); // 删除 createdAt < now - retentionDays，返回删除条数
}
```

- `query` 过滤条件（任意组合、均可省略）：
  - `tool`：`toolName like %tool%`
  - `user`：`username like %user% OR nickname like %user%`
  - `startDate`/`endDate`：`createdAt >= start` / `createdAt < end+1day`
  - `status`：精确匹配
- `RunLogSummary`：id/toolId/toolName/userId/username/runtime/sandboxUsed/exitCode/timedOut/status/inputFileCount/createdAt
- `RunLogDetail`：Summary 全部字段 + inputFileNames + output

### 4.2 配置项

`application.properties` 新增：

```properties
# 运行日志保留天数（超期由每日 00:30 定时任务清理）
runlog.retention-days=90
```

### 4.3 调度

- 主类加 `@EnableScheduling`
- `RunLogService.cleanupExpired()` 标 `@Scheduled(cron = "0 30 3 * * *")`（每日 03:30）
- 清理前后打日志：`运行日志清理：删除 N 条，剩余 M 条`

## 五、埋点

`ToolService.processUploadedFiles(Long toolId, Long userId, MultipartFile[] files)`：

- 现有执行分支（package 路径 `runScriptTemplateByRuntime` / `runScriptTemplatePython`、单 py 模板路径 `runScriptTemplate`）拿到 `ScriptRunResult result` 后，在 `toResultText` 之前插入：

```java
try {
    runLogService.record(tool.getId(), tool.getName(), userId, user.getUsername(), user.getNickname(),
                         normalizedRuntime, sandboxEnabled && sandboxExecution != null,
                         result, fileNames);
} catch (Exception e) {
    log.warn("记录运行日志失败，不影响本次运行", e);
}
```

- 参数说明：
  - `normalizedRuntime`：null/缺省一律归一到 `"python"`（与现有执行语义一致），否则取运行时令牌
  - `fileNames`：`MultipartFile[]` 各文件名，逗号连接后截断 500 字符
  - username / nickname：控制器 `uploadFile` 已持有 `User u`（`getCurrentUser`），直接作为参数传入 `processUploadedFiles(...)`（该方法签名新增 username、nickname 两个参数，比在 Service 内再做一次 `userRepository.findById` 更省）
- 纯报告 fallback（无脚本执行的分支）不记录。

## 六、接口（`RunLogController`，前缀 `/api/run-logs`）

**权限**：两个接口都先 `User u = getCurrentUser(request)`，非 `"admin"` 直接 403（沿用 FeedbackController 同款写法）。

### 6.1 `GET /api/run-logs`

Query 参数：`tool`?、`user`?、`startDate`?（yyyy-MM-dd）、`endDate`?、`status`?、`page`(默认1)、`size`(默认20，上限100)

响应：

```json
{
  "items": [
    {
      "id": 1, "toolId": 12, "toolName": "报表生成",
      "userId": 3, "username": "zhang", "runtime": "python",
      "sandboxUsed": true, "exitCode": 0, "timedOut": false,
      "status": "SUCCESS", "inputFileCount": 2, "createdAt": "2026-09-10T10:20:33"
    }
  ],
  "total": 1, "page": 1, "size": 20
}
```

> 列表项**不含 output**，避免大 TEXT 拖慢列表。

### 6.2 `GET /api/run-logs/{id}`

- 存在：返回全量（含 `inputFileNames`、`output`）
- 不存在：404

## 七、前端

### 7.1 api 封装 `frontend/src/api/runLogs.js`

```js
import request from './request'
export const listRunLogs = (params) => request.get('/api/run-logs', { params })
export const getRunLog = (id) => request.get(`/api/run-logs/${id}`)
```

（`request.js` 已统一带 token / 处理错误）

### 7.2 页面 `frontend/src/views/RunLogs/index.vue`

- **筛选区**（一行）：用户输入框、工具输入框、起止日期、状态下拉（全部/成功/失败/超时/运行时缺失）、查询按钮、重置按钮
- **表格列**：时间、用户（username/nickname）、工具、runtime、沙箱徽章（沙箱/本地）、状态徽章、退出码、输入文件数、操作（查看）
- **状态徽章配色**（沿用 `useToast.js` 颜色风格）：
  - SUCCESS → 绿（`bg-green-100 text-green-800`）
  - FAILED → 红（`bg-red-100 text-red-800`）
  - TIMEOUT → 橙（`bg-orange-100 text-orange-800`）
  - RUNTIME_MISSING → 灰（`bg-gray-100 text-gray-800`）
- **详情弹窗**：元信息 + `<pre>` 全量 output（等宽、可滚动）
- **分页**：底部页码（风格对齐现有页面）

### 7.3 菜单与路由

- `Layout/index.vue` 菜单数组新增：
  ```js
  { path: '/run-logs', title: '运行日志', icon: 'fas fa-list-alt', requiresAdmin: true }
  ```
  用 `userStore.isAdmin` 控制 `v-show`（沿用 `requiresAuthor` 的写法，`isAdmin` getter 已存在）。
- `router/index.js` 在 Layout 下新增 `{ path: '/run-logs', name: 'RunLogs', component: ... }`。
- 非 admin 直接输入 URL：前端不做跳转（后端 403 兜底）；不引入全局角色路由守卫（保持现状）。

## 八、测试

沿用现有纯 JUnit5（手动构造依赖、`org.junit.jupiter.api.Assertions`、无 Mockito/无 @SpringBootTest）风格：

1. `RunLogServiceTest`
   - status 推导四态（SUCCESS/FAILED/TIMEOUT/RUNTIME_MISSING，含 exitCode=-1、pythonFound=false 组合）
   - `record` 落库字段正确（含截断 inputFileNames、runtime 归一化）
   - `query` 五个过滤条件各自生效 + 组合 + 分页 + 倒序
   - `cleanupExpired` 只删过期行、返回条数正确
2. `ToolServiceTest` 追加：沙箱路径 / 进程级路径各断言生成一条日志；`record` 抛异常时主流程不受影响（模拟）
3. `RunLogControllerTest`：非 admin 403 / admin 放行；list 参数接线、detail 404
4. 回归：`mvn -o test`（58 现有 + 新增全绿）；`npm run build` 成功

## 九、边界与运维

- **隐私**：`output` 可能含用户上传数据内容，仅 admin 可读，后端强校验；不提供导出。
- **主流程保护**：记录日志走 try/catch 吞异常，日志系统瘫痪不影响用户运行。
- **体积估算**：output 已在上游截断（5000 行 / 1MB）。按 20 次运行/天、均值 50KB 计，日均 ~1MB，90 天滚动 ~90MB 量级，单机 MySQL 完全可承受。
- **清理线程**：单实例调度即可（本平台单机部署，无集群）。