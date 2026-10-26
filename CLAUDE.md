# Canvas (General-Dashboard): read this first

Personal Android app for Antonio ("Canvas"). Kotlin + Jetpack Compose + Material 3, minSdk 26,
no backend. Package `com.platinum20.dashboard`. Auto-updates from GitHub releases.

**Cost rule.** This file is the map of the repo. Read it, then open only the files the task
touches. Don't run wide `grep -r`, `find` or "read every screen" passes. Don't read
`CHANGELOG.md` beyond its top section, or big files (MainActivity 1.3k lines, NotesScreen 1.1k)
in full: grep for the symbol and read that range. Keep this file current when you move or
create something it lists, so the next thread doesn't have to explore.

## Code map (`app/src/main/java/com/platinum20/dashboard/`)

**App shell**
- `MainActivity.kt`: NavHost with all routes (`object Routes`), `CanvasNavBar` (floating
  `NavPill` + round "+"), predictive back, `OpenRequest` deep links (widget, notifications),
  PullToSearch wrapper, mini player overlay. Bar order: Home, Note, News, Media.
- `settings/AppSettings.kt`: every preference (StateFlow singleton), `HomeCard` order/hidden, theme.
- `ui/theme/Theme.kt`: themes CANVAS (default) and PURO (follows system: white mono / AMOLED),
  compact typography, Inter fonts in `res/font`.

**Shared UI (always reuse, never re-create)**
- `ui/screens/Common.kt`: `TopLevelBar(title, subtitle, onBack=)`, `CanvasCard`, `SectionHeader`,
  `Section`, `RowDivider`, `CanvasChip`, `RoundIconButton`, `IconTile`/`TileAccent`,
  `canvasFieldColors()`, `cardBorder()`, `Modifier.glass()/glassSource()`, `bouncyPress()`,
  `autoFocus()`, haptics (`rememberHaptic`, `hapticToggle`), `Springs`, `AutoRefresh`,
  `TabReselect`/`rememberReselectListState`, `PlusAction`/`ProvidePlusAction`, `relativeTime`,
  `weatherIcon`.
- `ui/screens/Motion.kt`: motion tokens (`Motion.*`, tab/push transitions, `Modifier.entrance()`
  cascade already built into CanvasCard + SectionHeader). Use these, not ad-hoc tweens.
- `ui/screens/GlassDialog.kt`: `CanvasDialog` (same params as AlertDialog) for every dialog;
  `Modifier.glassDialog()` for other dialog types.
- `ui/screens/LiquidGlass.kt` (API 33+ glass), Haze below. Glass only on overlays outside NavHost.
- `ui/screens/PullToSearch.kt` (pull down = "Cerca ovunque"), `SearchScreen.kt` + `search/EverywhereSearch.kt`.
- `ui/screens/Pickers.kt` (date/time/task edit dialogs), `SaveToNotesLayer.kt`/`SaveOnLongPress.kt`
  (long-press bubble: "Salva nelle note", "Aggiungi alla coda").

**Home ("Oggi")**: `HomeScreen.kt`, `HomeCards.kt`, `TomorrowCard.kt`, `WeekReviewCard.kt`,
`CustomizeScreen.kt`, `WeatherScreen.kt`, `RecapScreen.kt`. Logic in `today/`: `Briefing.kt`
(pure: the "Adesso" lines, also used by widget + morning notification), `DayTimeline.kt`
("La giornata" card), `TodayTasks`, `Tomorrow`, `WeekReview`, `Greeting`, `Invites`, `HomeResume` ("Riprendi" card, kept out of Adesso).
`weather/` (Open-Meteo, `WeatherStore`, `RainAlert`), `calendar/` (CalendarContract,
`QuickEvent`, `CalendarAsk`).

**Note**: data in `notes/NotesStore.kt` (one JSON file: tasks, notes, countdowns, habits,
expenses, parcels). `notes/QuickParse.kt` = Italian natural-language dates. Screens:
`NotesScreen.kt` (overview of 3 cards + detail pages), `NotesTimeline.kt` (the 3 kinds:
DATED / UNDATED / MONEY, grouping by day), `NotesExtras.kt`, `NotesSearchUi.kt`.
Reminders: `TaskReminderWorker`, `HabitReminderWorker`.

**Capture ("+")**: `capture/CaptureComposer.kt` (open state + text; the nav bar itself morphs into the input, `CanvasNavBar` BAR_TABS/VOICE/WRITE), `CaptureGuess.kt` (picks the
type from text: never ask the user the type), `CaptureSave.kt` (single save path + undo),
`Dictation.kt`/`VoiceCapture.kt` (in-app speech, no Google dialog), `SavedLink.kt`,
`QuickCaptureActivity.kt` (QS tile, share target). UI: `CaptureComposerLayer.kt` (`CaptureComposerInput` shown inside the bar, `CaptureComposerVeil`, `rememberComposerSend`),
`CaptureFeedbackLayer.kt` (dot flies to the target tab).

**News**: `news/` (`NewsRepository` free sources only, `NewsStore`, `NewsRecap` clustering,
`NewsHistory` "N nuove"/"Già viste", `RecapReader`), `news/reader/` (ArticleExtractor,
PageLoader, ArticleVerifier). `net/WebFetcher.kt` = hidden-WebView fetch fallback for 403 sites.
Screens: `NewsScreen`, `ReaderScreen`, `ArticleScreen`.

**Media** (Musica / Video / Radio e podcast)
- `media/Playback.kt`: the ONE player state (`Playing`, `PlaySource`), `Playback.starting(...)`
  every source must call, `next/previous/stop`. `media/PlayQueue.kt`: app-wide queue (pure
  `QueueOps` + StateFlow). `MediaLauncher.kt`: start any `ResumeItem`. `NowPlaying.kt`:
  web-player state + `pauseOthers`. `MediaHistory.kt` ("Continua"). `MediaPlaybackService.kt`.
  A rewrite into a single `CanvasPlayer` engine (ExoPlayer + queue, Spotify Web Playback SDK as
  external backend, one notification) was in progress on 2026-10-26: check main before touching.
- `media/youtube/` (YouTubeWeb/Api/Store, YtMusic, YtHistory, YtFavorites) + `player/`
  (NewPipeExtractor `YtResolver`, ExoPlayer `YtPlayer`, captions, service).
- `media/spotify/` (PKCE `SpotifyAuth`, `SpotifyApi`, `SpotifyStore`, `SpotifyPlayer` = hidden
  Web Playback SDK), `media/radio/`, `media/podcast/`, `media/songid/SongId.kt` (AudD).
- `media/MediaWebViews.kt`: any WebView in Compose must go through `WebView.inFrame(ctx)`.
- Screens: `MediaScreen`, `MusicScreen`, `ListenScreen`/`RadioScreen`/`PodcastScreen`,
  `YouTubeFeedScreen`, `YouTubeWatchScreen`, `VideoPlayerScreen`, `PlayerScreen`, `QueueScreen`,
  `FloatingPlayer` (mini player).

**Other**: `adblock/` (EasyList/uBO engine, `AdBlocker.attach()` on every WebView),
`autofill/` (notification/SMS extractor), `backup/`, `widget/` (DashboardWidget, TasksWidget),
`work/` (Morning, Refresh, RainAlert, UpdateCheck workers), `update/` (APK self-update),
`SettingsScreen.kt` (groups by intent), `UpdatesScreen.kt`, `LoginScreen.kt`.

**Tests** `app/src/test/java/com/platinum20/dashboard/`: JVM/Robolectric unit tests, one file per
feature (`*Test.kt`). Test class names must be unique across the package. `ScreenshotTour`,
`YouTubeTour`, `MediaClickTour`, `LiveChecks`, `YouTubeNativeCheck` run only with
`-Ptour=true` / `-Plive=true` (not in normal CI).

## Conventions

- UI text and code comments in Italian. Comments are short KDoc saying *why*, like the existing ones.
- State: `object XxxStore` singletons exposing StateFlow, prefs/JSON files; pure logic in
  separate functions/objects so it can be unit-tested. New logic ships with a test.
- Screens using `TopLevelBar` need `@OptIn(ExperimentalMaterial3Api::class)`.
- Never commit tokens or keys: the repo is public (AudD token lives only on the phone).

## Product rules (Antonio's, don't re-ask, don't undo)

- Everything native in-app: no external browser/app handoff, no service website shown even in
  a WebView. Extract data and render with Compose + our player. Spotify audio is DRM: only the
  official Web Playback SDK (he has Premium; App Remote rejected). Never bypass paywalls.
- One look everywhere: Canvas components above, no ElevatedCard, no FABs, no buttons in section
  headers (search = pull down, settings = drag "+" down). One colored "hero" block per page.
- Each piece of info lives in one place; no duplicates. Settings = set-once things only.
- "+" and voice never ask for the type. Note = 3 kinds only (Con scadenza, Senza scadenza,
  Spese); no tabs, no Idee.
- Nav pill animation must stay exactly as 1.140 (restored in 1.149): don't touch it.
- No serif fonts (tried and rejected). UI polish has priority over new features.
- Player: one source at a time, no sleep timer, no close button (gestures), mini player free
  2D drag without tilt.

## Build and release (CI only)

- The sandbox can't reach dl.google.com, so **no local Gradle build**. Verify by dispatching
  `.github/workflows/build.yml` on your branch (runs `testDebugUnitTest` then `assembleRelease`;
  a branch build never releases). About 4 minutes.
- Release = on main, `version.properties` has a version with no GitHub release yet. Bump BOTH
  `VERSION_NAME` (minor +1, e.g. 1.152.0 to 1.153.0) and `VERSION_CODE` (+1), and add a
  `## <version>` section at the top of `CHANGELOG.md` (bold title line + Italian bullets for
  Antonio; becomes the release notes). Docs-only changes need no bump.
- Standing approval: push, build, squash-merge to main and release without asking. Ship each fix
  as its own small release as soon as its build is green; no batching.
- **Parallel threads**: many threads release at once. Right before merging, `git fetch origin
  main`, merge it into your branch, and bump past the highest version among main, every open
  PR's `version.properties`, and active `claude/*` branches. Then merge immediately.
- **Reading CI errors**: log download URLs are blocked by the proxy. Use GitHub MCP
  `get_job_logs` with `tail_lines` ~600 (output goes to a file), then grep it for ` e: `
  (Kotlin compile) or `FAILED` (tests). Watch runs with public
  `curl https://api.github.com/repos/Platinum20/General-Dashboard/actions/runs?branch=<b>&per_page=3`.
- Visual check only for risky visual changes: dispatch `screenshots.yml` on the branch; PNGs and
  the test report land on branch `screenshots` under `<branch>/<run>/`. ScreenshotTour: never use
  `performScrollToNode` (hangs); scroll with `performScrollToIndex`.
- Other workflows (dispatch only): `verifica.yml` live checks, `ytcheck.yml` NewPipe extraction,
  `logincheck.yml` emulator WebView login shots.

## Sandbox gotchas

- `git push --delete` fails (proxy); merged branches just stay.
- Use `git fetch origin main && git merge --ff-only origin/main` rather than `git pull`/`reset --hard`.
- Maven Central gives 429: use repo1.maven.org. NewPipeExtractor must stay current (JitPack,
  `includeGroupByRegex("com\\.github\\.TeamNewPipe.*")`).
- Robolectric: guard GraphicsLayer use with `nativeCanvas.isHardwareAccelerated`; don't wait on
  `Playback.now` across tests (singleton bound to an earlier test's looper).
- Backlog lives in `BACKLOG.md` on the orphan branch `backlog`.
