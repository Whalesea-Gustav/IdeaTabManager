# IdeaTabManager 实现计划

## 1. 项目目标

构建一个独立的 JetBrains IDE 插件，用可命名、可着色、可持久化的 **Tab Group（标签页工作组）** 管理代码上下文。

目标用户场景：开发者同时处理“战斗系统”“背包系统”“SVN 修复”等多个功能时，可以把一组已经打开的编辑器文件保存为工作组，并在之后一键恢复这组文件、上次活动文件和光标位置。

插件展示名称暂定为 **Tab Groups**，仓库名称为 `IdeaTabManager`，插件 ID 建议为：

```text
com.whalesea.ideatabmanager
```

## 2. 产品边界和核心决策

### 2.1 支持的内容

- 同时记录多个已打开的编辑器文件；
- 创建、命名、改色、删除、更新工作组；
- 激活工作组时打开缺失文件、聚焦上次活动文件、恢复文本光标；
- 左侧 `Tab Groups` Tool Window；
- 编辑器 Tab 右键菜单和编辑器正文右键菜单；
- 项目私有持久化，IDE 重启后自动恢复；
- 在插件自己的 Tool Window 中实现 Edge 风格的彩色分组和拖拽交互；
- 支持 Rider、IntelliJ IDEA、CLion、PyCharm 等使用通用 File Editor API 的 IDE。

### 2.2 明确不做的内容

P0 和 P1 都不修改 JetBrains 原生编辑器 Tab 栏的绘制、颜色、拖拽或分组结构。

理由：原生 Tab 栏的拖拽已经处理排序、分屏、Preview Tab、Pinned Tab 和各产品内部状态，且没有稳定的公开“Tab Group”扩展 API。依赖 `EditorTabs`、`FileEditorManagerImpl` 等内部类会降低 Rider 与后续 IDE 版本兼容性。

插件的 Edge 风格体验应放在独立的 `Tab Groups` Tool Window 中；原生 Tab 栏只作为右键操作入口和文件打开结果。

P0 不保存未保存文档的文本内容，不自动关闭其他已打开文件，不精确恢复多分屏布局，也不修改 VCS 或项目模型。

### 2.3 工作组语义

默认采用 **工作上下文引用** 模式：一个文件可以属于多个组。

```text
CommonTypes.h
  ├─ 战斗系统
  ├─ 背包系统
  └─ 任务系统
```

这是代码开发中比浏览器“一个 Tab 仅属于一个组”更实用的默认行为。P1 可以增加可选的 Edge exclusive 模式，但不能改变已有用户数据的默认含义。

工作组激活默认为非破坏性：

```text
打开本组尚未打开的文件
→ 选中本组上次活动文件
→ 恢复记录的光标位置
→ 不关闭其他编辑器 Tab
```

后续的 `Focus Group` 模式必须是显式命令，并且默认保留 Pinned Tab 和未保存文档。

## 3. P0 用户故事与验收标准

### 3.1 创建组

用户点击 `Save Current Tabs as Group`，插件读取当前项目的已打开文件，弹出轻量对话框让用户输入名称并选择颜色。保存时记录所有文件、当前活动文件和可恢复的 caret offset。

验收：打开 `A.java`、`B.kt`、`C.cpp` 并聚焦 `B.kt` 后创建“功能 A”，组内有 3 个条目，活动文件为 `B.kt`。

### 3.2 激活组

用户双击组标题或点击 `Activate`。插件解析每个文件引用，打开仍存在但未打开的文件，随后选择活动文件并恢复 caret。

验收：关闭 A/B/C 后激活“功能 A”，A/B/C 均重新打开，B 获得焦点；不存在的文件被跳过并以通知列出，不阻断其余文件恢复。

### 3.3 右键菜单

编辑器 Tab 与编辑器正文右键菜单包含：

```text
Tab Groups
  New Group with Current Tab
  Add Current Tab to Group…
  Remove Current Tab from Group…
  Show in Tab Groups
```

验收：用户可将当前文件加入一个已有组，且同一文件加入另一组后不会从第一个组消失。

### 3.4 持久化

工作组应保存在项目个人工作区状态中，不应进入版本控制。

验收：创建组、关闭 IDE、重新打开相同项目后，组名、颜色、文件列表、活动文件和可用 caret 位置仍存在。

### 3.5 安全性

- 不自动保存 Document；
- 不自动关闭编辑器；
- 未保存文件只保存其文件引用及当前 caret，不尝试持久化内存中的文本；
- 对无效路径、目录、非文本编辑器、重复条目、中文路径和空格路径有明确行为；
- 所有涉及 Swing 和 File Editor UI 的操作必须在 EDT；文件解析和批量状态准备在后台进行。

## 4. 技术架构

### 4.1 推荐技术栈

- Kotlin；
- Gradle + IntelliJ Platform Gradle Plugin；
- Java 21；
- 通用 IntelliJ Platform File Editor API；
- 在搭建时以用户当前安装的 Rider 2026.2 为首要验证目标，并额外验证同一平台基线的 IntelliJ IDEA。

不要在开始时硬编码 `sinceBuild`。先读取目标 Rider 的 `product-info.json` 或 Gradle Platform SDK 的实际 build number，再把 `sinceBuild` 固化到构建配置和 README。

### 4.2 组件关系

```text
EditorTab / EditorPopup Actions
             │
             ▼
TabGroupProjectService ─── TabGroupPersistentState
       │                          │
       │                          └─ .idea/workspace.xml
       │
       ├─ TabGroupRestorer ───── FileEditorManager / OpenFileDescriptor
       ├─ TabGroupFileResolver ─ VirtualFileManager / project base path
       ├─ TabGroupSessionTracker ─ FileEditorManagerListener
       └─ TabGroupDndController ─ Tool Window custom DnD only
             ▲
             │
      TabGroupsToolWindowPanel
```

### 4.3 数据模型

建议把持久化 DTO 与运行时 UI 状态分开。

```kotlin
data class TabGroupState(
    var schemaVersion: Int = 1,
    var groups: MutableList<TabGroupRecord> = mutableListOf()
)

data class TabGroupRecord(
    var id: String,
    var name: String,
    var colorId: String,
    var tabs: MutableList<TabReference>,
    var activeFileKey: String?,
    var createdAtEpochMs: Long,
    var updatedAtEpochMs: Long
)

data class TabReference(
    var fileUrl: String,
    var projectRelativePath: String?,
    var lastKnownName: String,
    var caretOffset: Int?
)
```

文件恢复优先级：

1. 当 `projectRelativePath` 可用时，从 `project.basePath` 拼出并验证；
2. 回退 `VirtualFileManager.findFileByUrl(fileUrl)`；
3. 两者均失败时标记为 missing，不删除持久化记录；
4. 用户可在 UI 中移除 missing 条目，或在以后恢复文件后再次激活。

持久化服务使用项目级 `PersistentStateComponent`，存储到 `StoragePathMacros.WORKSPACE_FILE`。不要写入 `.idea/misc.xml` 或提交到 Git 的共享配置。

### 4.4 关键 API 约束

应优先使用公开 API：

- `FileEditorManager`：读取、打开、选择文件；
- `FileEditorManagerListener`：跟踪选中编辑器变化；
- `FileDocumentManager` 与 `Document`：读取当前 caret 关联文本文件；
- `OpenFileDescriptor`：按 offset 打开文本文件；
- `VirtualFileManager`：根据 URL 解析文件；
- `PersistentStateComponent`：保存项目状态；
- `ToolWindowFactory`：创建 Tool Window；
- `AnAction`、`ActionUpdateThread.BGT`：注册动作；
- `DnDSupport`：只用于插件自身 Swing 组件的拖拽。

禁止把内部 API 当作 P0 依赖，包括但不限于：

```text
EditorTabs
FileEditorManagerImpl
EditorsSplitters
FileEditorManagerEx 的内部实现细节
Rider RD 内部 Tab 布局类
```

如果某个功能只有内部 API 可实现，应记录为 P2 experimental，而不是让 P0 引入兼容性风险。

## 5. UI 与交互设计

### 5.1 Tool Window

Tool Window ID：`Tab Groups`，建议锚定左侧。

布局：

```text
┌─ Tab Groups ──────────────────────────────┐
│ + New Group     Save Current Tabs     ⌕    │
│                                            │
│ ● 战斗系统                            3   │
│   PlayerController.cpp                     │
│   CombatComponent.cpp                      │
│   CommonTypes.h                            │
│                                            │
│ ● 背包系统                            2   │
│   InventoryComponent.cpp                   │
│   CommonTypes.h                            │
└────────────────────────────────────────────┘
```

视觉规则：

- 使用 `JBUI`、`JBColor`、平台文件图标和标准 ActionToolbar；
- 每个组有彩色圆点或左侧色条，而不是网页风格的大卡片；
- 当前激活组有轻量高亮；
- 文件名称为主文字，项目相对父路径为较小的灰色次文字；
- 所有颜色必须同时定义浅色/深色主题值；
- 工具窗口文件双击时使用 IDE 原生编辑器打开并聚焦。

### 5.2 P0 创建路径

- 顶部 `Save Current Tabs as Group`；
- 顶部 `New Empty Group`；
- 当前 Tab 右键 `New Group with Current Tab`；
- 当前 Tab 右键 `Add Current Tab to Group…`；
- 组右键 `Update from Current Open Tabs`。

### 5.3 P1 Edge 风格拖拽

拖拽仅发生在插件 Tool Window 的文件 Chip/行之间：

| 来源 | 目标 | 结果 |
|---|---|---|
| Open Tabs 中的文件 | 组标题或组内容区 | 加入目标组 |
| 未分组文件 A | 未分组文件 B | 创建包含 A/B 的新组 |
| 组内文件 | 另一个组 | 默认加入；可选 modifier 实现移动 |
| 组标题 | 另一个组标题 | 调整组顺序 |
| 组内文件 | 空白区 | 显式移出或新建组，不能静默丢失 |

拖拽载荷不直接传递 UI 对象，只传递稳定的数据：

```kotlin
data class TabDragPayload(
    val fileUrl: String,
    val sourceGroupId: String?
)
```

当文件悬停在另一个未分组文件上时，显示“创建新组”的覆盖高亮和预览；松开后弹出组名/颜色对话框。

## 6. 分阶段实现顺序

### 阶段 0：工程基线

1. 初始化 Kotlin IntelliJ Platform Gradle 工程；
2. 配置插件 ID、名称、Vendor、`plugin.xml`、图标占位；
3. 配置 Java 21、测试、`buildPlugin`、结构校验；
4. 读取本机 Rider build number 并设定兼容范围；
5. 建立 `.gitignore`、README、CHANGELOG、GitHub Actions 基础构建。

完成条件：空插件可在 Rider Sandbox 启动，`test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure` 通过。

### 阶段 1：模型、持久化、文件恢复

1. 实现 DTO、schema version 和项目级持久化服务；
2. 实现创建、更新、重命名、删除、改色、加入、移除；
3. 实现文件解析与 missing 状态；
4. 实现非破坏性工作组激活；
5. 实现活动文件和 caret tracking。

完成条件：不用 Tool Window 也可从测试/动作创建组并在重启后恢复。

### 阶段 2：动作与 P0 Tool Window

1. 注册 Tool Window；
2. 实现 Edge 风格组树/列表；
3. 实现创建组、编辑名称与颜色的对话框；
4. 注册 Editor Tab / Editor Popup 动作；
5. 文件双击定位和上下文菜单；
6. 处理空组、missing 文件、重复文件和外部文件。

完成条件：完整 P0 用户故事可以通过鼠标操作完成。

### 阶段 3：拖拽与工作流体验

1. 增加 Open Tabs 镜像区；
2. 实现 DnD payload、drop target、视觉反馈与撤销友好操作；
3. 实现“拖到文件上创建组”；
4. 实现组排序和文件排序；
5. 增加搜索、折叠状态和最近激活组。

完成条件：Tool Window 内可实现接近 Edge 的核心拖拽创建体验。

### 阶段 4：安全 Focus Mode 与高级恢复

1. 可选 Focus Group 命令；
2. 永远保留 pinned 和 unsaved 编辑器；
3. 关闭前提供确认和可恢复通知；
4. 评估是否需要保存分屏位置；若仅能通过内部 API 实现，则保持为不支持。

### 阶段 5：跨 IDE 验证与发布

1. Rider 验证；
2. IntelliJ IDEA 验证；
3. 运行 Plugin Verifier；
4. 完善 Marketplace 描述、隐私说明、版本说明和图标；
5. 创建 tag、GitHub Release、Marketplace 受保护发布。

## 7. 测试矩阵

### 单元测试

- DTO 序列化、反序列化、schema migration；
- 创建、重命名、改色、删除、排序；
- 同一文件加入多个组；
- project-relative 路径优先、URL fallback；
- 中文、空格、Unicode 路径；
- missing 文件不破坏其他条目；
- caret offset 越界时安全截断或忽略；
- P0 激活不关闭其他 Tab；
- DnD 目标分类和非法 drop 拒绝。

### 手动 Sandbox 验收

1. 打开至少 5 个文件并建立两个工作组；
2. 重启 IDE 后恢复；
3. 将同一个公共文件加入多个组；
4. 删除磁盘文件后激活组；
5. 保持一个未保存的编辑器修改并激活另一组；
6. 在 Rider 的 Unreal 项目和普通 IntelliJ 项目各验证一次；
7. 在浅色、Darcula、高 DPI 下检查颜色和文件行；
8. 验证 Preview Tab、Pinned Tab 和编辑器 Split 没有被破坏。

## 8. 发布与版本策略

开发期每次变更至少运行：

```powershell
.\gradlew.bat test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure --console=plain
```

发布前再对本机 Rider 运行一次 Plugin Verifier。Rider 路径必须在本机探测后传入，不要在文档中假设固定安装路径。

版本规则：

- `0.1.0`：P0 可用；
- `0.2.0`：拖拽与 Edge 风格组管理；
- `0.3.0`：Focus Mode、导入导出等高级能力；
- 修复版本使用 patch，例如 `0.1.1`。

首次 Marketplace 发布需要人工确认条目元数据；后续发布应使用受保护 GitHub Environment 的批准流程。
