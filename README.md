# Sticker Studio Android

Native Android wrapper for the Sticker Studio editor, with a WhatsApp sticker ContentProvider integration.

## Features
- Keeps the existing Arabic RTL sticker editor in an Android WebView.
- Create/edit sticker packs in the editor.
- Tap **تصدير المجموعة** inside the Android app to prepare a pack and open WhatsApp's official `ENABLE_STICKER_PACK` confirmation flow.
- Converts each sticker to a transparent 512x512 WebP and tries quality levels until the image is <= 100 KB.
- Requires 3–30 static stickers per pack, matching WhatsApp's third-party sticker app requirements.

## Build APK
1. Open this folder in Android Studio (Giraffe or newer; recent version recommended).
2. Allow Gradle sync to download the Android Gradle Plugin and Kotlin plugin.
3. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. The debug APK should be at `app/build/outputs/apk/debug/app-debug.apk`.
5. Install the APK on Android, open Sticker Studio, create a pack with 3–30 stickers, then press **تصدير المجموعة**. WhatsApp will show its confirmation dialog; confirm to add the pack.

## Important notes
- This is a third-party sticker app integration, not an automatic bypass of WhatsApp confirmation. The user must confirm adding a pack in WhatsApp.
- Only static stickers are supported in this version.
- The app currently keeps pack data locally on the device.
- The current editor stores source sticker art in browser local storage; WebView DOM storage is enabled so edits persist in the app.
- This source project is prepared for Android Studio. It has not been compiled in this environment because Android SDK/Gradle are not installed here.

## WhatsApp integration
The app exposes `com.volt.stickerstudio.stickercontentprovider` with read permission `com.whatsapp.sticker.READ` and launches `com.whatsapp.intent.action.ENABLE_STICKER_PACK` after preparing the pack.

Requirements follow WhatsApp's sample integration: https://github.com/WhatsApp/stickers
