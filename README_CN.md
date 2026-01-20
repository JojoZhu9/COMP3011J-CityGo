# 🌍 CityGo - 智能旅行伴侣

> **Mobile Computing (COMP3011J) - 第 04 小组项目作业**

👋 **[Click here for English Version (返回英文版)](./README.md)**

---

## 📖 项目简介

**CityGo** 是一款专为提升旅行体验而设计的 Android 应用程序。本项目利用 **Google Maps SDK** 的强大功能，为用户提供智能路线规划、实时导航和无缝的城市探索体验。无论您是漫步在**哈尔滨**的街头，还是计划跨城旅行，CityGo 都是您可靠的口袋向导。

## 📥 APK 下载 (必读)

**⚠️ 注意：** 由于 GitHub 文件大小限制，安装包托管在外部链接，请点击下方链接下载：

👉 **[点击下载 CityGo.apk (Google Drive)](https://drive.google.com/file/d/17oRw_qTTKMSMIE5jjJ8eAfsoAA1IJME_/view?usp=drive_link)** 👈

## ✨ 核心功能

*   **📍 精准定位**：利用 Google 定位服务提供高精度的实时定位。
*   **🗺️ 交互式地图**：流畅的地图 UI，支持手势缩放、旋转及 Google 地图标记（Markers）交互。
*   **🚗 智能路线规划**：调用 Directions API 为步行、驾车或公共交通生成最佳路线。
*   **🏙️ 多城市支持**：支持在不同城市之间无缝切换，查看当地景点。
*   **📱 人性化设计**：基于 Material Design 原则构建，界面直观简洁。

## 📸 项目截图 (Screenshots)

| 首页预览 | 地图模式 | 路线规划 | 个人中心 |
|:---:|:---:|:---:|:---:|
| <img src="screenshots/home.png" alt="Home" width="200"/> | <img src="screenshots/map.png" alt="Map" width="200"/> | <img src="screenshots/route.png" alt="Route" width="200"/> | <img src="screenshots/profile.png" alt="Profile" width="200"/> |


## 🛠️ 技术栈与架构

*   **开发语言**: Java
*   **开发环境**: Android Studio Ladybug / Koala
*   **系统要求**: Android 7.0 (API 24) - Android 14 (API 34)
*   **核心库**:
    *   **Google Maps SDK for Android**: 用于地图显示和交互。
    *   **Google Places API**: 用于搜索兴趣点 (POI)。
    *   **AndroidX**: 用于向后兼容和 UI 组件构建。

## 🚀 开发者指南

如果您希望从源码构建本项目，请遵循以下步骤：

1.  **克隆仓库**
    ```bash
    git clone https://github.com/JojoZhu9/COMP3011J-CityGo.git
    ```

2.  **导入项目**
    *   在 Android Studio 中选择 `File` > `Open` 并选中项目目录。等待 Gradle 同步完成。

3.  **配置 API Key**
    *   **重要**：本项目需要有效的 Google Maps API Key 才能运行。
    *   请确保在 `AndroidManifest.xml` 中配置了您的 Key。
    *   该 Key 必须在 Google Cloud Console 中启用了 **Maps SDK for Android** 权限。

4.  **运行**
    *   连接 Android 真机或模拟器（**必须安装 Google Play Services**），点击运行按钮。

## 📝 评分说明 (Notes for Graders)

*   **权限**: 应用需要 **Location (定位)** 和 **Internet (网络)** 权限，请在首次启动时授予。
*   **运行环境**: 请确保测试设备或模拟器安装了 Google Play Services，否则地图无法加载。

## 👥 开发团队 (Group 04)

*   **Jiuzhou Zhu** (@JojoZhu9)
*   **Ciara Behan**
*   **Eva Barrett**

---
*© 2025 CityGo Project. All Rights Reserved.*
