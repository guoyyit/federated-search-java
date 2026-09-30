# Federated Search (Java)

多源联合检索服务：同一个查询并发下发到多个**本地**索引源（全部在进程内，无外部服务依赖），
对各源返回的命中做归一化、加权、去重、统一排序、高亮与摘要，并支持快照分页、查询级取消和全局并发上限。
单个源慢或失败不会导致整体查询失败，降级原因在响应中逐源说明。

## 运行

```bash
mvn spring-boot:run
```

Java 17 + Spring Boot 3.4。启动后内置三个本地源：`articles`、`products`、`wiki`，
它们的字段模型和数据新鲜度刻意不同（products 缺标题/URL，wiki 最旧并与 articles 共享文档）。

## HTTP 接口

### 检索

`POST /api/search`

```json
{
  "query": "federated search",
  "page": 1,
  "pageSize": 10,
  "dedupMode": "DOCUMENT_ID",
  "timeoutMs": 800,
  "snapshotId": null
}
```

- `dedupMode`：`DOCUMENT_ID`（默认）、`CONTENT_HASH`、`URL`，为空时使用配置默认值。
- `timeoutMs`：本次请求内**每个源**的统一超时；为空时使用各源独立配置。
- `snapshotId`：首页不传；翻页传首页返回的 id，从同一快照取数。

响应：

```json
{
  "query": "federated search",
  "page": 1,
  "pageSize": 10,
  "totalHits": 7,
  "complete": false,
  "degraded": true,
  "sourceReports": [
    { "source": "articles", "state": "COMPLETE", "reason": "ok", "elapsedMillis": 12, "hitCount": 3 },
    { "source": "products", "state": "TIMEOUT", "reason": "source did not respond within 800 ms", "elapsedMillis": 801, "hitCount": 0 },
    { "source": "wiki",     "state": "FAILED",  "reason": "RuntimeException: index read error", "elapsedMillis": 4, "hitCount": 0 }
  ],
  "hits": [ { "documentId": "...", "source": "...", "finalScore": 1.2, "highlightedTitle": "<em>federated</em> ...", "snippet": "…" } ],
  "snapshotId": "9f2c…"
}
```

### 权重

- `GET /api/search/weights`：查看全部源权重。
- `POST /api/search/weights/{source}`，体 `{ "weight": 2.5 }`：热更新权重，
  **下一个请求立即生效，无需重建任何索引**；权重必须非负。

## 合并与排序语义

1. **打分**：本地源按查询词在标题（权重 3）、正文（权重 1）、URL（权重 2）中的出现次数产出原始分。
2. **归一化（按源独立 min-max）**：每个源内部
   `normalized = (raw - minSource) / (maxSource - minSource)`，结果落在 `[0,1]`；
   该源所有得分相同（极差为 0）时归一化为 `1.0`，避免单条/同分命中被压成 0 而丢失。
3. **加权**：`finalScore = normalized × sourceWeight`。归一化只在源内做，因此各源原始分量纲不同也可比较；
   权重表达“这个源的最佳命中相对其他源最佳命中应放大/缩小多少”。
   权重 0 可用于让某源命中在排序中沉底（仍参与去重占位）。
4. **排序（确定性）**：`finalScore` 降序；平分时依次按 `source` 名称、`documentId` 升序。
   排序只依赖命中值，与源注册顺序、响应到达先后无关。

## 去重规则

同一键值的多条命中只保留一条，**胜出者为 `finalScore` 最高者**，平分按源名、文档 id 决出（与排序同一规则）。

| 模式 | 键 | 典型用途 |
| --- | --- | --- |
| `DOCUMENT_ID` | `DOCUMENT_ID:<documentId>` | 各源共享稳定文档 id |
| `CONTENT_HASH` | `CONTENT_HASH:<sha1(content)>` | id 体系不同但正文相同（镜像/转载） |
| `URL` | `URL:<url>` | 以落地页地址作为同一文档 |

兜底：当所选键的字段缺失/为空时，该命中退化为“源内唯一键”（`fallback:<source>:<documentId>`），
**绝不会因为键为空而把不相关文档错误合并**。内置语料中 articles 与 wiki 同时含有
`doc-federated-search`（id 相同、正文逐字一致、URL 不同），可直接观察三种模式差异。

## 降级语义

每个启用的源都有独立报告：`COMPLETE` / `TIMEOUT` / `FAILED` / `CANCELLED`，附 `reason` 与耗时。
仅当**所有**启用源均为 `COMPLETE` 时 `complete=true`、`degraded=false`；否则整体仍正常返回
（HTTP 200，含可用源的合并结果），`degraded=true` 且逐源给出原因。源抛异常时返回异常类型与消息。

## 高亮与摘要

- 查询词按 Unicode 字母/数字分词（小写匹配，最多 16 个词）。
- 标题高亮：命中词包裹 `<em>…</em>`（可配置）。
- 摘要：围绕最早命中词取最多 `snippet-length` 个字符的窗口，前后以 `…` 表示截断，窗口内高亮命中词。
- 兜底确定性：标题/正文为 null 或空白时输出空串；超长内容只截取窗口；标记严格成对出现；
  查询为空时输出纯文本截断前缀。任何字段异常都不会使合并失败。

## 分页与快照一致性

首页请求把**合并、排序、高亮后的完整命中列表**冻结为快照（默认存活 300 秒）并返回 `snapshotId`。
后续页携带该 id，直接从不可变快照切片，**不再访问任何源**。
因此翻页期间源侧增删改不会造成跨页重复或漏项；不同调用方的快照相互独立，可并发翻页。
快照过期后再翻页会视为新的首页请求（生成新快照）。

## 取消与并发

- 检索接口以 Spring MVC 异步（`Callable`）执行。客户端断开/异步请求异常时，Spring 中断工作线程；
  协调器把该中断（以及显式 `CancellationToken`）作为查询级取消信号。
- 取消后：已下发的子查询通过线程中断 + 协作检查点立即中止，排队中的任务不再产生有效结果，
  报告标记为 `CANCELLED`，守护线程池不残留后台任务。
- 子查询线程池为固定大小 `max-concurrent-requests`，是跨所有并发请求的**全局在途子查询上限**；
  超出部分在线程池队列排队，等待中的任务同样响应取消。
- 本地源在模拟延迟与扫描语料时均切片响应中断/取消（JVM 参数
  `-Dfederated.source.<name>.delay-ms=<ms>` 可给内置源加延迟，便于演示超时）。

## 配置项（`application.properties`）

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.mvc.async.request-timeout` | `-1` | 关闭容器级异步超时；由逐源超时与取消约束请求 |
| `federated.search.default-timeout-ms` | `800` | 未逐源/逐请求配置时的默认源超时 |
| `federated.search.max-concurrent-requests` | `8` | 全局在途子查询线程池大小 |
| `federated.search.snapshot-ttl-seconds` | `300` | 分页快照存活时间 |
| `federated.search.dedup-by` | `DOCUMENT_ID` | 默认去重模式 |
| `federated.search.snippet-length` | `160` | 摘要最大字符数（下限 40） |
| `federated.search.highlight-open` / `…close` | `<em>` / `</em>` | 高亮包裹标记 |
| `federated.search.sources[i].name` | — | 源名（对应本地源 bean：articles/products/wiki） |
| `federated.search.sources[i].enabled` | `true` | 是否参与 fan-out |
| `federated.search.sources[i].weight` | `1.0` | 初始权重，可经 API 热更新 |
| `federated.search.sources[i].timeout-ms` | `800` | 该源独立超时 |

## 测试

```bash
mvn test
```

覆盖场景（见 `src/test/.../tests`）：

- 源超时 + 源失败：整体不失败、状态与原因可解释、超时子查询被中断（`SourceDegradationTest`）。
- 跨源重复：三种去重模式、加权胜出者、排序不受源顺序影响（`DeduplicationTest`）。
- 权重热更新：同一实例内改权重后下一请求排序立即变化（`WeightAdjustmentTest`）。
- 查询取消：取消后快速返回、全部源 `CANCELLED`、worker 已停止（`CancellationTest`）。
- 快照分页：源数据变化时跨页不重不漏，翻页不再打源；多调用方并发翻页互不干扰（`PaginationTest`）。
- 全局并发上限：两并发请求、六源情况下峰值在途子查询不超过池大小（`ConcurrencyLimitTest`）。
- 高亮兜底与确定性（`HighlighterTest`），以及真实 Spring 上下文的 HTTP 端到端
  （合并完成态、跨源去重、权重读写，`SearchApiIntegrationTest`）。
