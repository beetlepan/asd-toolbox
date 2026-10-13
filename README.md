# ASD 工具箱（驗證版）

這是驗證版專案，目的是在小米與三星實機確認：版本偵測、設定捷徑、玻璃導覽列、教學 WebView。

## 如何建置（GitHub Actions）
1. 在 GitHub 建一個**私有** repo，例如 `asd-toolbox`。
2. 把這個資料夾裡的所有檔案（包含隱藏的 `.github` 資料夾）上傳到 repo 根目錄。
3. 到 repo 的 Actions 分頁，等 `build-verify-apk` 跑完（約 3–6 分鐘）。
4. 點進該次執行，下方 Artifacts 下載 `asd-toolbox-verify`，解壓得到 `app-release.apk`。
5. 傳到手機安裝（需允許「安裝未知來源應用程式」）。

## 測試清單
- 首頁裝置資訊（型號、Android、安全性更新日期、ROM、Root 管理器）是否正確。
- 「寶可夢」「Root」頁：已安裝的軟體是否顯示版本與狀態；沒裝的是否顯示未安裝。
- 「設定」頁：四個捷徑能否直接開啟；路徑文字是否與手機實際一致。
- 「教學」頁：官網上傳 toolkit.html 後才會有內容。
- 首頁最下方「複製診斷資訊」→ 貼給我。

## 注意
- 目前以 debug 金鑰簽署，只用於測試；正式版要建專屬 keystore（發佈後不可更換）。
- 資料來源：線上 https://ateliersourcedeck.com/toolkit.json → 快取 → App 內建備援（assets/toolkit.json）。
- 新增偵測對象時，除了 toolkit.json 加 `pkg`，也要把套件名稱加進 `AndroidManifest.xml` 的 `<queries>`。
