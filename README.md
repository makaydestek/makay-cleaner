# Makay Cleaner

Sideload Android temizleyici. Güncellemeler [GitHub Releases](https://github.com/makaydestek/makay-cleaner/releases) üzerinden dağıtılır (Makay Video Player ile aynı mantık).

## Kullanıcı akışı

1. Uygulama açılır → GitHub `releases/latest` kontrol edilir
2. Yeni sürüm varsa: **bildirim** + **diyalog** (Güncelle / Ertele)
3. Güncelle → APK + (varsa) `.sha256` indirilir, imza/paket doğrulanır, kurulum açılır
4. Ertele → bu oturumda diyalog kapanır; Ayarlar’dan tekrar kontrol edilebilir

## Yayınlama (siz)

1. `APK-Hazirla.bat` veya `gradlew assembleRelease`  
   → `APK/MakayCleaner_vX.Y.Z.apk` + `.apk.sha256`
2. `gh auth login` (bir kez)
3. `GitHub-Release.bat` → tag `vX.Y.Z` + APK + SHA asset

Siteye elle de atabilirsiniz: aynı isimli `.apk` ve `.apk.sha256` dosyalarını [Releases](https://github.com/makaydestek/makay-cleaner/releases) altına yükleyin.
