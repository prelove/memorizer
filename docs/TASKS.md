# Memorizer — 功能改进与开发路线图

> 本文档对项目（桌面版 + 内嵌 Web 服务器 + PWA）现有功能做深度审视，列出各方向的改进任务，并对每项任务的可行性、优先级和实现要点进行分析，供未来开发迭代参考。

---

## 目录

1. [Web 界面 / SSR 前端](#1-web-界面--ssr-前端)
2. [REST API 完善](#2-rest-api-完善)
3. [SRS 算法增强](#3-srs-算法增强)
4. [桌面 UI 改进](#4-桌面-ui-改进)
5. [PWA / 移动端改进](#5-pwa--移动端改进)
6. [数据管理 & 导入导出](#6-数据管理--导入导出)
7. [安全 & 认证](#7-安全--认证)
8. [性能优化](#8-性能优化)
9. [构建 & CI/CD](#9-构建--cicd)
10. [可行性总结矩阵](#10-可行性总结矩阵)

---

## 1. Web 界面 / SSR 前端

### T-WEB-01 · Web 统计仪表板页面
**描述**：在 `/web/stats` 增加统计仪表板，展示与桌面 `DashboardPanel` 相似的数据：最近 7 天日复习量、卡片状态分布、评分分布、漏习率趋势。  
**现状**：`/web` 主页仅有三个快捷卡片（Decks / Browse / Study），无任何图表展示。`ChartRepository` 和 `StatsRepository` 已提供所有需要的 SQL 查询。  
**实现要点**：
- 在 `SsrController` 或新建 `WebStatsController` 中注册 `GET /web/stats`。
- 后端查询复用 `ChartRepository`，将结果序列化为 JSON 嵌入模板。
- 使用轻量 Canvas 库（如 Chart.js CDN）渲染折线/柱状图，或用纯 CSS 条形图避免外部依赖。
- 在 `layout.html` 导航栏添加 "Stats" 链接。

**优先级**：⭐⭐⭐ 高  
**可行性**：✅ 很高——后端数据完整，仅需前端渲染层。

---

### T-WEB-02 · 笔记全文检索与过滤
**描述**：Browse 页（`/web/notes`）支持按关键词搜索正面/背面/示例字段，支持按牌组过滤。  
**现状**：`/web/notes` 已有基本列表，但缺乏搜索框；`NoteRepository` 已有 LIKE 查询支持。  
**实现要点**：
- 在页面顶部添加搜索表单（`q=` 参数）和牌组下拉筛选（`deck=` 参数）。
- `GET /web/notes?q=word&deck=1` 将参数透传至 SQL `WHERE front LIKE ? OR back LIKE ?`。
- 添加搜索高亮（简单字符串替换 + `<mark>` 标签，WebUtil.escape 后处理）。

**优先级**：⭐⭐⭐ 高  
**可行性**：✅ 很高——改动仅在 SSR 层。

---

### T-WEB-03 · 笔记列表分页
**描述**：Browse 页对大型牌组（千级+卡片）添加分页，避免一次性加载全部数据。  
**现状**：`/web/notes` 一次返回所有笔记，大数据量时渲染缓慢。  
**实现要点**：
- URL 参数 `page=` + `size=`（默认 50），SQL 添加 `LIMIT ? OFFSET ?`。
- 渲染上一页/下一页链接，保留 `q` 和 `deck` 参数。

**优先级**：⭐⭐ 中  
**可行性**：✅ 很高——标准分页模式。

---

### T-WEB-04 · Web 端笔记编辑
**描述**：Browse 页每行提供"编辑"按钮，弹出（或跳转独立页面）内联编辑表单，支持修改正面、背面、示例等所有字段。  
**现状**：`/api/notes/update` 已存在，但 Web UI 中无可视编辑入口。  
**实现要点**：
- `GET /web/notes/{id}/edit` 渲染编辑表单，`POST /web/notes/{id}/edit` 提交后重定向（PRG 模式）。
- 或使用 JavaScript 局部渲染行内编辑（`fetch` + DOM 替换）。

**优先级**：⭐⭐⭐ 高  
**可行性**：✅ 高。

---

### T-WEB-05 · Web 学习模式增强
**描述**：Web 学习页（`/web/study`）增加：键盘快捷键（1/2/3/4 评分，空格翻转）、当前牌组选择、今日进度显示、空状态提示。  
**现状**：基础翻卡和评分存在，但用户体验相较桌面版有差距。  
**实现要点**：
- 在 `layout.html` 或页面脚本添加 `keydown` 监听，映射到评分按钮的 `click()`。
- `GET /web/study?deck=id` 透传牌组参数至 `StudyService`。
- 进度条：`<progress>` 元素 + 服务端注入 `reviewedToday / dailyTarget`。

**优先级**：⭐⭐⭐ 高  
**可行性**：✅ 高——纯前端脚本，复用现有 API。

---

### T-WEB-06 · Web 端新建笔记 / 新建牌组
**描述**：在 Decks 页添加"创建牌组"按钮；在 Browse 页或 Study 页添加"新建笔记"入口。  
**现状**：`/web/decks/create` 已有 POST 端点，但 Decks 列表页仅展示，缺少可见的 Create 表单；Browse 页无新建入口。  
**实现要点**：
- Decks 页顶部添加内联表单（输入牌组名，提交 POST）。
- Browse 页添加"+ New Note"按钮，跳转至 `GET /web/notes/new` 表单页。

**优先级**：⭐⭐ 中  
**可行性**：✅ 很高。

---

### T-WEB-07 · 响应式 / 移动端布局优化
**描述**：Web 界面在手机浏览器（320–480 px 宽）下改善导航栏折叠（汉堡菜单）、按钮触控大小和表格横向滚动。  
**现状**：`app.css` 已有 `@media (max-width:640px)` 基础规则，但导航栏在小屏下直接 `display:none`。  
**实现要点**：
- 添加汉堡图标（纯 CSS + checkbox hack 或极简 JS）展开/收起导航。
- 学习卡片按钮最小高度 44 px（iOS 触控规范）。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高——CSS/JS 改动，不涉及后端。

---

## 2. REST API 完善

### T-API-01 · API 文档端点（简易 API Explorer）
**描述**：在 `/api/docs` 提供一个静态 HTML 页面，列出所有 API 端点、参数、示例请求/响应。  
**现状**：无任何 API 文档，PWA 和第三方集成靠阅读源码理解接口。  
**实现要点**：
- 手写静态 HTML 文档（避免引入 Swagger 依赖，保持 Java 8 兼容性）。
- 或生成 JSON（类 OpenAPI 3.0 Lite）并用极简前端渲染为可交互页面。
- 注册 `GET /api/docs` 返回文档页面。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高（静态 HTML 方案极低风险）。

---

### T-API-02 · API 版本前缀
**描述**：将所有 API 路径迁移至 `/api/v1/`，保留旧路径重定向兼容，为未来破坏性变更预留空间。  
**现状**：路径为 `/api/decks`、`/api/notes` 等无版本前缀。  
**实现要点**：
- 新增带 `/api/v1/` 前缀的路由，旧路由 301 重定向。
- 更新 PWA `src/` 中所有 `fetch('/api/...')` 调用。

**优先级**：⭐ 低（破坏性迁移成本高）  
**可行性**：⚠️ 中——需同步更新 PWA 和 WebApiTest。

---

### T-API-03 · 统一 API 错误响应格式
**描述**：所有 API 端点在出错时返回统一的 JSON 结构：`{"error": "message", "code": "DECK_NOT_FOUND"}`，而非裸字符串或 HTTP 状态码。  
**现状**：错误响应不一致（有的返回字符串，有的返回 JSON，有的直接 500）。  
**实现要点**：
- 定义 `ApiError` 工具方法 `WebUtil.error(ctx, int status, String code, String msg)`。
- 逐一替换现有错误分支。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高。

---

### T-API-04 · 批量操作接口
**描述**：添加批量删除笔记（`POST /api/notes/batch-delete`，body: `{"ids":[1,2,3]}`）和批量移动牌组（`POST /api/notes/batch-move`）接口。  
**现状**：目前仅支持单条删除/更新。  
**实现要点**：
- 接收 JSON 数组 ID，在单个事务内执行多条 `DELETE`/`UPDATE`。
- 在 Web Browse 页添加复选框 + 批量操作按钮。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高。

---

### T-API-05 · 导出接口（CSV / JSON）
**描述**：`GET /api/export/notes?format=csv&deck=id` 返回笔记数据文件供下载；`GET /api/export/review-log?format=csv` 导出复习日志。  
**现状**：桌面版有 Excel 导出，但 Web / API 层无下载入口。  
**实现要点**：
- CSV 行生成复用现有 Repository 查询。
- 设置 `Content-Disposition: attachment` 响应头。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高。

---

### T-API-06 · 统计数据 API
**描述**：`GET /api/stats/summary` 返回仪表板摘要（到期/新卡数量、今日复习、日目标完成率等），供 PWA 展示统计卡片，也供 T-WEB-01 使用。  
**现状**：`StatsRepository` 已有数据，但未暴露 API 端点。  
**实现要点**：
- 新增端点查询 `StatsRepository` + `PlanService.todayStats()`，返回 JSON。

**优先级**：⭐⭐⭐ 高  
**可行性**：✅ 很高。

---

## 3. SRS 算法增强

### T-SRS-01 · FSRS 算法支持（可选后端）
**描述**：在 `SrsEngine` 旁增加 `FsrsEngine`，实现 FSRS-4.5 算法（Four Parameters of Forgetting Curve Scheduling System），用户可在偏好中切换。  
**现状**：当前使用简化版 SM-2，ease 固定初始值 2.5，步进序列不可配置。  
**实现要点**：
- 参考 FSRS 开源 Java 实现，独立封装 `FsrsEngine.java`。
- `Config` 新增 `app.study.srs-algorithm=sm2|fsrs` 选项。
- `StudyService.rate()` 根据配置选择引擎。
- 迁移路径：新卡片使用 FSRS，旧卡片保持 SM-2 直到下次复习。

**优先级**：⭐⭐ 中（对重度用户价值高）  
**可行性**：⚠️ 中——算法本身不复杂，但需要充分测试调度正确性。

---

### T-SRS-02 · 可配置学习步进序列
**描述**：允许用户自定义 "学习阶段" 步进（如 Anki 默认 `1m 10m`）和 "重学阶段" 步进，而不是当前固定的 10 分钟。  
**现状**：AGAIN 固定为 10 分钟。  
**实现要点**：
- `application.properties` / 偏好新增 `app.study.learn-steps=1m 10m` 解析为 `long[]` 分钟数组。
- `SrsEngine` 按步进阶段索引（存在 `Card.learningStep` 字段，需 DB 迁移）调度。

**优先级**：⭐⭐ 中  
**可行性**：⚠️ 中——需新增 DB 迁移字段 `learning_step`。

---

### T-SRS-03 · 同源卡片遮蔽（Bury Siblings）
**描述**：同一笔记（Note）的多张卡片在同一天不同时出现，避免同一知识点短时间内重复复习。  
**现状**：同一笔记的正/反两面卡片可能同天出现。  
**实现要点**：
- `PlanService.buildToday()` 构建计划时，记录已选 `note_id`，后续相同 `note_id` 的卡片自动推迟 1 天。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高——逻辑集中在 `PlanService`，无需 DB 变更。

---

### T-SRS-04 · 每日负载均衡
**描述**：未来到期卡片数量波动较大时，自动将高峰日的部分卡片提前/推迟，使每天复习量更平稳。  
**现状**：严格按到期时间调度，高峰期可能单日出现数百张到期卡。  
**实现要点**：
- `PlanService` 查询未来 7 天到期预测，若某日超过 `dailyTarget * 1.5` 则提前散布。

**优先级**：⭐ 低  
**可行性**：⚠️ 中——调度逻辑较复杂。

---

## 4. 桌面 UI 改进

### T-DESK-01 · Stealth 横幅内快速添加笔记
**描述**：在 Stealth 横幅右侧或右键菜单添加"快速添加"入口，直接弹出最小化新建笔记对话框，无需切换到主窗口。  
**现状**：新建笔记须通过 Main Window → Data → New Entry。  
**实现要点**：
- `StealthStage` 添加小图标按钮或快捷键（如 `A`），触发 `DialogFactory.showAddEntry()`。
- 弹出对话框复用 `EditorStage` 或新建轻量快速输入对话框。

**优先级**：⭐⭐⭐ 高（高频需求）  
**可行性**：✅ 高。

---

### T-DESK-02 · 标签（Tags）支持
**描述**：笔记添加 `tags` 字段，支持多标签（逗号分隔），在 Stealth 横幅、Browse 页、Study 过滤中可按标签筛选。  
**现状**：`Note` 模型中 `tags` 字段已存在但未被 UI 使用。  
**实现要点**：
- `V4__tags_index.sql` 迁移：为 `notes.tags` 添加全文索引（H2 支持）。
- Browse 页添加标签筛选下拉。
- Stealth 偏好中添加 "按标签过滤" 选项。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高——字段已存在，主要是 UI 接入。

---

### T-DESK-03 · 学习历史回顾界面
**描述**：Main Window 添加"历史"标签页，显示最近 N 次复习的卡片（时间、正面、评分），支持点击重新查看或标记。  
**现状**：`review_log` 表已记录所有复习，但没有 UI 展示。  
**实现要点**：
- 新建 `HistoryPanel.java`，类似 `PlanPanel` 结构，查询 `review_log JOIN cards JOIN notes`。
- 在 `MainStage` TabPane 添加 "History" Tab。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高。

---

### T-DESK-04 · 字体大小动态调整
**描述**：Stealth 横幅正面/背面文字字体大小可通过快捷键（`+`/`-`）或偏好滑块实时调整。  
**现状**：`Config.getFontScale()` 已实现字体缩放比例，但没有实时键盘快捷键调整。  
**实现要点**：
- `StealthStage` 键盘处理添加 `+`/`-` 键，修改 `Config.setFontScale()`，调用现有 `applyFontScale()`。

**优先级**：⭐⭐ 中  
**可行性**：✅ 很高——基础设施已存在。

---

### T-DESK-05 · 牌组徽章与热键循环
**描述**：Stealth 横幅显示当前牌组名（小徽章），快捷键（如 `D`）循环切换牌组，无需打开主窗口。  
**现状**：在 `breakpoints.md` 中已作为可选拉伸任务提及。  
**实现要点**：
- `StealthStage` 在顶部区域添加牌组徽章 `Label`，绑定 `Config.get("app.deck.filter")`。
- `D` 键触发 `DeckRepository.findAll()` 获取列表，循环更新 `Config.set("app.deck.filter", ...)`，重新加载计划。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高。

---

### T-DESK-06 · 全局搜索快捷键
**描述**：系统托盘右键菜单或 `F7` 快捷键打开全局搜索对话框，输入关键词即时显示匹配笔记，支持回车打开编辑。  
**现状**：搜索只能在主窗口 Browse Tab 内操作。  
**实现要点**：
- 新建 `GlobalSearchStage`（小型浮动窗口，宽 480 px），输入框 + 结果列表。
- `TrayActions` 注册全局快捷键（JavaFX 无原生支持，可用 JNativeHook 或仅限托盘触发）。

**优先级**：⭐ 低（依赖第三方库）  
**可行性**：⚠️ 中——全局快捷键依赖 `JNativeHook` 等 native 库。

---

## 5. PWA / 移动端改进

### T-PWA-01 · 离线学习队列持久化
**描述**：PWA 在离线时能继续复习已缓存的卡片，待重新联网后批量同步评分结果。  
**现状**：PWA 有 Service Worker，但离线时无法完整学习（依赖实时 API 调用）。  
**实现要点**：
- 在 Dexie 本地 DB 新增 `pending_reviews` 表。
- 评分时先写本地，再尝试 `/api/reviews`；联网后批量提交。
- 服务端 `POST /api/reviews/batch` 接受数组。

**优先级**：⭐⭐⭐ 高（移动端核心体验）  
**可行性**：⚠️ 中——需 PWA 和服务端协作改动。

---

### T-PWA-02 · 推送通知 / Web Push 提醒
**描述**：桌面端定时触发 Web Push 通知，提醒用户到期卡片数量（如"今日有 23 张到期卡"）。  
**现状**：桌面端有系统托盘提醒，但移动端无推送。  
**实现要点**：
- 服务端实现 `POST /api/push/subscribe`（保存 PushSubscription 至 DB）。
- 使用 `web-push` Java 库（如 `nl.martijndwars:web-push`）发送 VAPID 推送。
- PWA Service Worker 实现 `push` 事件监听并展示通知。

**优先级**：⭐ 低（需额外依赖，且仅限 HTTPS 环境）  
**可行性**：⚠️ 中——VAPID 密钥管理及推送库集成有一定复杂度。

---

### T-PWA-03 · PWA 统计页面
**描述**：PWA 添加统计/仪表板页面，展示今日进度、7 天复习趋势图（Canvas 折线图）。  
**现状**：PWA 无任何统计展示。  
**实现要点**：
- 新增 Vue 组件 `StatsView.vue`，调用 `GET /api/stats/summary`（T-API-06）。
- 使用 `Chart.js` 或 `uPlot`（轻量）渲染趋势图。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高（依赖 T-API-06）。

---

### T-PWA-04 · 同步冲突解决策略
**描述**：当桌面和移动端在离线期间对同一笔记均有修改时，提供可视化冲突解决 UI（显示两个版本，用户选择保留哪个）。  
**现状**：`/api/sync` 采用 `last-write-wins` 策略，静默覆盖旧版本。  
**实现要点**：
- 同步响应中返回冲突列表 `{"conflicts": [...]}`。
- PWA 检测到冲突后展示 diff 面板，用户确认。

**优先级**：⭐ 低（冲突概率低）  
**可行性**：⚠️ 中——需修改同步协议，工作量较大。

---

## 6. 数据管理 & 导入导出

### T-DATA-01 · Anki APKG 导入
**描述**：支持从 Anki `.apkg` 文件导入牌组、笔记和卡片（含调度信息）。  
**现状**：只有 Excel/CSV 导入，无 Anki 兼容性。  
**实现要点**：
- `.apkg` 为 ZIP 格式，内含 SQLite DB（`collection.anki2`）和媒体文件。
- 用 `sqlite-jdbc` 读取 `notes`/`cards`/`decks` 表，映射到本项目 Schema。
- 在 `importer/` 包新建 `AnkiImporter.java`；UI 入口：Data → Import → From Anki (.apkg)。

**优先级**：⭐⭐⭐ 高（用户迁移需求强烈）  
**可行性**：⚠️ 中——Anki schema 字段复杂，字段映射需仔细处理，媒体文件暂可忽略。

---

### T-DATA-02 · Anki APKG 导出
**描述**：将本项目笔记/卡片导出为 Anki 可导入的 `.apkg` 文件。  
**现状**：只有 Excel/CSV 导出。  
**实现要点**：
- 创建内存 SQLite DB，写入 Anki schema，打包为 ZIP。
- 导出接口：Data → Export → As Anki (.apkg)。

**优先级**：⭐⭐ 中  
**可行性**：⚠️ 中——需内嵌 SQLite 写入逻辑。

---

### T-DATA-03 · 数据库在线备份与恢复
**描述**：主窗口 Data 菜单添加"Backup Database"和"Restore Database"功能，支持将 H2 数据库导出为单一 `.zip` 存档并恢复。  
**现状**：用户须手动复制 `data/memo.mv.db` 文件进行备份。  
**实现要点**：
- 备份：`H2 BACKUP TO '/path/backup.zip'` SQL 命令（H2 原生支持）。
- 恢复：关闭 H2 连接，替换文件，重新初始化 `Database`。
- UI：`JFileChooser` 选择目标路径。

**优先级**：⭐⭐⭐ 高（数据安全）  
**可行性**：✅ 很高——H2 内置 BACKUP 语句。

---

### T-DATA-04 · CSV 导入列映射 UI
**描述**：CSV 导入时，提供可视化列映射对话框（将 CSV 列拖拽/下拉对应到 front/back/reading 等字段），而不是依赖固定列顺序。  
**现状**：`importer/` 包要求 CSV 按固定列顺序排列。  
**实现要点**：
- 预览 CSV 前 3 行，每列显示下拉框选择映射目标字段。
- 新建 `CsvMappingDialog.java` JavaFX 对话框。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高。

---

## 7. 安全 & 认证

### T-SEC-01 · Web 界面访问令牌保护
**描述**：启用 sync server 后，`/web/*` 路由要求用户输入访问密码（简单 HTTP Basic Auth 或 Session Cookie），防止局域网内其他设备随意访问数据。  
**现状**：任何局域网设备只要知道 IP:port 即可无认证访问所有数据。  
**实现要点**：
- `Config` 添加 `app.web.auth.password`（默认空，禁用认证）。
- Javalin `before()` 拦截器检查 `Authorization` 头或 Session Cookie。
- 登录页 `GET /web/login`，`POST /web/login` 验证密码，种 Cookie（有效期 24h）。

**优先级**：⭐⭐⭐ 高（局域网隐私保护）  
**可行性**：✅ 高——Javalin before() 拦截器模式清晰。

---

### T-SEC-02 · API 令牌轮换
**描述**：配对令牌（Pairing Token）支持手动轮换（按钮"Revoke & Renew Token"），并提供令牌过期时间展示。  
**现状**：`PairingManager` 已有过期逻辑，但 `/pair` 页面无手动撤销按钮。  
**实现要点**：
- `GET /api/pair/revoke` 使当前令牌失效，生成新令牌返回。
- `/pair` 页面添加"Revoke Token"按钮，`fetch` 后刷新页面。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高。

---

### T-SEC-03 · 速率限制（Rate Limiting）
**描述**：对 `/api/reviews`、`/api/sync` 等写入端点添加简单速率限制（如每分钟最多 300 次请求），防止意外循环调用或恶意滥用。  
**现状**：无任何速率限制。  
**实现要点**：
- Javalin `before()` 拦截器 + 内存 `ConcurrentHashMap<String, AtomicInteger>` 计数器（每分钟重置）。

**优先级**：⭐ 低（局域网场景风险较低）  
**可行性**：✅ 高。

---

## 8. 性能优化

### T-PERF-01 · 数据库连接池
**描述**：将 `Database.get()` 单连接模式升级为 HikariCP 连接池（2–4 个连接），提升多并发请求（PWA + Web UI 同时访问）下的响应速度。  
**现状**：`Database.java` 单例 `Connection`，所有请求串行共享，在高并发下可能产生队列等待。  
**实现要点**：
- 添加 HikariCP 依赖（`com.zaxxer:HikariCP:4.0.3`，Java 8 兼容）。
- `Database` 改为 `DataSource` 封装，`get()` 改为 `getConnection()` 借出连接，用完归还（try-with-resources）。
- 检查所有 `Database.get()` 调用处确保连接正确关闭。

**优先级**：⭐⭐ 中  
**可行性**：⚠️ 中——改动面较广，需仔细审查每处连接用法。

---

### T-PERF-02 · 卡片预加载（Study 批次预取）
**描述**：学习会话开始时一次性预取下一批（如 10 张）卡片到内存，而不是每次评分后再查询数据库，减少学习流中的明显延迟。  
**现状**：`StudyService` 每次 `next()` 调用实时查询 DB。  
**实现要点**：
- `StudyService` 内部维护 `Queue<CardView>` 预取缓冲区，评分后异步补充。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高。

---

### T-PERF-03 · Web 静态资源缓存头
**描述**：`/web/static/*` 文件（CSS、JS、图标）添加 `Cache-Control: max-age=86400` 响应头，减少重复下载。  
**现状**：`SsrController` 静态文件无缓存头设置。  
**实现要点**：
- 在 `/web/static/*` 处理器中添加 `ctx.header("Cache-Control", "public, max-age=86400")`。

**优先级**：⭐ 低（影响较小）  
**可行性**：✅ 很高——单行改动。

---

## 9. 构建 & CI/CD

### T-CI-01 · GitHub Actions 工作流
**描述**：在 `.github/workflows/ci.yml` 添加 CI 工作流：触发条件为 push/PR，执行 `mvn -s settings.xml -Popenjfx -Djavafx.platform=linux verify`，报告测试结果。  
**现状**：无 CI 配置，本地手动运行测试。  
**实现要点**：
```yaml
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - run: mvn -s settings.xml -Popenjfx -Djavafx.platform=linux verify
```

**优先级**：⭐⭐⭐ 高  
**可行性**：✅ 很高——只需添加 YAML 文件，无代码改动。

---

### T-CI-02 · 测试覆盖率报告
**描述**：集成 JaCoCo，在 CI 中生成覆盖率报告并上传为 Artifact，目标覆盖核心路径（`SrsEngine`、`PlanService`、`StudyService`）达到 70%+。  
**现状**：无覆盖率统计。  
**实现要点**：
- `pom.xml` 添加 `jacoco-maven-plugin`，`verify` 阶段生成报告。
- CI 工作流上传 `target/site/jacoco/` 为 GitHub Actions Artifact。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高。

---

### T-CI-03 · 自动化发布打包
**描述**：打 Git Tag（如 `v0.5.0`）时，CI 自动构建 shaded JAR 并创建 GitHub Release，附带下载链接。  
**现状**：发布须手动构建后上传。  
**实现要点**：
- 在 `ci.yml` 添加 `on: push: tags: ['v*']` 触发。
- 使用 `softprops/action-gh-release` Action 上传 `target/*.jar`。

**优先级**：⭐⭐ 中  
**可行性**：✅ 高。

---

### T-CI-04 · 手动 QA 检查清单文档
**描述**：在 `docs/QA_CHECKLIST.md` 记录完整手工验证步骤：配对流程、HTTPS 证书安装、QR 解码、CRUD 全流程、PWA 同步对账。  
**现状**：`tasks/CONTINUE_NEXT_SESSION.md` 中只有简略提示。  
**实现要点**：编写 Markdown 文档，无代码改动。

**优先级**：⭐⭐ 中  
**可行性**：✅ 很高。

---

## 10. 可行性总结矩阵

| 任务 ID | 标题 | 优先级 | 可行性 | 估算工作量 | 依赖 |
|---------|------|--------|--------|-----------|------|
| T-WEB-01 | Web 统计仪表板 | ⭐⭐⭐ | ✅ 很高 | S (0.5d) | T-API-06 |
| T-WEB-02 | 笔记全文检索 | ⭐⭐⭐ | ✅ 很高 | S (0.5d) | — |
| T-WEB-03 | 笔记列表分页 | ⭐⭐ | ✅ 很高 | XS (2h) | — |
| T-WEB-04 | Web 端笔记编辑 | ⭐⭐⭐ | ✅ 高 | S (1d) | — |
| T-WEB-05 | Web 学习模式增强 | ⭐⭐⭐ | ✅ 高 | S (0.5d) | — |
| T-WEB-06 | Web 端新建入口 | ⭐⭐ | ✅ 很高 | XS (2h) | — |
| T-WEB-07 | 响应式布局优化 | ⭐⭐ | ✅ 高 | S (0.5d) | — |
| T-API-01 | API 文档端点 | ⭐⭐ | ✅ 高 | S (0.5d) | — |
| T-API-02 | API 版本前缀 | ⭐ | ⚠️ 中 | M (2d) | — |
| T-API-03 | 统一错误响应 | ⭐⭐ | ✅ 高 | S (0.5d) | — |
| T-API-04 | 批量操作接口 | ⭐⭐ | ✅ 高 | S (1d) | — |
| T-API-05 | 导出接口 | ⭐⭐ | ✅ 高 | S (0.5d) | — |
| T-API-06 | 统计数据 API | ⭐⭐⭐ | ✅ 很高 | XS (2h) | — |
| T-SRS-01 | FSRS 算法 | ⭐⭐ | ⚠️ 中 | L (5d) | — |
| T-SRS-02 | 可配置学习步进 | ⭐⭐ | ⚠️ 中 | M (2d) | DB 迁移 |
| T-SRS-03 | 遮蔽同源卡片 | ⭐⭐ | ✅ 高 | S (0.5d) | — |
| T-SRS-04 | 每日负载均衡 | ⭐ | ⚠️ 中 | M (2d) | — |
| T-DESK-01 | Stealth 快速添加 | ⭐⭐⭐ | ✅ 高 | S (0.5d) | — |
| T-DESK-02 | 标签支持 | ⭐⭐ | ✅ 高 | M (2d) | DB 迁移 |
| T-DESK-03 | 历史回顾界面 | ⭐⭐ | ✅ 高 | S (1d) | — |
| T-DESK-04 | 字体大小快捷键 | ⭐⭐ | ✅ 很高 | XS (1h) | — |
| T-DESK-05 | 牌组徽章与热键 | ⭐⭐ | ✅ 高 | S (0.5d) | — |
| T-DESK-06 | 全局搜索快捷键 | ⭐ | ⚠️ 中 | M (2d) | JNativeHook |
| T-PWA-01 | 离线学习队列 | ⭐⭐⭐ | ⚠️ 中 | M (3d) | T-API-04 |
| T-PWA-02 | Web Push 推送 | ⭐ | ⚠️ 中 | L (5d) | VAPID 库 |
| T-PWA-03 | PWA 统计页面 | ⭐⭐ | ✅ 高 | S (1d) | T-API-06 |
| T-PWA-04 | 同步冲突解决 | ⭐ | ⚠️ 中 | L (5d) | — |
| T-DATA-01 | Anki APKG 导入 | ⭐⭐⭐ | ⚠️ 中 | L (4d) | sqlite-jdbc |
| T-DATA-02 | Anki APKG 导出 | ⭐⭐ | ⚠️ 中 | M (3d) | sqlite-jdbc |
| T-DATA-03 | 数据库备份恢复 | ⭐⭐⭐ | ✅ 很高 | XS (2h) | H2 内置 |
| T-DATA-04 | CSV 列映射 UI | ⭐⭐ | ✅ 高 | S (1d) | — |
| T-SEC-01 | Web 访问令牌保护 | ⭐⭐⭐ | ✅ 高 | S (1d) | — |
| T-SEC-02 | API 令牌轮换 | ⭐⭐ | ✅ 高 | XS (2h) | — |
| T-SEC-03 | 速率限制 | ⭐ | ✅ 高 | XS (2h) | — |
| T-PERF-01 | 数据库连接池 | ⭐⭐ | ⚠️ 中 | M (2d) | HikariCP |
| T-PERF-02 | 卡片预加载 | ⭐⭐ | ✅ 高 | S (0.5d) | — |
| T-PERF-03 | 静态资源缓存头 | ⭐ | ✅ 很高 | XS (30min) | — |
| T-CI-01 | GitHub Actions CI | ⭐⭐⭐ | ✅ 很高 | XS (1h) | — |
| T-CI-02 | 测试覆盖率报告 | ⭐⭐ | ✅ 高 | XS (2h) | T-CI-01 |
| T-CI-03 | 自动化发布 | ⭐⭐ | ✅ 高 | XS (2h) | T-CI-01 |
| T-CI-04 | QA 检查清单文档 | ⭐⭐ | ✅ 很高 | XS (2h) | — |

---

### 推荐迭代顺序（Quick Wins 优先）

**Sprint 1（低风险高价值，合计约 2 天）**
1. T-CI-01 — GitHub Actions CI（30 min，零代码改动）
2. T-DATA-03 — 数据库备份恢复（2h，H2 原生）
3. T-API-06 — 统计数据 API（2h，复用现有 Repository）
4. T-WEB-01 — Web 统计仪表板（0.5d，依赖 T-API-06）
5. T-WEB-02 — 笔记全文检索（0.5d）
6. T-PERF-03 — 静态资源缓存头（30 min，单行改动）
7. T-DESK-04 — 字体大小快捷键（1h，基础设施已就绪）

**Sprint 2（功能完善，合计约 3 天）**
1. T-SEC-01 — Web 访问令牌保护
2. T-WEB-04 — Web 端笔记编辑
3. T-WEB-05 — Web 学习模式增强
4. T-SRS-03 — 遮蔽同源卡片
5. T-DESK-01 — Stealth 快速添加笔记
6. T-API-03 — 统一错误响应格式

**Sprint 3（中等复杂度，合计约 5 天）**
1. T-DATA-01 — Anki APKG 导入
2. T-DESK-02 — 标签支持（含 DB 迁移）
3. T-DESK-03 — 历史回顾界面
4. T-PWA-01 — 离线学习队列
5. T-SRS-02 — 可配置学习步进

**Sprint 4（长期演进）**
1. T-SRS-01 — FSRS 算法
2. T-PERF-01 — 数据库连接池
3. T-PWA-02 — Web Push 推送
4. T-API-02 — API 版本前缀

---

*文档版本：1.0 · 生成日期：2026-02-28*
