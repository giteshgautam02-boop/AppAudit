# App Audit — starter project

Kotlin + Jetpack Compose app jo `UsageStatsManager` aur `PackageManager` use karke
batata hai ki kaunsi app uninstall hui, kab active thi.

## GitHub se build karna (Android Studio ke bina)

Is folder mein ab pura Gradle project hai (`settings.gradle.kts`, root aur app
ka `build.gradle.kts`, `.gitignore`, `.github/workflows/android-build.yml`) —
seedha GitHub pe push karo, Actions khud APK bana dega.

1. GitHub pe ek naya **empty repo** banao (koi README/license mat add karna,
   warna push conflict aayega).
2. Is `AppAudit` folder ko apne computer pe le jao, phir terminal mein:

   ```bash
   cd AppAudit
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<tumhara-username>/<repo-name>.git
   git push -u origin main
   ```

3. Push hote hi GitHub repo ke **Actions** tab mein "Build APK" workflow
   automatically chalega (ye `.github/workflows/android-build.yml` mein
   defined hai).
4. Wo workflow run complete hone ke baad, usi run ke page ke neeche
   **Artifacts** section mein `app-debug-apk` milega — download karo, usme
   `app-debug.apk` hoga. Isko phone mein install kar sakte ho (Unknown
   sources allow karna padega).
5. Future mein jab bhi `main` branch pe push karoge, naya APK apne aap ban
   jayega — manually trigger karna ho to Actions tab mein **"Run workflow"**
   button bhi hai (`workflow_dispatch` ki wajah se).

> Agar build fail ho (dependency version mismatch jaisa error), Actions log
> mein exact error dikhega — usually kisi ek library ka version thoda purana
> pad jaata hai, latest version se replace karna padta hai. Wo error paste
> kar dena, main fix bata dunga.

## Setup (Android Studio)

1. **New Project → Empty Activity (Compose)**, package name:
   `com.example.appaudit` (ya apna naam rakho, phir sab files ke `package` line
   wahi rename kar dena).
2. Is folder ke andar jo `app/src/main/...` files hain, unhe apne naye project
   ke usi path pe copy-paste kar do (existing `MainActivity.kt` overwrite ho
   jayegi, `AndroidManifest.xml` merge kar lena apne existing manifest ke saath).
3. `app/build.gradle.kts` mein ye dependencies add karo:

```kotlin
plugins {
    id("com.google.devtools.ksp") // Room ke liye
}

dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Compose BOM/material3 — agar Empty Compose Activity template se
    // banaya hai to ye already honge
}
```

   Root `build.gradle.kts` mein KSP plugin version add karna hoga
   (Android Studio "Empty Activity" template banate waqt khud suggest kar
   dega agar KSP missing ho).

4. Build & Run.

## Pehli baar chalane par

- App khulte hi "Enable Usage Access" button dikhega → tap karo → system
  Settings khulegi → apni app ko **Usage access: ON** karo → wapas app mein aa
  ke "I've enabled it — refresh" dabao.
- Isse pehle koi "known_packages" snapshot nahi hota, toh **pehla scan sirf
  baseline banata hai** (abhi jo apps installed hain unki list save karta
  hai). Uninstalls agle scan se dikhna shuru honge — matlab app install karne
  ke baad koi app uninstall karo, 15 min ke andar (ya turant, kyunki
  `PackageChangeReceiver` bhi laga hua hai) wo list mein aa jayegi.

## Kaise kaam karta hai (short version)

| Piece | Role |
|---|---|
| `UsageStatsHelper` | Permission check + raw usage events ko per-app summary mein convert karta hai |
| `InstalledAppsRepository` | Har scan pe "ab kya installed hai" ko "pehle kya tha" se diff karta hai |
| `UninstallCheckWorker` | Har 15 min mein background scan chalata hai (`WorkManager`) |
| `PackageChangeReceiver` | Install/uninstall hote hi turant scan trigger karta hai |
| `AppDatabase` / Room | History store karta hai (list restart ke baad bhi rehti hai) |
| `MainActivity` | Compose UI — permission gate + list |

## Ek zaroori baat

Ye app device ke **har** app ka install/uninstall history padhti hai — ye
sirf apne khud ke phone pe, apni marzi se use karna. Agar kabhi Play Store pe
publish karne ka socho, Google ki policy hai ki `PACKAGE_USAGE_STATS`
permission use karne wali apps ko apna purpose clearly disclose karna padta
hai privacy policy mein — "Usage Access Permission declaration form" bharna
padta hai console mein.
