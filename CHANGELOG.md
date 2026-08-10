# Changelog

All notable changes to this project are documented in this file.

## [0.2.8] - 2026-08-10

### Fixed

- Fixed the Group Header color marker expanding across the row and pushing the title, file count, and note out of place.

## [0.2.7] - 2026-08-10

### Changed

- Made the Group Header file count a compact, muted `num={count}` label so the editable group title remains the primary visual element.

## [0.2.6] - 2026-08-07

### Fixed

- Aligned the test runtime with JUnit 5.13.4 so Rider 2026.2's JUnit session listener can start on GitHub Actions.

## [0.2.5] - 2026-08-07

### Fixed

- Kept Group Header titles and notes adjacent with a compact 4dp gap instead of allowing the title's layout slot to push the note to the far edge of the Tool Window.

## [0.2.4] - 2026-08-07

### Changed

- Redesigned the Tool Window and plugin icons around a three-tab editor surface with a shared group rail, making the multi-tab management purpose legible at compact sizes.

### Added

- Approval-gated GitHub Actions workflows for tag-based GitHub Releases and JetBrains Marketplace publishing, with Java 25 release builds and `default` / `eap` channel support.

### Fixed

- GitHub Actions now marks the Gradle Wrapper executable after checkout, preventing Linux runners from failing with `./gradlew: Permission denied` when the repository is edited on Windows.

## [0.2.3] - 2026-08-07

### Fixed

- Reworked Group Header reordering to use direct pointer tracking inside the plugin-owned Tool Window instead of Swing cross-component drop routing, which could fail in embedded IDE Tool Windows.
- The blue before/after insertion indicator and the persisted drop position now use the same Group-panel coordinate system; reordering to the end of the list is covered by a unit test.

## [0.2.2] - 2026-08-07

### Added

- `Add Open Tabs…` in the group Header menu for directly multi-selecting current editor tabs into that group.
- A six-dot Header grip for persistent Tab Group drag-reordering, including a visible before/after insertion line.
- Cached Tortoise client discovery, Group commit classification, and working-copy roots to keep Group context menus responsive.
- Replaced the oversized system move pointer over the Group reorder grip with a compact custom four-way drag cursor.

### Changed

- Reduced the Header note's minimum left inset to tighten title/note spacing.

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
