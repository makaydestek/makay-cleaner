# Makay Cleaner

Sideload Android temizleyici. Güncellemeler [GitHub Releases](https://github.com/makaydestek/makay-cleaner/releases) üzerinden dağıtılır (Makay Video Player ile aynı mantık).

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
| 6 | Eski release'ler + etiketleri silinir, yalnızca yenis kalır |

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

## Notlar

- `GitHub-Release.bat` artık gerekli değil; yalnızca `gh` CLI ile tek başına sürüm yüklemek istediğinizde yedek olarak durur.
- Otomatik güncelleme, APK'yı kurmadan önce 4 katman doğrulama yapar: URL allowlist → SHA-256 → paket adı → imza kesişimi. Bu yüzden `.sha256` dosyasını APK ile birlikte **mutlaka** yükleyin.
