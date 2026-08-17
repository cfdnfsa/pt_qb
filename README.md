# pt_qb

个人自用的安卓 App：Transmission 管理 + PT 站内嵌浏览器（Firefox 内核）一键转存。

## 开发原则

- 个人项目，唯一用户是自己，样式最不重要
- 一切以**高性能 + 代码简洁**为先：能删的层都删，能不写的样板代码不写
- 利用现成轮子优先于自己造
- ViewModel 直接暴露 `val state = MutableStateFlow(State())`，**不用** `_xxx` 私有变量 + `asStateFlow()` 转换那套
- 无 DI 框架、无 Repository 接口层、无 use-case 层

## 功能

### 下载管理（Transmission RPC）

- 多服务器配置切换（每次操作单服务器）；顶栏速度 / 磁盘剩余 / 累计上传
- 种子列表：状态（功能色）/大小/进度/速度/种|活/分享率/做种时长；10s 轮询（可调/可关）+ 手动刷新 + 下拉刷新
- 筛选：状态 / 数据目录 / 标签 / 名称搜索；列表左右滑切目录
- 排序：8 维度（含上传量）正反序
- 多选批量：暂停/恢复/校验/汇报/改 Tracker/换目录/打标签/删除，全部带确认
- 详情：常规/快/用户/Tracker/文件（折叠）五分区 + 单种子限速
- 添加：磁力 / URL / 本地 .torrent，目录选择器（收藏 + 现有目录）
- 目录收藏管理（新建=记路径，Transmission 添加种子时自动建目录）

### PT 浏览（v2，GeckoView = 真正的 Firefox 内核）

- 站点管理/切换/入口路径；登录态持久化（Cookie 由引擎保管）
- TLS 指纹 = Firefox，站点 WAF 无法拦截（WebView 会被 net::ERR_CONNECTION_CLOSED）
- 站内搜索直达种子列表；每站点会话缓存（切站不丢页面）；网页返回键后退
- **下载链接一键转存**：拦截 magnet/​.torrent/download.php（含 target=_blank），用引擎网络栈（Cookie/UA/指纹一致）下载种子 → torrent-add 到当前服务器 + 选目录
- 设置里可清缓存（保留登录）

## 技术栈

- Kotlin 2.3 + Jetpack Compose + Material3（黑白描边 monochrome 主题）
- GeckoView 153（maven.mozilla.org，**不在 central**）
- 纯 OkHttp + kotlinx.serialization（Transmission 单端点 RPC）
- DataStore（配置）+ 协程 Flow；单模块 :app，minSdk 26 / compileSdk 36
- AGP 8.7.3（受本机 AS 版本限制，force androidx.core 1.15 绕开 1.18 的 AGP 8.9 要求）

## 开发环境备忘（本机）

- 项目：`E:\work\qb`；Android SDK：`E:\kaifa\huanjing\android\sdk`；JDK：`E:\kaifa\huanjing\jdk\jdk\21`
- Gradle 依赖走腾讯镜像 + maven.mozilla.org（GeckoView 专用）
- git 身份与 SSH 密钥均为仓库级隔离（公司 GitLab 全局配置不受影响）

## 已知未做

- 网页长按链接菜单（GeckoView 153 长按 API 待查证）
- Gecko 深色模式跟随
- AGP/AS 升级（升 AS 后可摘掉 force，全家桶对齐最新）
