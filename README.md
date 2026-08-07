# IdeaTabManager

`IdeaTabManager` 是一个面向 JetBrains IDE 的工作上下文插件：将当前打开的代码文件保存为可命名、可着色、可持久化的 Tab Group，并在之后恢复对应文件、活动文件和光标位置。

当前可构建版本为 **0.2.4**，已提供完整的 Tab Group 日常工作流：

- Kotlin + IntelliJ Platform Gradle Plugin；
- Rider 2026.2（Build 262）编译基线；
- Java 25 工具链与 Kotlin 2.4.0（与 Rider 2026.2 平台基线一致）；
- `Tab Groups` 左侧 Tool Window 组列表与管理操作；
- 项目级 `workspace.xml` 持久化模型、CRUD、文件解析与非破坏性恢复；
- 编辑器正文与 Tab 右键的组管理入口；
- `Save Selected Tabs` 弹窗与 Project View 的多文件批量编组；
- Header 折叠、随机组色、左右布局的标题/注释，以及 F2 行内编辑；
- Group Header 的 `Add Open Tabs…` 与六点 Grip 拖拽排序（Tool Window 内直接鼠标定位，避免嵌入式 DnD 丢失）；
- 安全的 Focus Group：只关闭组外的干净、未固定 Tab；
- 按工作副本自动筛选并唤起 TortoiseSVN / TortoiseGit Commit 对话框；
- Plugin ZIP 打包与结构校验任务；
- GitHub Actions 的普通构建检查。

## 产品边界

插件会通过自己的 `Tab Groups` Tool Window 提供 Edge 风格的彩色工作组体验，并从编辑器 Tab 右键菜单进入后续动作。它不会劫持或重绘 JetBrains 原生 Tab 栏，也不会在默认切换组时关闭无关 Tab 或保存用户未保存的文本。

详细路线图见 [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)，新开发 Agent 应先阅读 [AGENT_HANDOFF.md](AGENT_HANDOFF.md)。

## 本地构建

本机 Rider 2026.2 自带 Java 25。PowerShell 中可临时指定其 JBR：

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Rider 2\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure --console=plain
```

脚本会自动采用默认位置的本机 Rider SDK，避免重复下载数 GB 的 IDE。若 Rider 装在其他位置，请附加 `-PriderSdkPath="C:\path\to\Rider"`。在没有本机 Rider 的 CI 上，构建会下载 Rider 的 archive SDK。安装包会生成到 `build/distributions/`。

## 持续集成与 Marketplace 发布

GitHub Actions 使用三段安全的发布链：

- **Build and Test**：每次推送 `main` 或 Pull Request 时构建、测试、结构校验，并保留 ZIP artifact 14 天；不会上传 Marketplace。
- **GitHub Release**：推送形如 `v0.2.5` 的 tag 时，从 tag 提取版本，使用 Java 25 构建匹配版本的 ZIP，并创建或更新 GitHub Release。
- **Publish to JetBrains Marketplace**：成功的 GitHub Release 会自动触发此工作流；它在 `jetbrains-marketplace` GitHub Environment 等待批准，批准后才读取发布令牌并上传精确的 tag 版本。也可手动 dispatch 已存在的 tag，用于重试或 `eap` channel。

首次接入需要一次性配置：

1. 在 [JetBrains Marketplace](https://plugins.jetbrains.com/) 完成 Vendor/Author 协议，并在 [My Tokens](https://plugins.jetbrains.com/author/me/tokens) 创建永久 token。
2. 在 GitHub 仓库 **Settings → Secrets and variables → Actions** 中创建 repository secret：`PUBLISH_TOKEN`。
3. 在 GitHub 仓库 **Settings → Environments** 中创建 `jetbrains-marketplace`，配置 Required reviewers；这样普通 tag push 不会直接发布到生产 Marketplace。
4. 首次版本仍需在 [Marketplace upload page](https://plugins.jetbrains.com/plugin/add) 手动上传，以补齐插件条目、许可和展示元数据；后续自动发布将通过相同插件 ID `com.whalesea.ideatabmanager` 更新该条目。

发布 `0.2.5` 的标准命令：

```powershell
git tag -a v0.2.5 -m "Tab Groups 0.2.5"
git push origin v0.2.5
```

GitHub Release 成功后，在对应 Action 的 **Review deployments** 中批准 `jetbrains-marketplace`。上传成功并不代表立即公开：需在 Marketplace 侧确认该版本的审核、`listed` 和 `hasUnapprovedUpdate` 状态。

## 开发顺序

1. 完成 Tool Window 的细节交互与 Rider Sandbox 手动验收；
2. 覆盖空组、missing 文件、外部文件、中文路径和主题可读性；
3. 最后在插件 Tool Window 内实现拖拽交互；不要拦截原生 Editor Tab 拖拽。

除非用户明确要求，不要提交或推送本仓库。
