# Federated Search (Java)

多源联合检索服务。同一条查询会**并发**下发到多个**本地**索引源（`catalog` / `docs` / `news`，均为进程内内存实现，不依赖任何外部服务），
合并各源命中后统一打分、去重、排序，并生成高亮与摘要。单个索引源超时或抛错不会让整体查询失败，调用方可以明确区分“完整结果”与“降级结果”。

## 快速开始

```bash
mvn spring-boot:run
curl 'http://localhost:8080/api/search?q=keyboard&page=0&size=10'
```

## HTTP API

### 搜索 / 翻页

`GET /api/search?q={query}&page={0-based}&size={pageSize}&snapshotId={optional}`

首次请求不要传 `snapshotId`，响应中会返回一个 `snapshotId`；后续翻页带上同一个 `snapshotId`，
即可在同一份快照上稳定翻页。快照过期或不存在时返回 `410 SNAPSHOT_EXPIRED`，需要重新发起首查。

响应：

```json
{
  "query": "keyboard",
  "snapshotId": "8f1c...",
  "page": 0,
  "pageSize": 10,
  "totalHits": 7,
  "hits": [
    {
      "key": "id:p-1001",
      "docId": "p-1001",
      "title": "Wireless Mechanical Keyboard",
      "highlightedTitle": "Wireless Mechanical <em>Keyboard</em>",
      "snippet": "...refreshed wireless mechanical <em>keyboard</em> lineup...",
      "score": 1.2,
      "matchedSources": ["catalog", "docs"],
      "lastUpdated": "2026-09-20T08:00:00Z"
    }
  ],
  "complete": false,
  "degradations": [
    { "sourceId": "news", "reason": "TIMEOUT", "detail": "source exceeded timeout of 1000ms" }
  ]
}
```

- `complete=true`：所有索引源都在各自超时内正常返回。
- `complete=false`：至少一个源降级；`degradations` 逐个列出降级源、原因与可读详情。
- 降级原因枚举：`TIMEOUT`（源超时）、`FAILED`（源抛异常）、`CANCELLED`（查询被取消或中断）。

### 权重即时调整

```bash
curl -X PUT 'http://localhost:8080/api/sources/news/weight?weight=2.5'
curl     'http://localhost:8080/api/sources/news/weight'
```

权重保存在内存中的 `SourceWeightRegistry`，**下一次查询立即生效，无需重建任何索引**。
权重必须是有限的非负数（`NaN`、`Infinity`、负数返回 `400`）；未配置/未登记的源默认权重为 `1.0`。

## 合并语义

### 1. 并发下发与每源超时

- `SourceDispatcher` 将每个源的查询包装为可中断的 `FutureTask`，提交到一个共享的**固定大小线程池**。
- 线程池大小由 `federated.search.global-concurrency` 决定，是“所有请求 × 所有源”维度上的**全局在途子查询上限**；
  超出上限的子查询在队列中等待，不会额外创建线程。
- 每个源拥有独立超时 `federated.search.sources.<id>.timeout`（ISO-8601，如 `PT1S`）。
  超时由共享调度器触发 `future.cancel(true)`，运行中的源会收到线程中断。

### 2. 失败隔离与降级可解释

- 某源超时 → `TIMEOUT`；源内部抛异常 → `FAILED`（detail 含异常信息）；调用方取消 → `CANCELLED`。
- 失败源只是缺席合并，其余源结果照常返回；响应通过 `complete` + `degradations` 明确表达降级。

### 3. 得分归一化与权重

不同源的原始分量纲不同，不能直接比较。`ScoreNormalizer` 对**每个源本次返回的命中集合**做 min-max 归一化：

```
normalized = (raw - min(raw...该源)) / (max(raw...) - min(raw...))
final      = normalized * weight(source)
```

- 归一化后每个源的最高分恒为 `1.0`，再乘以该源权重；即“源内相对相关性 × 源权重”。
- 当某源只返回一条命中，或该源所有原始分相同（`max == min`）时，归一化值定义为 `1.0`，避免除零并保证单条结果不因量纲被抹平。
- 权重是线性倍率：`weight=2` 的源，其最佳命中理论上可达 `2.0` 分；`weight=0` 等价于该源结果沉底（仍参与去重与 matchedSources 统计）。
- 权重调整只改变后续查询的 `final` 分，不触碰任何索引数据。

### 4. 跨源去重（可配置）

`federated.search.dedup-strategy` 可选：

- `DOC_ID`（默认）：key = `id:<docId>`。表示“同一业务文档”在不同源复用同一 docId。
- `TITLE_NORMALIZED`：key = `title:<归一化标题>`；标题转小写、去除标点、压缩空白，
  因此 `Wireless Keyboard!` 与 `wireless   keyboard` 视为同一文档。
- `CONTENT_FINGERPRINT`：key = `content:<SHA-256 前 16 位>`，对归一化后的正文取指纹。

当首选字段缺失时，按固定顺序回退，保证异常/脏数据不会让合并失败：

| 策略 | 回退顺序 |
| --- | --- |
| `DOC_ID` | docId → 归一化标题 → 正文指纹 → `unknown:<sourceId>:<rawScore>` |
| `TITLE_NORMALIZED` | 归一化标题 → docId → 正文指纹 → unknown 兜底 |
| `CONTENT_FINGERPRINT` | 正文指纹 → 归一化标题 → docId → unknown 兜底 |

同一 key 的多个命中只保留一次，但 `matchedSources` 会列出**所有**包含该文档的源（按源名字典序）。
代表文档选用 `final` 加权分最高的那条（其标题/正文用于渲染）。

### 5. 确定性排序

排序与源返回顺序、线程到达时序完全无关。比较键依次为：

1. `score`（加权归一化分，降序）
2. `lastUpdated`（文档时间戳，新的优先；缺失按 `Instant.EPOCH` 兜底）
3. `title`（大小写不敏感字典序）
4. `key`（去重 key，最终兜底，保证全序）

### 6. 高亮与摘要的兜底行为

- 查询词以 Unicode 字母/数字切分、小写、去重。
- 标题/正文缺失：标题渲染为 `Untitled`，摘要为空串 `""`。
- 正文超长：以首个命中词为中心截取长度约 `snippet-length`（最小 40）的窗口，
  超窗前后缀以 `...` 表示，且在单词边界截断；无命中时取等长前缀。
- 高亮标记由 `highlight-prefix` / `highlight-suffix` 配置（默认 `<em>` / `</em>`），配置为 null 时回退 `[` / `]`。

### 7. 快照分页

- 首次查询：并发取数 → 合并 → 得到一条**全量、已排序**的命中间隔，写入 `SnapshotStore`（含 `complete` 与 `degradations`）。
- 翻页：只对该快照的不可变列表做 `subList(from, to)`，**不再访问任何源**。
  因此翻页期间源侧数据增删改不会造成跨页重复或漏项。
- 快照有 TTL（`snapshot-ttl`，默认 10 分钟）与容量上限（`snapshot-max`，默认 100，超出时淘汰最旧快照）。
  过期/未知 snapshotId 返回 `410 SNAPSHOT_EXPIRED`。
- 页码从 0 开始；越界页返回空 `hits`，但 `totalHits` 仍为快照总数。

### 8. 查询级取消与资源回收

- 编程式入口 `FederatedSearchService.submit(request)` 返回 `SearchTask`：
  - `await()` 等待全部源；`await(Duration)` 给出请求级超时，到时会 `cancel(true)` 尚未结束的源并返回已完成的部分结果；
  - `cancel()` 立即中断所有在途子查询。
- 被取消的源通过 `InterruptedException` 响应中断（本地源使用可中断的 `Thread.sleep`/匹配循环），
  不会残留后台任务占用线程；全局线程池在 Bean 销毁时 `shutdownNow()`。

## 配置项（`application.properties`）

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `federated.search.global-concurrency` | `8` | 全局在途源查询线程数上限 |
| `federated.search.dedup-strategy` | `DOC_ID` | `DOC_ID` / `TITLE_NORMALIZED` / `CONTENT_FINGERPRINT` |
| `federated.search.snippet-length` | `160` | 摘要目标长度（最小钳制为 40） |
| `federated.search.highlight-prefix` | `<em>` | 高亮前缀 |
| `federated.search.highlight-suffix` | `</em>` | 高亮后缀 |
| `federated.search.snapshot-ttl` | `PT10M` | 快照存活时间（ISO-8601） |
| `federated.search.snapshot-max` | `100` | 最多保留快照数，超出淘汰最旧 |
| `federated.search.sources.<id>.timeout` | `PT2S` | 单源超时（ISO-8601 Duration） |
| `federated.search.sources.<id>.weight` | `1.0` | 单源初始权重（运行期可被 PUT 覆盖） |

内置源 id：`catalog`、`docs`、`news`。

## 代码结构

```
model/        请求/响应/命中/降级记录等不可变 record
source/       SearchIndexSource SPI
local/        三个内存索引源实现（含跨源重复数据，可构造延迟）
config/       FederatedSearchProperties、DedupStrategy、DedupKeyResolver
federation/   SourceDispatcher/SourceInvocation（并发、超时、取消）
              ScoreNormalizer、ResultMerger、Highlighter
              SourceWeightRegistry、SnapshotStore、FederatedSearchService
web/          REST 控制器与异常处理
```

## 测试

```bash
mvn test
```

覆盖场景（均为自包含、无外部依赖）：

- `DegradationIT`：源超时、源异常、全成功的 complete 标记
- `DedupIT`：跨源同 docId 去重、标题归一化、字段缺失兜底、到达时序不影响排序
- `WeightIT`：运行期调权立即改变排序、非法权重拒绝、未知源默认权重
- `CancellationIT`：`cancel()` 与请求级超时中断所有在途源
- `ConcurrencyCapIT`：全局并发上限不被突破且线程池被打满
- `PaginationIT`：快照翻页无重无漏、越界页、源数据并发变更下快照稳定
- `HighlighterIT`：高亮、超长截断、无命中前缀、空字段兜底
- `SearchHttpIT`：REST 端到端（搜索/翻页/调权/410）
