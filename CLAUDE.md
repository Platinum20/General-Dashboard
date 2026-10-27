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
- `ui/screens/PullToSearch.kt` (pull down = "Cerca ovunque"; also on Media subpages, prefiltered via `SearchFilter.forRoute` + `Routes.search(filter)`), `SearchScreen.kt` + `search/EverywhereSearch.kt`. Media subpages have no search fields or header buttons.
- `ui/screens/SwipeAway.kt`: "drag sideways to close" (rule + `Modifier.swipeAway` + X), shared by mini player and save bubble.
- `ui/screens/Pickers.kt` (date/time/task edit dialogs), `SaveToNotesLayer.kt`/`SaveOnLongPress.kt`
  (long-press bubble: "Salva nelle note", "Aggiungi alla coda"; also holds the shared `BottomBubble`
  scaffold + `BubbleRow`). `ActionBubble.kt`: `ActionBubble.show(BubbleAction…)` = every menu/confirm
  pop-up (glass, bottom center, swipe to close). Use it instead of DropdownMenu or confirm dialogs.

**Home ("Oggi")**: `HomeScreen.kt`, `HomeCards.kt` (`NextThingsCard` "Prossimi giorni", `MoneyCard` "Spese" = timeline of the last 5 expenses, `NotesCard` (HomeCard.NOTES), all via the shared `TimelineCard` (whole card clickable, also used by the Note overview)),
`CustomizeScreen.kt`, `WeatherScreen.kt`, `RecapScreen.kt`, `ExpensesScreen.kt` (route
`home/expenses`, Spese moved here from Note in 1.186). Logic in `today/`: `Briefing.kt`
(pure: the "Adesso" lines, also used by widget + morning notification; the Home card uses `withTomorrow=false, wholeDay=true`
= every event/task of today, no budget line), `Upcoming.kt` (HomeCard.DAY "Prossimi giorni" card: next 8 items within 400 days (`Upcoming.DAYS`, also Notes' `EVENT_DAYS`) of
events/tasks/dates/parcels/charges, `Upcoming.MAX`=5 rows, drawn with the shared `ui/screens/Timeline.kt` `TimelineRow` (names left, line + day right, `Upcoming.gapDp` = log-scaled gap, `Upcoming.monthBreak` → `TimelineEntry.month` = `TimelineMonthMarker` 'NOVEMBRE' with haptic via `MonthCross`; Timeline.kt also has `ExpenseCategory.icon()/color()`); tomorrow's weather only in WeatherScreen), `Greeting`, `Invites`,
`HomeResume` ("Riprendi" card, kept out of Adesso).
`weather/` (Open-Meteo, `WeatherStore`, `RainAlert`), `calendar/` (CalendarContract,
`QuickEvent`, `CalendarAsk`, `Trips` = Gmail train events merged into one trip, shown as departure + arrival legs (applied in `CalendarRepository.eventsFrom`), ticket details + Google boilerplate stripped on the event page).

**Note**: data in `notes/NotesStore.kt` (one JSON file: tasks, notes, countdowns, habits,
expenses, parcels). `notes/QuickParse.kt` = Italian natural-language dates. Screens:
`NotesScreen.kt` (overview of 2 cards: DATED "Prossimi giorni" = everything with a day incl. calendar
events 60d (the Calendario page was folded in here in 1.187), UNDATED "Note"; `NotesNav.open(section)` opens one from
elsewhere), `NotesTimeline.kt` (pure `dueDays` = one group per day + Scaduti/Senza data, `timelineEntries` = Prossimi giorni drawn with the same `TimelineRow` as the Home card; `pastDays` = past 60d above it, page opens scrolled to now; `undatedEntries` = Note page as the same timeline by createdAt (all timelines: past on top, open on OGGI at the top of the screen with room below, `nowIndex` for Note/Spese); pages draw it on the background (no card), `TimelineRow(onLongClick=)` → `ActionBubble` actions per row; `TimelineNowMarker` + `PastBarrier`/`rememberPastBarrier` = 'OGGI' marker with scroll resistance and haptic; `expenseEntries` = Spese month list, drawn in `ExpensesTab` (NotesExtras.kt)),
`DayScrollbar.kt` (reusable time-proportional timeline + fast scroll with tick labels: `DayMark(at=epochDay)` + `DayScroll` pure logic; currently unused since 1.201, notes ordered by `Note.createdAt`), `NotesExtras.kt`, `NotesSearchUi.kt`.
Long notes: `NoteEditorScreen` (NotesScreen.kt) = sheet with only back + meta line on top, bottom bar (list toggle, `notes/NoteText.kt` word count, "Altro" → ActionBubble pin/share/delete).
Detail pages: `DetailPage.kt` = THE full-screen detail scaffold (back, Modifica, Elimina, `DetailTitle`/`DetailRow`), used by the event page and `ItemDetailScreen.kt` (route `notes/item/{kind}/{id}`, `Routes.noteItem`, `rememberOpenItem()`: tasks, dates, parcels, habits, expenses, fixed expenses all open here; edited inline since 1.219: `EditableDetailTitle`, `DetailTapRow` (pickers/ActionBubble), `DetailFieldRow` in DetailPage.kt, wiring in `lineEdit`). `CalendarScreen.kt` now only holds the event detail/edit screens, `NotesExtras.kt`, `NotesSearchUi.kt`.
Reminders: `TaskReminderWorker`, `HabitReminderWorker`.

**Capture ("+")**: `capture/CaptureComposer.kt` (open state + text; the nav bar itself morphs into the input, `CanvasNavBar` BAR_TABS/VOICE/WRITE), `CaptureGuess.kt` (picks the
type from text: never ask the user the type), `CaptureSave.kt` (single save path + undo), `PhotoAttach.kt` (paperclip at the left of the WRITE bar: any photo → on-device OCR → `decide`: receipt = expense text, date/parcel code = text in the bar, else note with the photo (`Note.photo` = file in filesDir/note-photos, shown by `NotePhoto` in NotesScreen); was the Spese "scontrino" card until 1.202),
`Dictation.kt`/`VoiceCapture.kt` (in-app speech, no Google dialog), `SavedLink.kt`,
`QuickCaptureActivity.kt` (QS tile, share target). UI: `CaptureComposerLayer.kt` (`CaptureComposerInput` shown inside the bar, `CaptureComposerVeil`, `rememberComposerSend`),
`CaptureFeedbackLayer.kt` (dot flies to the target tab).

**News**: `news/` (`NewsRepository` free sources only, `NewsStore`, `NewsRecap` clustering,
`NewsHistory` "N nuove"/"Già viste", `RecapReader` + `RecapSpeaker` = reading voice), `news/voice/`
(`VoiceStore` Piper voice choice/download (Giorgio = community voice repacked by `voicepack.yml` + `scripts/pack_piper_voice.py` into the prerelease `voci`), `NeuralVoice` sherpa-onnx + AudioTrack; system TTS fallback;
the sherpa AAR is downloaded by `app/build.gradle.kts` into ignored `app/libs/`; `net/HeavyDownloads.kt`
= "solo col Wi-Fi" gate for voices + update APKs; LiveChecks really downloads a voice), `news/reader/` (ArticleExtractor,
PageLoader, ArticleVerifier). `net/WebFetcher.kt` = hidden-WebView fetch fallback for 403 sites.
Screens: `NewsScreen`, `ReaderScreen`, `ArticleScreen`.

**Media** (Musica / Video / Radio e podcast)
- `media/CanvasPlayer.kt`: THE player (since 1.157). One ExoPlayer whose queue is the app queue
  (pure ops in `QueueOps.kt`, `EngineNow` state); play/playList/add/next/previous/jump/moveTo.
  Spotify plugs in as `ExternalBackend`. `PlayerService.kt` = the one MediaSession/notification
  (SimpleBasePlayer mirroring CanvasPlayer). `Playback.kt` = what screens show (`Playing`,
  `PlaySource`) + speech (RecapSpeaker). `MediaLauncher.kt`: start any `ResumeItem`.
  `MediaHistory.kt` ("Continua"/"Riprendi"). `YtPlayer`, `RadioStore`, `PodcastStore` are thin
  adapters over CanvasPlayer. `VideoPlayerScreen` (route media/video) is only the picker for phone
  videos/files/links: `MediaLauncher.playVideo` plays them on CanvasPlayer (ResumeKind.VIDEO).
- `media/youtube/` (YouTubeWeb/Api/Store, YtHistory, YtFavorites, `SponsorBlock` (hash-prefix fetch, pure `step`/`marks`, 200 ms loop started by `YtPlayer.load`, per-category `SbAction` in `AppSettings.sponsorActions`, Settings › Fa da solo; skip button + bar marks in YouTubeWatchScreen), `YtVotes` (Return YouTube Dislike, `AppSettings.dislikes`); YouTube Music removed from the app in 1.227, Antonio doesn't want it) + `player/`
  (NewPipeExtractor `YtResolver`, `YtPlayer` = YouTube sources/quality/subtitles on CanvasPlayer, captions).
- `media/spotify/` (PKCE `SpotifyAuth`, `SpotifyApi`, `SpotifyStore`, `SpotifyMixes` = our "Mix per te" (Spotify-made playlists are 403 for personal apps), `SpotifyPlayer` = own hidden
  WebView with the Web Playback SDK, started in MainActivity.onCreate, audio output only), `media/radio/`, `media/podcast/`, `media/songid/SongId.kt` (AudD).
- `media/MediaWebViews.kt`: any WebView in Compose must go through `WebView.inFrame(ctx)`.
- Screens: `MediaScreen`, `MusicScreen` (Spotify only, one page of horizontal `coverRow`s), `ListenScreen` (one page, no chips: `radioSections` in RadioScreen + `podcastSections`/`PodcastPage` in PodcastScreen),
  `YouTubeFeedScreen` (`ListTiles` row on top = Playlist/Mi piace/Più tardi (= YouTube videos in the notes, `SavedLinks.youtubeVideos`)/Telefono, then 4 equal-width `CanvasTabChip`s Home/Iscrizioni/Cronologia/Canali (Canali = `YouTubeStore.channels` ordered by pure `channelLines`, tap = `ChannelVideos` from the channel RSS); `AppSettings.autoplayNext` gates the suggested video after the queue ends (`CanvasPlayer.ended`); Escludi Shorts/live = `AppSettings.hideShorts/hideLive` in Settings › Fa da solo, applied by pure `pickFeed`), `YouTubeWatchScreen`, `VideoPlayerScreen`, `PlayerScreen`, `QueueScreen`,
  `FloatingPlayer` (mini player). `PlayerScreen` is the single player route: video layout
  (YouTubeWatchScreen) for YouTube, artwork layout otherwise. `ui/screens/Reorder.kt` = shared
  drag-to-reorder for LazyColumns (queue, Personalizza).

**Mail** (`mail/`): `GmailAuth` (gmail.readonly via AuthorizationClient, `needsConsent` = 7-day re-consent while
the Google app is in Testing), `MailParse` (pure: Gmail JSON, MIME/HTML to text, mail to `Incoming`), `MailStore`
(sync: new mails into `AutoFill.offer`, important unread for the Home card), `work/MailWorker` (hourly + on
Gmail notification; the listener skips Gmail previews when connected). UI `ui/screens/MailUi.kt` (`FromMailCard` = Home card "Dalla posta": only important unread mail (since 1.191 AutoFill always saves found items straight into Note, no "Aggiungi"; "La tua settimana" card removed),
GmailSettingsGroup inside AutoFillScreen, MailScreen route `home/mail/{id}`).

**Other**: `adblock/` (EasyList/uBO engine, `AdBlocker.attach()` on every WebView),
`autofill/` (notification/SMS extractor), `backup/`, `widget/` (DashboardWidget, TasksWidget),
`work/` (Morning, Refresh, RainAlert, UpdateCheck every 15 min + on open), `update/` (APK self-update),
`SettingsScreen.kt` (groups by intent; `RadioAlarmSetting.kt` = Sveglia con la radio in Cosa ti avvisa; `AccountConnect.kt` = official Spotify/Google connection from Account + `SpotifyAuthDialog`), `UpdatesScreen.kt`, `LoginScreen.kt`.

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
- "+" and voice never ask for the type. Note = 2 kinds only (Con scadenza, Senza scadenza);
  Spese is a Home card + page (1.186). No tabs, no Idee.
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
