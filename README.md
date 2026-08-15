# pt_qb

个人自用的 qBittorrent 管理安卓 App（后期加 PT 站聚合搜索 + 一键转存）。

## 功能规划

### v1（当前）：qB 管理

- 多服务器配置保存，随时切换当前操作服务器
- 种子列表：状态过滤（全部/下载中/做种/暂停/错误）、进度/速度/大小/ETA、自动轮询刷新
- 顶部全局上传/下载速度显示
- 种子操作：暂停、恢复、强制恢复、删除（可选删文件）
- 添加种子：磁力链接 / URL / 本地 .torrent 文件
- 种子详情：属性、Tracker 列表、文件列表
- 认证：自动登录，403 自动重登重试

### v2（以后）：PT 站

- NexusPHP 站点聚合搜索
- 搜索结果一键推送下载（带 Cookie 转存种子文件）

## 技术栈

- Kotlin + Jetpack Compose + Material3，单 Activity + Navigation Compose
- Retrofit + OkHttp + kotlinx.serialization（qB Web API v2）
- DataStore 存服务器配置
- 单模块 :app，无 DI、无 Repository 接口层、无 use-case
- ViewModel 直接暴露 `MutableStateFlow`

## qB API 对接要点

- `POST /api/v2/auth/login` → SID cookie，后续请求携带
- `GET /api/v2/torrents/info?filter=...` 列表；`GET /api/v2/transfer/info` 全局速度
- `POST /api/v2/torrents/pause|resume|forceResume|delete`
- `POST /api/v2/torrents/add` multipart（urls + torrents 文件）
- `GET /api/v2/torrents/properties|trackers|files?hash=...`

## 开发环境备忘（本机）

- 项目路径：`E:\work\qb`
- git 身份与 SSH 密钥均为仓库级隔离（公司 GitLab 全局配置不受影响）
- 专用 SSH key：`C:\Users\admin\.ssh\id_ed25519_ptqb`
