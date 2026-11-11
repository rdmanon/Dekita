This is a Kotlin Multiplatform project targeting Android and iOS. Each platform has its own native UI;
only the code below the screens is shared.

* [/sharedLogic](./sharedLogic/src) holds everything both apps share: domain types and rules, the in-memory
  mock data layer, and the view models ([commonMain](./sharedLogic/src/commonMain/kotlin)). It also builds the
  `SharedLogic` framework that the iOS app links.

* [/androidApp](./androidApp/src/main) is the Android app, written in Jetpack Compose with Material 3, following
  the Android artboards of the design canvas.

* [/iosApp](./iosApp/iosApp) is the iOS app, written in SwiftUI, following the iOS artboards (Liquid Glass on
  the navigation layer only).

### How the iOS app is put together

* The iOS app has a SwiftUI shell ([ContentView.swift](./iosApp/iosApp/ContentView.swift)): a `TabView` that iOS 26
  draws in Liquid Glass, with Search as its own tab. Every screen is SwiftUI.
* SwiftUI screens use the shared Kotlin view models through small bridges in
  [sharedLogic/src/iosMain/.../ios](./sharedLogic/src/iosMain/kotlin/tech/vauldex/dekita/ios), which turn each
  `StateFlow` into a callback and clear the view models when the screen goes away.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :sharedLogic:testAndroidHostTest`
- iOS tests: `./gradlew :sharedLogic:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…