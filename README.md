# 🌍 CityGo - Smart Travel Companion

[![Language](https://img.shields.io/badge/Language-Java-orange.svg)]()
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)]()
[![SDK](https://img.shields.io/badge/Map-Google%20Maps%20SDK-blue.svg)]()
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)]()

> **Mobile Computing (COMP3011J) - Group 04 Project Submission**

👋 **[点击这里查看中文说明 (Click here for Chinese Version)](./README_CN.md)**

---

## 📖 Introduction

**CityGo** is a comprehensive Android application designed to enhance the travel experience. By leveraging the power of the **Google Maps SDK**, CityGo provides users with intelligent route planning, real-time navigation, and seamless city exploration features. Whether you are navigating the streets of **Harbin** or planning a cross-city journey, CityGo is your reliable pocket guide.

## 📥 APK Download (Required)

**⚠️ Important:** Due to GitHub's file size policies, the APK is hosted externally. Please download it via the link below:

👉 **[Download CityGo.apk (Google Drive)](https://drive.google.com/file/d/17oRw_qTTKMSMIE5jjJ8eAfsoAA1IJME_/view?usp=drive_link)** 👈

## ✨ Key Features

*   **📍 Precision Positioning**: Utilizes Google Location Services to provide high-accuracy real-time location.
*   **🗺️ Interactive Map Interface**: A smooth, responsive map UI supporting gestures, zooming, and Markers via Google Maps.
*   **🚗 Intelligent Route Planning**: Generates optimal routes for walking, driving, or public transit using the Directions API.
*   **🏙️ Multi-City Support**: Seamlessly switch contexts between cities to view local attractions.
*   **📱 User-Centric Design**: Built with Material Design principles for maximum usability.

## 📸 Screenshots

| Home | Map View | Route Planning | Profile |
|:---:|:---:|:---:|:---:|
| <img src="screenshots/home.png" alt="Home" width="200"/> | <img src="screenshots/map.png" alt="Map" width="200"/> | <img src="screenshots/route.png" alt="Route" width="200"/> | <img src="screenshots/profile.png" alt="Profile" width="200"/> |


## 🛠️ Tech Stack

*   **Language**: Java
*   **IDE**: Android Studio Ladybug / Koala
*   **SDK Level**: Min SDK 24 (Android 7.0) -> Target SDK 34 (Android 14)
*   **Core Libraries**:
    *   **Google Maps SDK for Android**: Map display and interaction.
    *   **Google Places API**: For searching points of interest.
    *   **AndroidX**: UI components and compatibility.

## 🚀 Getting Started (For Developers)

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/JojoZhu9/COMP3011J-CityGo.git
    ```

2.  **Open in Android Studio**
    *   File > Open > Select project directory.
    *   Wait for Gradle sync.

3.  **API Key Configuration**
    *   **Important**: This project requires a valid Google Maps API Key.
    *   Ensure your API Key is correctly configured in `AndroidManifest.xml` (or `local.properties` if hidden).
    *   The key must have **Maps SDK for Android** enabled in the Google Cloud Console.

4.  **Run**
    *   Connect a device or Emulator (with Google Play Services installed) and click **Run**.

## 📝 Notes for Graders

*   **Permissions**: The app requires **Location** and **Internet** permissions. Please grant them on first launch.
*   **Google Play Services**: Please ensure the test device/emulator has Google Play Services installed to load the map correctly.

## 👥 Contributors (Group 04)

| Name | Role | GitHub |
|:---:|:---:|:---:|
| **Jiuzhou Zhu** | Member | [@JojoZhu9](https://github.com/JojoZhu9) |
| **Ciara Behan** | Member | - |
| **Eva Barrett** | Member | - |

---
*© 2025 CityGo Project. All Rights Reserved.*
