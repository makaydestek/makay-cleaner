# Makay Cleaner

Sideload Android temizleyici. Güncellemeler [GitHub Releases](https://github.com/makaydestek/makay-cleaner/releases) üzerinden dağıtılır.

## Sürüm / APK

- Tek sürüm kaynağı: `VERSION.txt`
- Teslimat klasörü: `APK/`
- Derleme: `APK-Hazirla.bat` veya `gradlew assembleRelease`

## Güncelleme yayınlama

1. `VERSION.txt` artır (veya `APK-Hazirla.bat` ile bump)
2. Release APK üret → `APK/MakayCleaner_vX.Y.Z.apk`
3. GitHub’da tag oluştur: `vX.Y.Z`
4. Release’e APK asset olarak yükle

Uygulama **Ayarlar → Güncelleme kontrol et** ile `releases/latest` API’sini okur.
