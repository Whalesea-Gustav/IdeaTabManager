# IdeaTabManager：新 Agent 开发上下文交接

## 0. 此文档的用途

本文件让一个没有任何前序聊天记录的新 agent 能独立继续开发 `T:\Projects\IdeaTabManager`。

开始前必须先阅读：

1. `IMPLEMENTATION_PLAN.md`；
2. 本文件；
3. 仓库根目录的 `AGENTS.md`（如果后续出现）；
4. 当前 `git status --short --branch`；
5. 当前 `README.md`、`CHANGELOG.md`、`build.gradle.kts`、`src/main/resources/META-INF/plugin.xml`（这些文件在工程初始化后会出现）。

当前交接时的事实：

```text
Repository root: T:\Projects\IdeaTabManager
Git branch: main
Remote: https://github.com/Whalesea-Gustav/IdeaTabManager
Initial state: empty Git repository; no source files and no commits
Product: JetBrains Tab Groups / context workspace plugin
Primary IDE: Rider 2026.2
Secondary target: generic IntelliJ Platform IDEs
```

## 1. 用户真正要解决的问题

用户同时处理多个功能时，会打开许多代码文件。原生 JetBrains 编辑器支持多个 Tab，但不支持可命名、可着色、可持久化的 Edge 风格 Tab Group。

插件需要让用户：

```text
保存当前打开文件集合为命名组
→ 以后切换组
→ 恢复该组的文件、活动文件和光标位置
→ 用彩色分组 UI 管理多个工作上下文
```

“像 Edge”是交互和视觉参考，不是要求复刻 Edge 源码或修改 JetBrains 原生 Tab 栏。

## 2. 不可违反的架构约束

### 2.1 不劫持原生编辑器 Tab 拖拽

不能把 P0/P1 建立在内部 API 上，例如：

```text
EditorTabs
FileEditorManagerImpl
EditorsSplitters
Rider RD 内部编辑器布局类
```

原生 Tab 拖拽同时负责排序、分屏、Pinned、Preview 等行为。插件要把 Edge 风格 DnD 放在自己的 Tool Window 中。

### 2.2 不破坏用户编辑状态

- 默认切换组只打开和聚焦文件，不关闭任何 Tab；
- 不自动保存未保存 Document；
- 不尝试持久化未保存文件的文本；
- 文件丢失时跳过并通知，不能让一次失效路径阻止其余文件恢复；
- Focus Mode 是后续显式功能，必须保留 Pinned 和修改过的文件。

### 2.3 数据是个人工作区状态

工作组保存于项目级 `workspace.xml`，不应被默认提交到 Git。不要把它放入共享 `.idea` 配置、项目源码或全局 Application 状态。

### 2.4 文件可属于多个工作组

默认数据语义是引用，而不是所有权。将 `CommonTypes.h` 加入“战斗”和“背包”后，不应自动从另一组移除。

## 3. P0 范围

必须完成：

- `Tab Groups` Tool Window；
- 从所有当前打开 Tab 创建组；
- 通过 Tab 右键将当前文件创建/加入/移出组；
- 创建、重命名、改颜色、更新、删除组；
- 双击组或 Activate 后恢复文件、活动文件和 caret；
- 项目级持久化；
- missing 文件提示；
- 单元测试、构建、结构检查、Rider 手动验证。

不要在 P0 实现：

- 原生 Tab 栏改色或分组绘制；
- Tab 栏拖拽拦截；
- 多分屏精确恢复；
- 强制关闭其他 Tab；
- 云同步、团队共享；
- 保存未保存文本；
- VCS、SVN 或 AI 功能。

## 4. 建议文件布局

工程初始化后，应尽量接近以下布局：

```text
IdeaTabManager/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ README.md
├─ CHANGELOG.md
├─ IMPLEMENTATION_PLAN.md
├─ AGENT_HANDOFF.md
├─ src/main/kotlin/com/whalesea/ideatabmanager/
│  ├─ actions/
│  │  ├─ CreateGroupFromOpenTabsAction.kt
│  │  ├─ AddCurrentTabToGroupAction.kt
│  │  ├─ RemoveCurrentTabFromGroupAction.kt
│  │  └─ ShowTabGroupsAction.kt
│  ├─ model/
│  │  ├─ TabGroupRecord.kt
│  │  └─ TabReference.kt
│  ├─ service/
│  │  ├─ TabGroupProjectService.kt
│  │  ├─ TabGroupPersistentState.kt
│  │  ├─ TabGroupFileResolver.kt
│  │  ├─ TabGroupRestorer.kt
│  │  └─ TabGroupSessionTracker.kt
│  ├─ toolwindow/
│  │  ├─ TabGroupsToolWindowFactory.kt
│  │  ├─ TabGroupsPanel.kt
│  │  ├─ TabGroupRenderer.kt
│  │  └─ TabGroupDndController.kt
│  └─ IdeaTabManagerBundle.kt
├─ src/main/resources/
│  ├─ META-INF/plugin.xml
│  ├─ icons/
│  └─ messages/
└─ src/test/kotlin/com/whalesea/ideatabmanager/
```

允许根据实际复杂度调整目录，但必须保持：模型、持久化、文件恢复、UI 和 Action 之间的职责分离。

## 5. 核心实现指导

### 5.1 创建组

使用 `FileEditorManager.getInstance(project)` 获取已打开文件，并从当前 selected editor 对应文件记录活动项。对文本文件可记录 caret offset；非文本编辑器只保存文件引用。

创建前执行去重，使用稳定的 `VirtualFile.url` 作为唯一键。不要只使用文件名。

### 5.2 文件恢复

恢复必须分两段：

1. 后台准备：解析 `TabReference`，筛出有效文件和 missing 文件；
2. EDT 执行：通过公开 `FileEditorManager` / `OpenFileDescriptor` 打开并选择文件。

建议先非焦点方式打开全部有效文件，最后才打开活动文件并恢复 offset。不要假设文件打开顺序一定等于原生 Tab 显示顺序。

### 5.3 caret 恢复

仅对可获得 `Document` 的文本文件记录 offset。恢复前必须把 offset clamp 到：

```text
0 .. document.textLength
```

打开失败、文件变成二进制、offset 无效时安全忽略 caret 恢复，不应让激活组失败。

### 5.4 Tool Window UI

使用 JetBrains Swing UI：`JBUI`、`JBColor`、`SimpleColoredComponent`、标准 ActionToolbar 和文件图标。

不要复制 Web React/CSS 的卡片式 UI。目标是“Edge 式彩色组层级”，但视觉上仍像 JetBrains 原生工具窗口。

文件行要同时显示：

```text
较醒目的文件名
较小的灰色项目相对路径
```

### 5.5 拖拽

P0 可以没有 DnD；先保证右键与 Tool Window 按钮可用。

P1 的拖拽仅在插件 Panel 内实现。载荷应是纯数据：

```kotlin
data class TabDragPayload(
    val fileUrl: String,
    val sourceGroupId: String?
)
```

Drop 行为必须在 service 层执行，而不是直接修改 Swing model。修改后由 service 发出状态更新，UI 重新渲染。

### 5.6 监听器

`FileEditorManagerListener` 在 P0 只追踪：

- 用户最后选择的文件；
- 可选的当前 caret 更新。

不要在 `fileClosed` 时自动把文件从所有组删除。工作组是可恢复上下文，不是当前 Tab 列表的被动镜像。

## 6. 每次开发前后的操作

### 开始前

```powershell
git status --short --branch
rg --files
```

若工作区不干净，先区分用户改动和当前任务改动；不能擅自 reset、checkout 或删除。

### 修改规则

- 所有源码与文档编辑使用 `apply_patch`；
- 不使用 `git reset --hard`、`git checkout --`；
- 不能为方便实现而引入 JetBrains 内部 API；
- 每一个新行为都在 CHANGELOG 或实现计划中有对应说明；
- 只在用户明确要求时 commit 或 push；
- 发现产品语义冲突时，先报告并提出默认方案，不要静默改变数据模型。

### 最低验证命令

```powershell
.\gradlew.bat test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure --console=plain
```

发布前额外运行一次本机 Rider Plugin Verifier。先探测实际 Rider 安装目录；不要假设固定路径。

推荐的 PowerShell 探测方式：

```powershell
Get-ChildItem "$env:LOCALAPPDATA\Programs" -Directory |
  Where-Object { $_.Name -like 'Rider*' }
```

然后将真实路径传给 Gradle：

```powershell
.\gradlew.bat verifyPlugin "-PriderPath=<实际 Rider 路径>" --console=plain
```

## 7. 完成 P0 的验收清单

- [ ] 新建空组、从当前打开 Tab 创建组均可用；
- [ ] 组可命名、改色、重命名、更新、删除；
- [ ] 当前 Tab 可以加入和移出组；
- [ ] 同一文件可加入多个组；
- [ ] 激活组会打开缺失 Tab 并聚焦活动文件；
- [ ] 激活不会自动关闭无关 Tab；
- [ ] 文本 caret 在可用情况下被恢复；
- [ ] missing 文件不会阻断其他文件恢复；
- [ ] 重启 IDE 后数据保留；
- [ ] 中文、空格、项目外文件不崩溃；
- [ ] 浅色与 Darcula 主题可读；
- [ ] Rider 中 Tool Window、Tab 右键和编辑器右键均出现；
- [ ] 测试、构建、项目配置检查、结构检查通过；
- [ ] 最终 Plugin Verifier 对目标 Rider 通过。

## 8. 后续 Agent 的首个建议任务

从阶段 0 开始，而不是直接写 Tool Window。

第一提交应只包含：

1. Gradle Kotlin IntelliJ Platform 插件骨架；
2. 合法的 `plugin.xml`；
3. 一个可显示的空 `Tab Groups` Tool Window；
4. 一个项目级 `PersistentStateComponent` 空壳；
5. 构建、测试和结构验证通过；
6. 简短 README，明确 P0 非破坏性切换语义。

骨架验证成功后，再按“模型/恢复 → Action → UI → DnD”的顺序推进。不要把持久化、复杂 UI、原生 Tab 拦截和 Marketplace 发布堆到同一个首次提交中。
