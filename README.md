> [!NOTE]  
> **This repository is a feature-rich fork of the original [Prism File Explorer](https://github.com/Raival-e/Prism-File-Explorer) project.** 
> It integrates premium companion features from [`NFile`](https://github.com/Senzme/NFile) (including a Private Wallet, Local FTP Server, and Web Sharing with port-forwarding), replaces the PDF viewer with Jetpack's native `androidx.pdf:pdf-viewer-fragment`, introduces a custom Compose Excel spreadsheet viewer, integrates the office document layout engine from [`all-documents-reader`](https://github.com/ahmadullahpk/all-documents-reader), implements split APK bundle installation, and adds support for Shizuku/root privileged file access.


<div align="center">

<img src="assets/app_icon.png" width="120" alt="Prism File Explorer Logo"/>

# Prism File Explorer

[![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg?logo=android)](https://www.android.com/)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Release](https://img.shields.io/github/v/release/techflyers/Prism-File-Explorer?label=Release)](https://github.com/techflyers/Prism-File-Explorer/releases)
[![APK Downloads](https://img.shields.io/github/downloads/techflyers/Prism-File-Explorer/total.svg?label=APK%20Downloads)](https://github.com/techflyers/Prism-File-Explorer/releases)
[![Stars](https://img.shields.io/github/stars/techflyers/Prism-File-Explorer?style=flat&logo=github)](https://github.com/techflyers/Prism-File-Explorer/stargazers)
[![Forks](https://img.shields.io/github/forks/techflyers/Prism-File-Explorer?style=flat&logo=github)](https://github.com/techflyers/Prism-File-Explorer/network/members)
[<img src="https://shields.rbtlog.dev/simple/com.raival.compose.file.explorer" alt="RB shield">](https://shields.rbtlog.dev/com.raival.compose.file.explorer)

**A modern, feature-rich, and lightweight file manager for Android, built entirely with Kotlin and Jetpack Compose.**

*Delivering a seamless file management experience with a beautiful Material Design interface*

</div>

---

## 📱 Screenshots

<div align="center">
  <img src="assets/image1.png" width="22%" alt="Main Interface"/>
  <img src="assets/image2.png" width="22%" alt="File Operations"/>
  <img src="assets/image3.png" width="22%" alt="Apps Extractor"/>
  <img src="assets/image4.png" width="22%" alt="Text Editor"/>
</div>

<div align="center">
  <img src="assets/image6.png" width="21%" alt="Audio Player"/>
  <img src="assets/image7.png" width="21%" alt="Image Viewer"/>
  <img src="assets/image5.png" width="21%" alt="PDF Viewer"/>
</div>

---

## ✨ Key Features

### 🎨 **Modern User Interface & Navigation**
*   **Jetpack Compose & Material 3**: Fully declarative, highly responsive, and beautiful layouts with automatic light/dark theme support.
*   **NFile Navigation Drawer**: Quick-navigation sidebar for accessing the **Dashboard**, **System Root**, **Recycle Bin**, and server/network configuration wizard.
*   **Multi-Tab Layout**: Seamlessly open, navigate, and manage multiple folders at once.
*   **Intuitive Breadcrumbs**: Quick hierarchical folder traversal.

### 📁 **Advanced File Management**
*   **Comprehensive Operations**: Copy, cut, paste, rename, delete, batch select, and inspect detailed properties of files and folders.
*   **Interactive Clipboard FAB**: An `ExtendedFloatingActionButton` dynamically shows "Paste here" / "Move here" options for pending clipboard items.
*   **Intelligent Duplicate Renaming**: Same-folder copy operations automatically bypass directory validation and append a ` (copy)` suffix.
*   **Recycle Bin Restore**: Preserves the original file paths by writing a `metadata.json` sidecar, allowing you to restore deleted files to their exact source.
*   **Privileged Explorer Mode**: Complete root and **Shizuku** integration to display file sizes, timestamps, and contents count for restricted folders.

### 🔒 **Private Wallet (Vault)**
*   **Secure PIN Protection**: Set up a custom 4-digit PIN with a haptic-feedback keypad to lock your sensitive files.
*   **Cryptographic Scrambling**: Encrypts and decrypts files using **SHA-256 password salting** and custom **byte XOR-scrambling**.
*   **Seamless Temporary Viewing**: Decrypts files temporarily to the cache for inline viewer viewing and automatically scrubs them on exit.

### 🌐 **Servers & Remote Connections**
*   **Local FTP Server**: Run a background FTP server via a persistent `FtpForegroundService` with notification-area status controls.
*   **Web Sharing Portal**: Host an offline directory listing server, support chunked/range media streaming for browser viewing, accept remote POST file uploads, and establish secure public tunnels via SSH client port-forwarding to `localhost.run`.
*   **Local QR Code Generation**: Swapped online sharing APIs for the **ZXing** library to render secure, local QR codes for Web Sharing URLs without transmitting data.
*   **Remote Connection Wizard**: Configure and save connections for **FTP**, **SFTP** (via `sshj`), **WebDAV**, and simulated **SMB/LAN** servers.
*   **Remote Explorer**: Manage remote archives directly, download, upload, create folders, and delete remote files.

### 🗜️ **Archive Management**
*   **Quick Context Actions**: Right-click context menus enable rapid extraction of `.zip`, `.7z`, and `.rar` archives.
*   **Configurable Compression**: Choose password protection (**AES-256**) and compression scales (Store, Fastest, Fast, Normal, Maximum, Ultra) when creating archives.
*   **Encrypted Archive Listing**: Decoupled from the UI thread using `zip4j`, allowing users to enter passwords to list or browse encrypted entries cleanly.
*   **Logical Nesting Fix**: Browse nested sub-archives without exposing temporary folder paths in breadcrumbs.

### 📺 **Premium Built-in Viewers & Editors**
*   **Jetpack PDF Viewer**: Rewritten from legacy PDFBox loading to Jetpack's native `androidx.pdf:pdf-viewer-fragment` (reducing load times from 6.4s to under 1.5s). Features native text selection, copy-paste, find-in-document search, coordinate-aligned highlights, high-resolution rendering on zoom, page indicator bubble, and a Go-to-Page dialog.
*   **Native Excel Spreadsheet Viewer**: Renders `.xls` and `.xlsx` sheets in a custom Compose-based scrollable grid with coordinate headers (A, B, C... / 1, 2, 3...) and cell borders. Includes filter/sorting controls and tab-based sheet navigation.
*   **Embedded Office Layout Engine (Docreader)**: High-fidelity layout rendering of Word (`.doc`, `.docx`) and PowerPoint (`.ppt`, `.pptx`) files, powered by the core engine from [`all-documents-reader`](https://github.com/ahmadullahpk/all-documents-reader).
*   **LaTeX Compiler & Editor**: Compile LaTeX offline using the built-in **Tectonic** compiler. Includes search/scroll navigation in the source view.
*   **Markdown Viewer**: Render markdown syntax, block/inline equations (`flutter_math_fork`), and search highlights in raw text.
*   **HTML Viewer**: Switch between styled WebView previews and monospace code editing with search highlights.
*   **Enhanced Audio Player**: Scan directories, auto-queue tracks, navigate next/prev, and queue tracks interactively using a sliding bottom sheet.
*   **Video Player Speed Control**: Toggle playback speeds directly from the video interface.
*   **Image Merger**: Multi-select images and stitch them vertically or horizontally into a single output JPEG.

### 🔍 **Smart Search & AI Integrations**
*   **Auto-Trigger Search**: Debounced search-on-type execution immediately fires when query lengths hit 2+ characters.
*   **AI Semantic Search**: Integrates semantic matching with similarity scores (e.g. `Semantic match score: 92%`) shown in search results.

### 📦 **System Integrations**
*   **Split APK Installation**: Support for `.apks`, `.xapk`, and `.apkm` split bundles with session install progress indicators.
*   **"Open With" History**: Bubbles recently used applications to the top of the dialog based on file extensions.
*   **Open In New Button**: Launch a file directly with alternative system apps via the quick action bar.

---

## 🛠️ Built With

| Technology | Purpose | Version |
| :--- | :--- | :--- |
| **Kotlin** | Programming Language | `2.2.0` |
| **Jetpack Compose** | Modern Declarative UI Framework | `2025.07.00` |
| **Android SDK** | Native Android Platform APIs | API `36` (Android 15+) |
| **androidx.pdf** | Native PDFium-backed document viewer | `1.0.0-alpha19` |
| **SSHJ** | SFTP connections and SSH port-forwarding | `0.39.0` |
| **Zip4j** | Comprehensive ZIP archive handling & encryption | `2.11.5` |
| **ZXing** | Offline QR Code generation | `3.5.3` |
| **ONNX Runtime** | Offline AI semantic vector search models | `1.22.0` |
| **Sora Editor** | Syntax-highlighted text and code editor | `0.23.6` |

---

## 🔨 Building from Source

### Prerequisites
*   **JDK**: 17 or higher
*   **Android SDK**: API level 36+
*   **Git**: For cloning the repository

### Build Instructions

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/techflyers/Prism-File-Explorer.git
    cd Prism-File-Explorer
    ```

2.  **Build the application**:
    To compile a debugging version or run tests:
    ```bash
    ./gradlew assembleDebug
    ```
    To compile the optimized release binaries split by target CPU architecture (ARMv7, ARM64, x86, x86_64):
    ```bash
    ./gradlew assembleRelease
    ```

3.  **Find the APKs**:
    *   Universal and CPU-split APKs will be located in `app/build/outputs/apk/release/` or `app/build/outputs/apk/debug/`.

---

## 🤝 Contributing

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### 📝 **Development Guidelines**

- Follow Kotlin coding conventions
- Write meaningful commit messages

---

## 📄 License

This project is licensed under the **GNU General Public License v3.0**.

```
Prism File Explorer - A modern Android file manager
Copyright (C) 2024 Raival-e

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

See the [LICENSE](LICENSE) file for the full license text.

---

## 💬 Support & Community

| Platform               | Purpose                       |
|------------------------|-------------------------------|
| **GitHub Issues**      | Bug reports, feature requests |
| **GitHub Discussions** | Community support, questions  |
| **Email**              | Private inquiries             |

**⭐ If you find this project useful, please consider giving it a star !**

[![Star History](https://img.shields.io/github/stars/techflyers/Prism-File-Explorer?style=social)](https://github.com/techflyers/Prism-File-Explorer/stargazers)
