# Changelog

All notable changes to this project are documented in this file.

## [0.2.1] - 2026-08-07

### Changed

- Replaced the always-visible Open Tabs checkbox list with a compact `Save Selected Tabs` toolbar workflow.
- `Save Selected Tabs` now opens a modal multi-select list of currently open files, defaults to the active file, and provides `Select All` and `Clear` controls.
- The selection dialog keeps both subset workflows: create a new group or add the selected files to an existing group.
- Changed group headers to show title and note side by side, keeping the header more compact for scanning.

## [0.2.0] - 2026-08-07

### Added

- Persistent, named, randomly colored Tab Groups stored in project-local `workspace.xml`.
- Non-destructive group restore with active-file and caret recovery, plus missing-file notifications.
- `Tab Groups` Tool Window controls, editor-context actions, collapsible headers, color editing, notes, and F2 inline title/note editing.
- Open Tabs multi-select workflows, Project View multi-file add actions, recent-group shortcuts, and group-level multi-file chooser.
- `Focus Group`, which closes only clean, non-pinned tabs outside the target group and preserves modified or pinned files.
- Group-header commands to open TortoiseSVN or TortoiseGit Commit for detected local working-copy files.
- Safe mixed-repository behavior: commit choices are separated by VCS type and working-copy root.
- UTF-16LE, no-BOM, LF-only validation for TortoiseSVN multi-file path files; TortoiseGit's documented `*`-delimited `/path:` invocation.

### Changed

- Updated the plugin description and Marketplace What’s New content to reflect the usable Tab Group workflow.

## [0.1.0]

### Added

- Initial IntelliJ Platform Kotlin plugin scaffold targeting Rider 2026.2 / build 262.
