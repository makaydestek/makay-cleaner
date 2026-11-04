# Makay Cleaner

Sideload Android temizleyici (telefon + Android TV). Güncellemeler [GitHub Releases](https://github.com/makaydestek/makay-cleaner/releases) üzerinden dağıtılır (Makay Video Player ile aynı mantık).

## Mimari (özet)

| Katman | Konum | Rol |
|---|---|---|
| UI | `app/src/main/java/.../ui/` | Jetpack Compose ekranları; gezinme `AppNavigator` back stack |
| Tarama | `scanner/`, `util/JunkScanHelper` | Junk, yinelenen, blur, yüz, kalıntı |
| Temizlik | `FilePurger`, `SystemCacheReclaimer` | Silme + sistem önbelleği geri kazanımı |
| Veri | `data/` | Prefs + `AppSettingsStore` (DataStore) |
| Güvenlik | `security/` | PIN / biyometrik, `SecureStorage` |
| Güncelleme | `update/` | GitHub OTA: URL → SHA-256 → paket → imza |
| Arka plan | `worker/` | WorkManager otomatik temizlik |

**Teknik:** Kotlin, Compose Material3, minSdk 26, targetSdk 36, AGP 8.7.

## Kullanıcı akışı

1. Uygulama açılır → GitHub `releases/latest` kontrol edilir
2. Yeni sürüm varsa: **bildirim** + **diyalog** (Güncelle / Ertele)
3. Güncelle → APK + (varsa) `.sha256` indirilir, imza/paket doğrulanır, kurulum açılır
4. Ertele → bu oturumda diyalog kapanır; Ayarlar'dan tekrar kontrol edilebilir

## Yayınlama (siz)

Tek komut — `APK-Hazirla.bat`:

| Adım | Ne olur |
|---|---|
| 1 | Açık (commitlenmemiş) değişiklikler commit edilir |
| 2 | `VERSION.txt` otomatik artar (`X.Y.Z` → `X.Y.(Z+1)`) |
| 3 | `gradlew assembleRelease` → `APK/MakayCleaner_vX.Y.Z.apk` + `.apk.sha256` |
| 4 | Sürüm commit'i oluşturulur ve `origin main`'e itilir |
| 5 | GitHub Release oluşturulur, APK + SHA yüklenir (tag = `X.Y.Z`) |
| 6 | Eski release'ler + etiketleri silinir, yalnızca yenisi kalır |

**Release imza zorunlu:** `local.properties` içinde `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` tanımlı olmalı. Debug imzalı release yayını engellenir.

Yalnızca **sürüm dosyası** (`VERSION.txt`) commit'lenirken push edilir; APK ve derleme çıktıları `.gitignore` nedeniyle depoya girmez.

### Bir kez gerekenler

```bat
winget install --id GitHub.cli
gh auth login
```
veya `Gh-Giris.bat` dosyasını çalıştırın. Oturum `keyring` üzerinde kalıcıdır.

### Elle sürüm atlamak

`tools\bump-version.ps1` sürümü artırır. Manuel build için yalnızca `gradlew assembleRelease` yeterlidir; `APK\` klasörüne yazılır ve klasörde yalnızca son sürüm kalır.

### Manuel yüklemek isterseniz

Aynı isimli `.apk` ve `.apk.sha256` dosyalarını [Releases](https://github.com/makaydestek/makay-cleaner/releases) altına yükleyin. Etiket `X.Y.Z` biçiminde olmalı; uygulamadaki `normalizeVersion()` `v` önekini de kabul eder.

## Pro kapısı

`BuildConfig.PRO_GATING_ENABLED` şu an **false** (tüm özellikler ücretsiz). Açmak için `app/build.gradle.kts` içinde `true` yapın ve `local.properties`'e kodun SHA-256 hash'ini ekleyin:

```
PRO_UNLOCK_CODE_SHA256=<sha256-hex-of-UPPERCASE-code>
```

Düz metin unlock kodu kaynakta tutulmaz.

## Notlar

- Eski `GitHub-Release.bat` → `tools/archive/` altında arşivlendi.
- Otomatik güncelleme, APK'yı kurmadan önce 4 katman doğrulama yapar: URL allowlist → SHA-256 → paket adı → imza kesişimi. Bu yüzden `.sha256` dosyasını APK ile birlikte **mutlaka** yükleyin.
- Şifreli depo (Keystore) açılamazsa PIN kurulumu reddedilir; güvenlik ekranında uyarı gösterilir.
