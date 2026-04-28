# Paparcar 🚗🅿️

**Paparcar** is a community-driven, offline-first mobile application built with **Kotlin Multiplatform (KMP)**. It aims to solve the urban parking struggle by automatically detecting when a user leaves a parking spot and sharing that information in real-time with the community.

## 🌟 Key Features

- **Automated Spot Detection:** Uses Activity Recognition and GPS tracking to detect when you leave a parking spot without manual input.
- **Real-time Map:** Interactive map showing available parking spots nearby, updated via Firebase Firestore.
- **Offline-First Experience:** Reliable local caching using Room KMP for seamless operation even with poor connectivity.
- **Community-Powered:** A collaborative ecosystem where users help each other find parking effortlessly.
- **Cross-Platform:** Shared business logic and UI across Android and iOS using Compose Multiplatform.

## 🛠️ Tech Stack

- **Framework:** [Kotlin Multiplatform (KMP)](https://kotlinlang.org/docs/multiplatform.html)
- **UI:** [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- **Architecture:** Clean Architecture + MVI (Model-View-Intent)
- **Dependency Injection:** [Koin](https://insert-koin.io/)
- **Database:** [Room KMP](https://developer.android.com/kotlin/multiplatform/room)
- **Networking/Backend:** [Firebase (GitLive SDK)](https://firebase.google.com/docs/android/setup) (Auth, Firestore, Crashlytics)
- **Concurrency:** Kotlin Coroutines & Flow
- **Logging:** [Napier](https://github.com/AAiraa/Napier)
- **Navigation:** Compose Navigation Multiplatform

## 🏗️ Architecture & Project Structure

The project follows **Clean Architecture** principles, ensuring a separation of concerns and making the codebase testable and maintainable.

### Layers:
- **`domain`**: Contains business logic, entities (`Spot`, `GpsPoint`), and repository interfaces. Completely independent of any framework.
- **`data`**: Implements repository interfaces. Handles data sources (Room for local, Firebase for remote) and mappers.
- **`presentation`**: Follows the **MVI** pattern. ViewModels manage `State`, process `Intents`, and emit `Effects`.
- **`ui`**: Compose Multiplatform screens and components.
- **`core`**: Shared utilities, base classes, and cross-cutting concerns (logging, error handling).

### Module breakdown:
- `composeApp/src/commonMain`: Shared logic and UI for all platforms.
- `composeApp/src/androidMain`: Android-specific implementations (Foreground Services, Activity Recognition).
- `iosApp`: Swift wrapper for the iOS application.

## 🚀 How it Works (Detection Flow)

1. **Monitoring:** A Foreground Service monitors user activity (e.g., `STILL` -> `IN_VEHICLE`).
2. **Detection:** When the user starts moving in a vehicle, the app identifies the last known stationary location as a potential "released spot".
3. **Synchronization:** The spot is saved locally in Room and then synchronized to Firebase Firestore for other users to see.
4. **Consumption:** Nearby users receive real-time updates via Firestore listeners, and the new spot appears on their map instantly.

## 📖 Documentation

For more detailed information, check out the following documents:
- [Architecture Details](./Paparcar_Arquitectura.md)
- [Roadmap & Tech Debt](./Paparcar_Roadmap_TechDebt.md)
- [Full Product Roadmap](./Paparcar_Roadmap_Completo.md)

## 🛠️ Getting Started

### Prerequisites
- Android Studio Ladybug or later.
- Xcode (for iOS development).
- Kotlin Multiplatform Mobile plugin.

### Setup
1. Clone the repository.
2. Add your `google-services.json` to `composeApp/`.
3. Configure `local.properties` with your API keys (Google Maps, etc.).
4. Sync Gradle and run the `:composeApp` (Android) or `iosApp` (iOS) configuration.

---

*Built with ❤️ by the AppToLast Team.*
