# Changelog

This changelog follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [0.1.0] - 2026-11-10

### Added

- Added a **Config** button to the Forge 1.6.4 Mods menu. Ordinatio discovers local configuration files opened through Forge's configuration API and lists multiple files by relative path and mod ID.
- Added search by category, setting name, or comment, with a draggable scrollbar for long lists.
- Added editors for booleans, numbers, strings, and lists, with options to restore defaults, save, or discard changes.
- Added yellow tooltip text explaining detected or inferred registry IDs, with confirmation before saving changes that may damage existing worlds.
- Added the `ConfigOptions` API for mod authors to supply choices for settings and list items, including Java enum constants. Players can apply values outside those choices after confirmation.
- Added English and Simplified Chinese translations.

[Unreleased]: https://github.com/DesideriumMC/Ordinatio/compare/v0.1.0...main
[0.1.0]: https://github.com/DesideriumMC/Ordinatio/releases/tag/v0.1.0
