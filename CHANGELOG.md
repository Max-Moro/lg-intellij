<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Listing Generator Changelog

## [1.0.0] - 2025-01-XX

### Added
- Initial plugin structure based on [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
- Plugin personalization: renamed all classes from `My*` to `Lg*`
- Custom plugin icon (40×40 SVG) and Tool Window icon (13×13 SVG)
- Basic Tool Window with placeholder content
- Resource bundle for localization (`LgBundle`)
- Compatibility with IntelliJ Platform 2024.1-2024.3 (builds 241-243.*)
- DumbAware support for Tool Window Factory

### Changed
- Updated plugin metadata (ID, name, vendor information)
- Configured plugin description with HTML formatting
- Removed sample components and activities

### Technical
- Gradle 9.0.0 support
- Kotlin 1.9.22
- IntelliJ Platform Gradle Plugin 2.9.0
- Target JVM: 21
