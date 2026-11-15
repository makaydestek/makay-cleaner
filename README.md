# Makay Cleaner

Sideload Android temizleyici (telefon + Android TV). Güncellemeler [GitHub Releases](https://github.com/makaydestek/makay-cleaner/releases) üzerinden dağıtılır.

## Mimari (özet)

| Katman | Konum | Rol |
|---|---|---|
| UI | `app/src/main/java/.../ui/` | Jetpack Compose ekranlar; gezinme `AppNavigator` back stack |
| Tarama | `scanner/`, `util/JunkScanHelper` | Junk, yinelenen, bulanık, yüz, kalıntı |
| Temizlik | `FilePurger`, `SystemCacheReclaimer` | Silme + sistem önbelleği geri kazanımı |
| Veri | `data/` | Prefs + `AppSettingsStore` (DataStore) + Room (çöp kutusu indeksi) |
| Güvenlik | `security/` | PIN (PBKDF2) / biyometrik, `SecureStorage` |
| Güncelleme | `update/` | GitHub OTA: URL allowlist → yönlendirme → SHA-256 → paket → imza |
| Arka plan | `worker/` | WorkManager otomatik temizlik, çöp kutusu bakımı |

**Teknik:** Kotlin, Compose Material3, minSdk 26, targetSdk 36, AGP 8.7.3, Gradle 8.9, KSP + Room 2.6.1.

## Yerelleştirme

- Tüm arayüz, bildirim, widget ve öneri metinleri `res/values/strings.xml` (Türkçe) ve
  `res/values-en/strings.xml` (İngilizce) içinde tanımlıdır.
- `android:localeConfig` tanımlıdır; Android 13+ cihazlarda Ayarlar → Uygulamalar →
  Makay Cleaner → Dil bölümünden uygulama dili değiştirilebilir.
- Uygulama içi dil seçici (`LanguageManager`) Android 13+ için `LocaleManager` üzerinden
  çalışır; daha eski sürümlerde sistem dili kullanılır.

## Kullanıcı akışı

1. Uygulama açılır → GitHub `releases/latest` kontrol edilir (12 saatlik aralığa saygılı)
2. Yeni sürüm varsa: **bildirim** + **diyalog** (Güncelle / Ertele)
3. Güncelle → APK + (varsa) `.sha256` indirilir; her yönlendirme adımı doğrulanır,
   boyut sınırı uygulanır, paket adı + `versionCode` + imza kontrol edilir, kurulum açılır
4. Ertele → bu oturumda diyalog kapanır; Ayarlar'dan tekrar kontrol edilebilir

## Temizlik güvenliği

- Tek dokunuş **Optimizasyon** yalnızca önbellek/geçici dosyaları kalıcı siler;
  APK ve kalıntılar **çöp kutusuna** taşınır (48 saat geri alınabilir).
- Eski indirmeler (PDF, belge, fotoğraf, arşiv) otomatik silinmez; yalnızca
  `.apk/.tmp/.part/.crdownload` gibi gerçek artık uzantıları aday olur.
- Yinelenen dosya/fotoğraf/video tespitinde kısmi hash yalnızca aday üretir;
  **silmeden önce tam SHA-256** doğrulaması yapılır.
- `Android/media` (WhatsApp/Telegram indirilmiş medyası) kalıntı taramasından
  bilinçli olarak hariç tutulur.
- Tüm silme işlemleri onay diyaloğundan geçer.

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

**Release imza zorunlu:** `local.properties` içinde `RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` tanımlı olmalı.
Debug imzalı release yayını engellenir.

Yalnızca **sürüm dosyası** (`VERSION.txt`) commit'lenirken push edilir; APK ve derleme
çıktıları `.gitignore` nedeniyle depoya girmez.

### Bir kez gerekenler

```bat
winget install --id GitHub.cli
gh auth login
```

veya `Gh-Giris.bat` dosyasını çalıştırın. Oturum `keyring` üzerinde kalıcıdır.

### Elle sürüm atlamak

`tools\bump-version.ps1` sürümü artırır. Manuel build için yalnızca
`gradlew assembleRelease` yeterlidir; `APK\` klasörüne yazılır ve klasörde
yalnızca son sürüm kalır.

### Manuel yüklemek isterseniz

Aynı isimli `.apk` ve `.apk.sha256` dosyalarını [Releases](https://github.com/makaydestek/makay-cleaner/releases)
altına yükleyin. Etiket `X.Y.Z` biçiminde olmalı; uygulamadaki `normalizeVersion()`
`v` önekini de kabul eder.

## Pro kapısı

`BuildConfig.PRO_GATING_ENABLED` şu an **false** (tüm özellikler ücretsiz).
Açmak için `app/build.gradle.kts` içinde `true` yapın ve `local.properties`'e
kodun SHA-256 hash'ini ekleyin:

```
PRO_UNLOCK_CODE_SHA256=<sha256-hex-of-UPPERCASE-code>
```

Düz metin unlock kodu kaynakta tutulmaz.

## Güvenlik notları

- PIN'ler PBKDF2-HMAC-SHA256 (120.000 tur) + rastgele tuz ile saklanır; 5 hatalı
  denemede üstel bekleme uygulanır. Tuz yoksa doğrulama reddedilir (fail-closed).
- Şifreli depo (Keystore) açılamazsa uygulama kilitsiz açılmaz; kullanıcı cihaz
  ekran kilidiyle doğrulayıp kilidi yeniden kurar.
- Kilit etkinken `FLAG_SECURE` ile ekran görüntüsü ve son uygulamalar önizlemesi
  engellenir; güncelleme diyaloğu kilit ekranının üstünde gösterilmez.
- Otomatik güncelleme, APK'yı kurmadan önce çok katmanlı doğrulama yapar:
  URL allowlist → yönlendirme zinciri → SHA-256 → paket adı → versionCode → imza.
  Bu yüzden `.sha256` dosyasını APK ile birlikte **mutlaka** yükleyin.
- Şifreli depo (Keystore) açılamazsa PIN kurulumu reddedilir; güvenlik ekranında
  uyarı gösterilir.

## Notlar

- Eski `GitHub-Release.bat` → `tools/archive/` altında arşivlendi.
- `APK/` klasöründe yalnızca son sürümün APK'sı, SHA'sı ve R8 mapping dosyası tutulur.
- `keystore/` klasörü `.gitignore` ile korunur; yedeğini mutlaka alın.
