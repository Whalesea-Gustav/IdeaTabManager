# IdeaTabManager

`IdeaTabManager` 是一个面向 JetBrains IDE 的工作上下文插件：将当前打开的代码文件保存为可命名、可着色、可持久化的 Tab Group，并在之后恢复对应文件、活动文件和光标位置。

当前可构建版本为 **0.2.0**，已提供完整的 Tab Group 日常工作流：

- Kotlin + IntelliJ Platform Gradle Plugin；
- Rider 2026.2（Build 262）编译基线；
- Java 25 工具链与 Kotlin 2.4.0（与 Rider 2026.2 平台基线一致）；
- `Tab Groups` 左侧 Tool Window 组列表与管理操作；
- 项目级 `workspace.xml` 持久化模型、CRUD、文件解析与非破坏性恢复；
- 编辑器正文与 Tab 右键的组管理入口；
- Open Tabs 与 Project View 的多文件批量编组；
- Header 折叠、随机组色、可选注释，以及 F2 行内编辑标题/注释；
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

## 开发顺序

1. 完成 Tool Window 的细节交互与 Rider Sandbox 手动验收；
2. 覆盖空组、missing 文件、外部文件、中文路径和主题可读性；
3. 最后在插件 Tool Window 内实现拖拽交互；不要拦截原生 Editor Tab 拖拽。

除非用户明确要求，不要提交或推送本仓库。
