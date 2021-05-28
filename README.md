[![PIA logo][pia-image]][pia-url]

# Private Internet Access

Private Internet Access is the world's leading consumer VPN service. At Private Internet Access we believe in unfettered access for all, and as a firm supporter of the open source ecosystem we have made the decision to open source our VPN clients. For more information about the PIA service, please visit our website [privateinternetaccess.com][pia-url] or check out the [Wiki][pia-wiki].

# CSI common library for Android and Apple platforms

With this library, clients from iOS and Android can submit debug information.

## Installation

### Requirements
 - Git (latest)
 - Xcode (latest)
 - IntelliJ IDEA (latest)
 - Gradle (latest)
 - ADB installed
 - NDK (latest)
 - Android 4.1+
 - Cocoapods

#### Download Codebase
Using the terminal:

`git clone https://github.com/pia-foss/mobile-common-csi.git *folder-name*`

type in what folder you want to put in without the **

#### Building

Once the project is cloned, you can build the binaries by running `./gradlew bundleDebugAar` or `./gradlew bundleReleaseAar` for Android. And, `./gradlew iOSBinaries` for iOS. You can find the binaries at `[PROJECT_DIR]/build/outputs/aar` and `[PROJECT_DIR]/build/bin/iOS` accordingly

## Usage

### Android 

To use this project in your Android apps, you need to import the generated AAR module and include the following dependencies in your application's gradle. See the project's `build.gradle` for the specific versions.

`
implementation 'io.ktor:ktor-client-okhttp:x.x.x'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-core:x.x.x'
implementation 'org.jetbrains.kotlinx:kotlinx-serialization-core:x.x.x'
`

### iOS

To use this project in your iOS apps, just add the library as a pod

`pod "PIACSI", :git => "http://github.com/pia-foss/mobile-common-csi`

After the pod install is completed, when you run your app, the PIACSI pod will generate the `PIACSI.framework`.

### Add new classes or change iOS project structure

When adding new classes or if you need to change the project structure of the `PIACSI` module you will need to update the `PIACSI.podspec` file. This file is located in the root path of the project.

## Documentation

#### Architecture

The library is formed by two layers. The common layer. Containing the business logic for all platforms. And, the bridging layer. Containing the platform specific logic being injected into the common layer.

Code structure via packages:

* `commonMain` - Common business logic.
* `androidMain` - Android's bridging layer, providing the platform specific dependencies.
* `iosApp` - iOS's bridging layer, providing the platform specific dependencies.

#### Significant Classes and Interfaces

* `CSIBuilder` - Public builder class responsible for creating an instance of an object conforming to `CSIAPI` interface.
* `CSIAPI` - Public interfaces defining the API to be offered by the library to the clients.
* `CSIHttpClient` - Class defining the certificate pinning logic on each platform.

## Contributing

By contributing to this project you are agreeing to the terms stated in the Contributor License Agreement (CLA) [here](/CLA.rst).

For more details please see [CONTRIBUTING](/CONTRIBUTING.md).

Issues and Pull Requests should use these templates: [ISSUE](/.github/ISSUE_TEMPLATE.md) and [PULL REQUEST](/.github/PULL_REQUEST_TEMPLATE.md).

## Authors

- Jose Blaya - [ueshiba](https://github.com/ueshiba)
- Juan Docal - [tatostao](https://github.com/tatostao) 

## License

This project is licensed under the [MIT (Expat) license](https://choosealicense.com/licenses/mit/), which can be found [here](/LICENSE).

## Acknowledgements

- Ktor - © 2020 (http://ktor.io)

[pia-image]: https://www.privateinternetaccess.com/assets/PIALogo2x-0d1e1094ac909ea4c93df06e2da3db4ee8a73d8b2770f0f7d768a8603c62a82f.png
[pia-url]: https://www.privateinternetaccess.com/
[pia-wiki]: https://en.wikipedia.org/wiki/Private_Internet_Access
