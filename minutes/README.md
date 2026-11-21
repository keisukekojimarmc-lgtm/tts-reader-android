# 議事録ビューア（Android）

Dropbox に保存した議事録（Markdown / `.md`）を、スマホで読みやすく表示するアプリです。
Obsidian の有料同期は不要で、Dropbox から直接読み込みます（読み取り専用）。

## できること

- **一覧**: 新しい順・月ごとに表示。日付・時刻・区分（CCBJ / 研修 など）・タイトル・概要の冒頭を表示
- **未読マーク**: 新しく追加・更新された議事録に青い点
- **絞り込み・検索**: 区分のチップで絞り込み。タイトル・参加者・本文を全文検索
- **読みやすい本文表示**
  - 冒頭のメタ情報（日時・参加者など）をカードにまとめ、録音・Notion などは「詳細」に折りたたみ
  - ToDo などの表はスマホ幅ではカード形式に変換（横スクロール不要）
  - （要確認）を蛍光ペン風に強調、`[00:00〜06:00]` のタイムスタンプは小さく表示
  - ダークモード対応、文字サイズ 80〜200%
- **目次**: 見出し（##, ###）へジャンプ
- **読み上げ**: 🎧 ボタンで、画面に見えている段落から日本語で読み上げ
  - 画面下の操作パネルで 一時停止／再開、前の文・次の文、速さ（0.5〜3.0倍、読み上げ中も即反映）、シークバーで好きな位置へ移動
  - 読んでいる段落を色付けし、自動でスクロール
  - 読み上げ中に段落をタップすると、そこから読み直し
- **オフライン**: 一度同期した議事録は端末に保存され、電波がなくても読める。更新時は変わったファイルだけ取得

## インストール

1. GitHub の **Actions** タブ →「議事録ビューア APK」→ 最新の実行 → **Artifacts** の `minutes-reader-apk` をダウンロード
2. zip を展開して `minutes-reader.apk` をスマホで開き、インストール（「提供元不明のアプリ」の許可が必要）

署名用キーストア（`minutes/debug.keystore`）をリポジトリに含めているため、新しい APK はそのまま上書きインストールできます（ログイン状態も保持）。

## 初回設定（1 回だけ）

Dropbox の API を使うため、自分用の「Dropbox アプリ」を登録します（無料）。

1. https://www.dropbox.com/developers/apps/create を開く
2. **Scoped access** → **Full Dropbox** を選び、任意の名前で作成
3. **Permissions** タブで `files.metadata.read` と `files.content.read` にチェック → **Submit**
4. **Settings** タブの **App key** をコピー
5. アプリで App key を入力 →「Dropbox で許可する」→ ブラウザで許可 → 表示されたコードをコピーしてアプリに貼り付け →「ログイン」

議事録フォルダの既定値は `/Kojima_unshared/議事録` です（設定画面で変更可）。サブフォルダ（年/月）内の `.md` もすべて読み込みます。

## 仕組み

- 認証: OAuth 2 PKCE（クライアントシークレット不要）。リフレッシュトークンは端末内にのみ保存
- 通信先: `api.dropboxapi.com`, `content.dropboxapi.com` のみ
- Markdown 変換: commonmark-java（GFM の表・取り消し線・チェックボックス・自動リンク）→ WebView

## ファイル構成

```
minutes/src/main/java/com/example/minutesreader/
├── MainActivity.kt / AppViewModel.kt   画面遷移・同期・読み上げの制御
├── dropbox/  DropboxClient.kt（API・PKCE 認証）, AuthStore.kt, RemoteFile.kt
├── data/     MinutesRepository.kt（キャッシュ・差分同期・検索）, MinuteParser.kt（ファイル名/フロントマター/概要）, Prefs.kt
├── render/   MinutesRenderer.kt（Markdown → スマホ向け HTML/CSS）
├── tts/      Speaker.kt, SpeechText.kt
└── ui/       SetupScreen / ListScreen / ViewerScreen / PlayerBar / SettingsScreen
```
