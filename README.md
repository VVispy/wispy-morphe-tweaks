# Wispy Tweaks

Wispy's patches for use with Morphe.

## ❓ About

Standalone patches that can be applied on their own or alongside other patch bundles.

- **Instagram: Whitelist followed accounts.** Long-press the Home tab to pick followed accounts
  whose home feed posts and stories are the only ones shown (whitelist) or are hidden (blacklist).
  Derived from [Piko](https://github.com/crimera/piko), see [NOTICE](NOTICE).

### How to use these patches

Click the button below, or manually add this repo url to Morphe Manager: https://github.com/VVispy/wispy-morphe-tweaks

<a href="https://morphe.software/add-source?github=VVispy/wispy-morphe-tweaks">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0](https://github.com/VVispy/wispy-morphe-tweaks/releases/tag/v1.0.0)**&nbsp;&nbsp;&middot;&nbsp;&nbsp;`main`&nbsp;&nbsp;&middot;&nbsp;&nbsp;**1 patches** across **1 apps**&nbsp;&nbsp;&middot;&nbsp;&nbsp;[Full details](PATCHES.md)

| # | App | Patches | Version | Package |
|---|---|---|---|---|
| 1 | [**Instagram**](PATCHES.md#instagram-cominstagramandroid) | 1 | `452.1.0.53.64` | [`com.instagram.android`](https://play.google.com/store/apps/details?id=com.instagram.android) |
<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

Wispy Tweaks are licensed under the [GNU General Public License v3.0](LICENSE)
