<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Gradle Dependency Updater Changelog

## [Unreleased]


## 0.0.1

Initial release of the plugin

### Added
- Main feature: outdated dependency detection and underlining from a build.gradle.
- Side panel to manage build.gradle dependencies.
- Settings panel to configure the plugin.

## 0.0.2

### Added
- Added translations for French
- Added force refresh
- Added app-level caching

## 0.0.3

### Added
- Added filtering for Nexus repository

### Fix
- Settings values not properly showing after saving
- Support for ${variable} format in gstring
- Removed deprecated dependencies

## 0.0.4

### Added
- Added search bar in extension panel to filter dependencies
- Added right click action on dependency item in extension panel

### Fix
- Optimised refresh and parsing

## 0.0.5

### Added
- Not found dependencies are now highlighted in red instead of being silently ignored

### Fix
- Version ordering: plain releases (e.g. `1.0.3`) now correctly rank above `-feat`/`-pr` and other unrecognized branch/build suffixes of the same version, and `-pr` suffixes always rank above `-feat` suffixes.