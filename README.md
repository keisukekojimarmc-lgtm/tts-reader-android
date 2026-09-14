# TTS Reader (Android)

他のアプリに表示されている文章を、自動スクロールしながら日本語で読み上げる Android アプリです。
Kotlin + Jetpack Compose (Material 3) で実装しています。

## 主な機能

- **画面テキストの取得**: `AccessibilityService` で前面アプリのノードツリーを走査し、画面内に見えているテキストをレイアウト順に取得
- **日本語読み上げ**: `TextToSpeech`（`Locale.JAPANESE`）。速度 0.5x〜2.0x・声の高さを設定画面で調整可能
- **自動スクロール**: 読み上げ中の要素が画面下部（高さの 75%）に達したら `ACTION_SCROLL_FORWARD` を実行し、新しく現れたテキストだけを続けて読む
- **重複読み上げ防止**: 正規化したテキストの履歴を持ち、スクロール後も画面に残っている要素・固定ヘッダーなどは二度読みしない
- **フローティングボタン**: `SYSTEM_ALERT_WINDOW`（`TYPE_APPLICATION_OVERLAY`）で再生/停止ボタンを表示。ドラッグで移動可
- **初回セットアップ画面**: ユーザー補助とオーバーレイの権限状態を表示し、それぞれの設定画面へ誘導。設定から戻ると自動で再確認

## 動作要件

- minSdk 26 (Android 8.0) / targetSdk 35
- 日本語対応の TTS エンジン（Google 音声サービスなど）がインストールされていること

## ファイル構成

```
app/src/main/
├── AndroidManifest.xml                      SYSTEM_ALERT_WINDOW、Activity、AccessibilityService の宣言
├── res/xml/accessibility_service_config.xml ユーザー補助サービスの設定（購読イベント・フラグ）
├── res/values/strings.xml                   日本語 UI 文言
└── java/com/example/ttsreader/
    ├── MainActivity.kt                      Compose エントリ。権限状態に応じて Setup / Home を切替
    ├── ui/
    │   ├── SetupScreen.kt                   初回起動時の権限案内画面
    │   ├── HomeScreen.kt                    状態表示・速度/ピッチ設定・使い方
    │   └── theme/Theme.kt                   Material 3 テーマ
    ├── service/
    │   └── ReaderAccessibilityService.kt    サービス本体。TTS・Controller・Overlay を所有し、各部品へ委譲
    ├── reading/
    │   ├── ReadingController.kt             読み上げ + 自動スクロールの状態機械（中核）
    │   ├── ScreenTextExtractor.kt           ノードツリー → 可視テキスト片（TextSegment）の抽出
    │   ├── ScrollHelper.kt                  スクロール可能ノードの探索と ACTION_SCROLL_FORWARD 実行
    │   ├── ReadHistory.kt                   読み上げ済みテキストの履歴（重複防止）
    │   ├── TextChunker.kt                   文単位に分割し TTS に渡せる長さへ整形
    │   ├── TextNormalizer.kt                重複判定用の正規化
    │   ├── TextSegment.kt                   テキスト片（文字列・座標・ページ番号）
    │   ├── ScreenAccess.kt                  Controller がサービスに要求する最小インターフェース
    │   └── ReaderStateHolder.kt             サービス/オーバーレイ/画面で共有する状態（StateFlow）
    ├── tts/TtsEngine.kt                     TextToSpeech ラッパー（初期化・速度・完了通知）
    ├── overlay/
    │   ├── FloatingOverlay.kt               WindowManager に ComposeView を載せる
    │   ├── FloatingButton.kt                再生/停止 FAB（ドラッグ対応）
    │   └── OverlayLifecycleOwner.kt         Activity 外で Compose を動かすための Lifecycle 提供
    ├── settings/ReaderPreferences.kt        速度・ピッチ・オーバーレイ表示・ボタン位置の永続化
    └── util/PermissionUtils.kt              権限チェックと設定画面への Intent
app/src/test/.../reading/                    Android 非依存ロジックの単体テスト
```

## 設計方針

### 責務の分離

- `ReaderAccessibilityService` はフレームワークとの接点に徹し、ロジックは `ReadingController` に委譲します。
- `ReadingController` はサービスを直接参照せず `ScreenAccess` インターフェース経由で画面にアクセスするため、差し替えやテストが容易です。
- `TextChunker` / `ReadHistory` / `TextNormalizer` は Android API に依存しない純粋な Kotlin で、JVM 上で単体テストできます。
- 3 者（サービス・オーバーレイ・アプリ画面）は同一プロセスなので、`ReaderStateHolder` の `StateFlow` を共有するだけで状態を同期します。

### 読み上げと自動スクロールの流れ

1. **開始**: `rootInActiveWindow` を深さ優先で走査し、`isVisibleToUser` かつ画面矩形と交差するノードの `text`（無ければ `contentDescription`）を取得します。パスワード欄・ヒント表示中の入力欄は除外します。
2. **重複排除**: 各テキストを正規化して `ReadHistory` に登録し、登録済みならスキップします。履歴は読み上げセッション単位でリセットされます。
3. **発話**: 文末（。！？!? 改行）で分割し、約 300 文字を目安に結合して `TextToSpeech.speak(QUEUE_ADD)` に渡します。utteranceId で完了を追跡し、完了ごとに次の発話へ進みます。
4. **先読みスクロール**: 発話を開始した要素の下端が画面高さの 75% を超えていたら、その時点で `ACTION_SCROLL_FORWARD` を実行します。スクロール対象は「前方向にスクロール可能で、可視面積が最大のノード」から順に試します。
5. **スクロール完了待ち**: `TYPE_VIEW_SCROLLED` イベントの後 350ms（イベントが来ない場合は 800ms、最長 2 秒）待ってから再取得します。スクロール後は座標が古くなるため、ページ番号を進め、古いページのセグメントでは下部判定を行いません。
6. **終了**: スクロールが `false` を返す（末尾到達）か、3 回連続で新しいテキストが見つからなかった場合、残りを話し切ってから「最後まで読み上げました」と告げて停止します。

### 権限まわり

- ユーザー補助の有効化は `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` と、サービスの `instance` の両方で判定します。
- オーバーレイは `Settings.canDrawOverlays` で判定し、権限が無い間はボタンを出しません。
- `MainActivity` は `LifecycleResumeEffect` で設定画面から戻るたびに再確認し、サービスにもオーバーレイ表示の更新を促します。

## ビルド

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Android Studio Ladybug 以降（AGP 8.7 / Kotlin 2.0 / JDK 17）を想定しています。

## 既知の制限

- テキストを持たない画像や Canvas 描画のみの画面は読めません。
- ツールバーのタイトル・タブ名など画面上の全テキストを一度は読みます（2 回目以降は重複排除されます）。
- 同一セッション内で同じ文字列が離れた位置に再登場した場合（例: 同じ見出しの繰り返し）は重複とみなしてスキップします。
- 横スクロール（ViewPager など）には対応していません。前方向スクロールのみです。
- 読み上げ中に別アプリへ切り替えた場合は、切り替え先の画面から続きを取得します。必要なら停止してください。
