# pt_qb

个人自用的 Transmission 管理安卓 App（后期加 PT 站聚合搜索 + 一键转存）。

## 开发原则

- 个人项目，唯一用户是自己，样式最不重要
- 一切以**高性能 + 代码简洁**为先：能删的层都删，能不写的样板代码不写
- 利用现成轮子优先于自己造
- ViewModel 直接暴露 `val state = MutableStateFlow(State())`，**不用** `_xxx` 私有变量 + `asStateFlow()` 转换那套
- 无 DI 框架、无 Repository 接口层、无 use-case 层

## 功能规划

### v1（当前）：Transmission 管理

- 服务器列表（名称 / ip:端口），点按钮切换当前服务器，每次只操作一个
- 种子列表：**状态筛选**（全部/下载中/做种中/已停止/校验中/错误）+ **数据目录筛选**（动态生成）
- 10 秒轮询刷新，顶栏全局上传/下载速度
- 种子操作：暂停、恢复、强制开始、删除（可选删数据）
- 添加种子：磁力 / URL / 本地 .torrent 文件
- 详情：属性、Tracker、文件列表、复制磁力链

### v2（以后）：PT 站

- NexusPHP 站点聚合搜索
- 搜索结果一键推送下载（带 Cookie 转存种子文件）

## 技术栈

- Kotlin 2.1 + Jetpack Compose + Material3（黑白描边 monochrome 主题）
- 单 Activity + Navigation Compose 类型安全路由
- 纯 OkHttp + kotlinx.serialization（单端点 RPC，一个 `call(method, args)` 泛型函数）
- DataStore 存服务器配置（JSON）
- 单模块 :app，minSdk 26 / targetSdk 35

## Transmission RPC 对接要点

- 端点：`POST http://host:port/transmission/rpc`
- 认证：HTTP Basic Auth
- 会话：首次请求返回 409，读响应头 `X-Transmission-Session-Id` 后续携带（Interceptor 自动处理）
- 列表：`torrent-get`（fields 一次拿全：属性/Tracker/文件都在同对象）
- 全局速度：`session-stats`
- 操作：`torrent-start` / `torrent-stop` / `torrent-start-now` / `torrent-remove`（delete-local-data）
- 添加：`torrent-add`（filename=磁力/URL，metainfo=base64 种子文件）
- 目录筛选 RPC 不支持服务端过滤，客户端内存过滤

## 开发环境备忘（本机）

- 项目路径：`E:\work\qb`
- Android SDK：`E:\kaifa\huanjing\android\sdk`（compileSdk 35）
- JDK：`E:\kaifa\huanjing\jdk\jdk\21`（JBR 21）
- Gradle 依赖与发行版均走腾讯云镜像
- git 身份与 SSH 密钥均为仓库级隔离（公司 GitLab 全局配置不受影响）
- 专用 SSH key：`C:\Users\admin\.ssh\id_ed25519_ptqb`
