# Modder Tools — Android app

A native Android shell (Java) around the Modder Tools web app. One full-screen
WebView loads the bundled site from `app/src/main/assets/www/index.html`
(the same SPA used on the website — Home, tool details, downloads, and the
mobile fixes are all unchanged) and a floating "glass" bottom nav bar adds
four tabs on top of it:

| Tab | What it shows |
|---|---|
| Home | Tools grid, search, categories, banners — identical to the website |
| Search | Combined search across tools **and** files |
| Files | A second grid/detail/download flow, same UI as tools, reading from a new `/files` Firebase node |
| App Info | Developer bio, links, and app version |

Tapping a tab just changes the WebView's URL hash (`#/home`, `#/search`,
`#/files`, `#/app-info`) — there's one WebView instance, so switching tabs is
instant and there's no page reload.

## Firebase

The same `firebaseConfig` object already in your website's `index.html` /
`admin.html` is baked into `app/src/main/assets/www/index.html` and
`admin.html` — find the `const firebaseConfig = { ... }` block near the top
of each file's `<script>` tag and replace the values if you ever need to
point the app at a different Firebase project. No native Firebase SDK or
`google-services.json` is used — the app talks to Firebase purely over HTTPS
from inside the WebView, exactly like the website does, so there's nothing
else to configure.

`admin.html` is **not** bundled into the app on purpose — shipping the admin
panel inside something end users install isn't good practice, even behind
an access key. It's delivered separately (same `firebaseConfig` placeholder,
easy to find/replace) and now has a full **Files** section alongside Apps,
with the same add/edit/draft/publish workflow — keep managing apps, files,
categories and banners from there on the web as usual.

## App icon

Generated from the `logo.png` you provided — legacy launcher icons for every
density plus a proper adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml`)
with a white background and the logo as the foreground layer. To change it
later, drop a new square PNG (ideally 512×512+) in and regenerate the
mipmap set with any Android icon generator, or ask to have it redone.

## Building locally (optional)

You need Android Studio or just a JDK 17 + Android SDK on PATH:

```bash
cd ModderToolsApp
gradle assembleDebug
# APK lands at app/build/outputs/apk/debug/app-debug.apk
```

No Gradle wrapper jar is committed (see below), so either use your own local
Gradle install or open the project in Android Studio, which provisions
Gradle automatically.

## Building via GitHub Actions (the zip is ready for this)

1. Push this whole folder to a new GitHub repo (as the repo root, or adjust
   the `working-directory`/paths in `.github/workflows/build-apk.yml` if you
   nest it inside a larger repo).
2. Push to `main`, open a PR, or trigger the workflow manually from the
   **Actions** tab (`workflow_dispatch`).
3. The **Build Modder Tools APK** workflow runs automatically:
   - Sets up JDK 17
   - Sets up the Android SDK
   - Installs Gradle 8.7 via `gradle/actions/setup-gradle` — **no
     `gradle-wrapper.jar` binary is committed to this repo**, which is what
     normally trips up fresh CI setups (a missing/corrupt wrapper jar is the
     #1 cause of "permission denied" / "could not find or load main class"
     failures in Android GitHub Actions). Installing Gradle directly on the
     runner sidesteps that completely.
   - Runs `gradle assembleDebug`
   - Uploads the resulting `app-debug.apk` as a build artifact you can
     download from the workflow run's summary page.

This produces a **debug-signed APK** (signed automatically with a CI-local
debug keystore), which installs and runs fine for testing/sideloading. It is
**not** suitable for the Play Store, which requires a release build signed
with your own upload key. If/when you want that, you'll need to:

- Generate a keystore (`keytool -genkey -v -keystore release.keystore ...`)
- Add it (base64-encoded) plus its passwords as repository secrets
- Add a `signingConfigs { release { ... } }` block to `app/build.gradle` and
  switch the workflow to `gradle assembleRelease`

Ask if you'd like that wired up — it's a deliberate omission here to keep
the default build 100% dependency- and secret-free so it always succeeds.

## Project structure

```
ModderToolsApp/
├── build.gradle, settings.gradle, gradle.properties     (root Gradle config)
├── app/
│   ├── build.gradle                                     (app module — Java, no Firebase SDK)
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/moddertools/app/MainActivity.java   (WebView shell + glass nav logic)
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml                 (WebView + floating nav bar)
│   │   │   ├── drawable/ic_nav_*.xml                    (nav icons, accent-tinted in code)
│   │   │   ├── drawable/bg_nav_glass.xml                (glass pill background)
│   │   │   ├── mipmap-*/                                (launcher icons, from logo.png)
│   │   │   └── values/colors.xml, strings.xml, themes.xml
│   │   └── assets/www/index.html                         (the web app — admin.html is not bundled)
└── .github/workflows/build-apk.yml                      (CI build → APK artifact)
```

## What the native shell adds on top of the website

- **Download handling** — tapping a download link inside the WebView hands
  it to the system (browser / Download Manager) instead of trying to render
  a binary file inline, which is what a plain WebView would otherwise do.
- **External links** — banner external links, share-overlay social links,
  and developer/social links on tool and file pages open in the device's
  browser/app instead of inside the WebView.
- **Offline state** — a simple "you're offline" screen with retry, shown
  instead of a broken WebView when there's no connection.
- **Pull-to-refresh** — swipe down on Home/Files/Search/App Info to reload.
- **Back button** — follows WebView history (so hash-route navigation, e.g.
  tool details → download, works with the system back button) before
  falling back to closing the app.
