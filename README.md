# Devil Audit

Android app that tracks which apps were uninstalled from the device and when they were last active.

- Reads Android usage statistics (events, aggregated stats, daily / weekly / monthly buckets)
- Compares them with the installed package list to find removed apps
- Scans storage for leftover `.apk` installers
- Live log of install, update and uninstall events
- Exportable text report

## Build

Push to `main`; GitHub Actions builds the debug APK and uploads it as the `app-debug-apk` artifact.

## Permissions

- Usage access (Settings > Special app access > Usage access)
- All files access (only for the residual APK scan)

Developer: Devil · Team Devil
