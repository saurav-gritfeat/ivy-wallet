# Ivy Wallet (Personal Edition) 💸

[![Build APK](https://github.com/saurav-gritfeat/ivy-wallet/actions/workflows/build.yml/badge.svg)](https://github.com/saurav-gritfeat/ivy-wallet/actions/workflows/build.yml)
[![Release APK](https://github.com/saurav-gritfeat/ivy-wallet/actions/workflows/release.yml/badge.svg)](https://github.com/saurav-gritfeat/ivy-wallet/actions/workflows/release.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

A fast, private, and modern personal finance manager for Android built with **100% Kotlin** and **Jetpack Compose**. Engineered for offline privacy, clean design, and automated continuous delivery via GitHub Actions.

---

## 📱 Features

- **Personal Finance Tracking**: Track income, expenses, account balances, and budgets with instant reactive updates.
- **Modern Jetpack Compose UI**: Smooth animations, Material 3 design system, and custom pie charts & spending trends.
- **Privacy & Local-First Persistence**: All data stored locally using Room DB (SQLite) and DataStore with zero mandatory cloud lock-in.
- **Multi-Account & Currency Support**: Easily switch between accounts, categories, and exchange rates.
- **Planned Payments & Recurring Budgets**: Organize upcoming payments, loan tracking, and monthly allowances.
- **Automated CI/CD Workflows**: Direct cloud compilation and release generation via GitHub Actions — install directly on your device without local build toolchains.

---

## 🏗️ Architecture & Tech Stack

```mermaid
graph TD
    subgraph UI & Presentation
        App[":app Application Module"]
        Nav[":shared:ui:navigation"]
        UICore[":shared:ui:core (Design Tokens & Compose Components)"]
    end

    subgraph Feature Modules
        Home[":feature:home"]
        Transactions[":feature:transactions / edit-transaction"]
        Budgets[":feature:budgets"]
        Reports[":feature:reports / piechart"]
        Accounts[":feature:accounts / loans"]
    end

    subgraph Domain & Data Layer
        Domain[":shared:domain (Use Cases & Business Logic)"]
        DataCore[":shared:data:core (Room DB & Repositories)"]
        DataStore[":shared:data:core:datastore (Preferences)"]
    end

    App --> Home & Transactions & Budgets & Reports & Accounts
    Home & Transactions & Budgets --> Domain
    Domain --> DataCore
    DataCore --> DataStore
    Home & Transactions --> UICore
    App --> Nav
```

| Layer | Technologies |
| :--- | :--- |
| **Language & Concurrency** | 100% Kotlin, Kotlin Coroutines, Kotlin Flow |
| **UI Framework** | Jetpack Compose, Material 3, Compose Navigation |
| **Dependency Injection** | Hilt / Dagger, KSP |
| **Database & Persistence** | Room DB (SQLite ORM), AndroidX DataStore |
| **Networking & Serialization** | Ktor Client, Kotlinx Serialization |
| **Functional Utilities** | ArrowKt |
| **Build & CI/CD** | Gradle Kotlin DSL (`build.gradle.kts`), GitHub Actions (Temurin JDK 17) |

---

## 🚀 Building & Installing

### Automated Cloud Builds (No Android Studio Required)

1. **Continuous Integration Build**:
   - Push your code to `main` or trigger the **Build APK** workflow from GitHub Actions.
   - Download the generated `ivy-wallet-debug.apk` directly from workflow artifacts.

2. **Publish a Release**:
   - Push a tag:
     ```bash
     git tag v1.0.0
     git push origin v1.0.0
     ```
   - Or trigger **Release APK** manually from GitHub's **Actions** tab.
   - Download the APK directly from the repository's **Releases** page on your phone.

### Local Development (Optional)

Requirements:
- **JDK 17+**
- **Android SDK (Compile SDK 34, Min SDK 28)**

```bash
# Build Debug APK
./gradlew assembleDebug

# Build Optimized Demo APK
./gradlew assembleDemo
```

---

## 🗺️ Roadmap & Personal Extensions

- [x] Automated GitHub Actions Build & Release Workflows
- [ ] On-Device SMS & Bank Notification Ingestion Service
- [ ] Intelligent Merchant Extraction & Auto-Categorization
- [ ] On-Device Financial Insights / Copilot (LiteRT / MediaPipe LLM)
- [ ] Automated Backup & Export Sync

---

## 📄 License

This project is licensed under the [GNU General Public License v3.0 (GPL-3.0)](LICENSE).
