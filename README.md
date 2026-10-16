# ASD 工具箱

Atelier Source Deck 的 Android 工具箱：偵測手機上 Root 與寶可夢相關軟體的版本、提供手機設定捷徑與線上教學。
套件名稱 `com.ateliersourcedeck.toolbox`。與「ASD 工作室」App 是兩個各自獨立的 App。

## 資料來源
線上 https://ateliersourcedeck.com/toolkit.json → 本機快取 → App 內建備援（`assets/toolkit.json`）。
更新軟體版本、教學只要改 toolkit.json 並推上官網，不用重新打包 App。
新增偵測對象時，除了 toolkit.json 加 `pkg`，也要把套件名稱加進 `AndroidManifest.xml` 的 `<queries>`。

## 發佈新版本
1. 修改 `app/build.gradle` 的 `versionCode`（每次 +1）與 `versionName`。
2. 到 GitHub → Releases → Draft a new release，建立新標籤（例如 `v1.0.1`）並發佈；或本機執行 `git tag v1.0.1 && git push origin v1.0.1`。
3. Actions 的 `release-apk` 會自動建置、簽署，並把 `ASD-Toolbox.apk` 附加到該次 Release。
4. 固定下載連結：`https://github.com/beetlepan/asd-toolbox/releases/latest/download/ASD-Toolbox.apk`

## 簽署金鑰（重要）
- 正式版使用專屬 keystore，**遺失就無法再發佈能覆蓋更新的版本**，請至少保存兩份離線備份（不要放進 repo）。
- 金鑰與密碼只放在 repo 的 Settings → Secrets and variables → Actions：
  `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
