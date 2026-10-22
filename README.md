# HAMK Inventory for Android

HAMK Inventory for Android is an Android WebView app for the HAMK AI & Robotics
Club inventory website. The app requires Android 7.0 (API 24) or later, an
internet connection, and an available website server. It does not store or
cache inventory data offline.

## Download

Download and install [`releases/HAMK-Inventory.apk`](releases/HAMK-Inventory.apk)
on an Android phone. Android may ask you to allow APK installation from the
browser or file manager used to open the download. This debug APK is for private
testing and is not a Google Play release.

## Build

Install JDK 17 and Android SDK Platform 35, then run the Gradle wrapper:

```powershell
.\gradlew.bat --no-daemon "-PHAMK_SITE_URL=https://your-https-host.example" assembleDebug
```

On Windows, the repository publisher script builds the APK and copies it to
`releases/HAMK-Inventory.apk`:

```powershell
.\build-and-publish.ps1 -SiteUrl https://your-https-host.example
```

Use an HTTPS website address that you control. A temporary Cloudflare Quick
Tunnel address may expire; if it does, update the address and rebuild the APK.
Anyone can download this public repository and APK, but the app still requires
an authorized website account to sign in. Do not put email addresses, passwords,
tokens, or other secrets in the repository.

## App behavior

- Loads the website using HTTPS and keeps website login cookies on the device.
- Offers an in-app retry screen if the website or internet connection is unavailable.
- Allows file uploads and downloads from the configured website.
- Opens links to other websites in the external browser.
