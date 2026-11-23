# Canvas (General-Dashboard): read this first

Personal Android app for Antonio ("Canvas"). Kotlin + Jetpack Compose + Material 3, minSdk 26, compileSdk 36,
no backend. Package `com.platinum20.dashboard`. Auto-updates from GitHub releases.

**Cost rule.** This file is the map. Read it, then open only the files the task touches: grep for the symbol and
read that range (MainActivity, NotesScreen and other big files never in full). No wide `grep -r`/`find` passes.
Read only the top section of `CHANGELOG.md`. Keep this file SHORT and CURRENT: describe what the code is now,
no history ("since 1.x", "removed in"). Add a line only when you create or move something a future thread needs.

## Code map (`app/src/main/java/com/platinum20/dashboard/`)

**Shell**: `MainActivity.kt` = NavHost (`object Routes`), `CanvasNavBar` (floating `NavPill` Home/News/Media + round
"+" that morphs into the capture bar), predictive back, `OpenRequest` deep links, PullToSearch, mini player and glass
layers (queue, video settings, What's new) drawn OUTSIDE the NavHost. Calendario page = `Routes.NOTES`, pushed over Home.
`settings/AppSettings.kt` = every preference (StateFlow). `ui/theme/Theme.kt` = themes Canvas / Material You / Puro, Inter.

**Shared UI (`ui/screens/`, always reuse)**
- `Common.kt`: `TopLevelBar` (collapses with scroll), `CanvasCard`, `CardSection`/`CardTitle(opens=)` (Home card title
  INSIDE the card, "DA FARE ›"), `SectionHeader`, `OpenableHeader`, `RowDivider`, `CanvasChip`, `RoundIconButton`,
  `IconTile`, `CanvasButton` (`ButtonLevel`), `CanvasCheck` (CanvasCheck.kt = THE square tick, never circles), `bouncyPress`, haptics, `RollingText`, `LoadingPulse`/`Placeholder`
  (no spinners), `Clip.copy`, `relativeTime`, `rememberRowAtStart` (Media LazyRows), `PlusAction`.
- `KeyboardDismiss.kt`: root nestedScroll in MainActivity = a finger drag on any page hides the keyboard (code scrolls don't).
- `Motion.kt`: only `Motion.settle()`, `Motion.lively()`, `Motion.gesture()` + `Motion.fling`; `Modifier.entrance()`.
- `ActionBubble.kt` = every menu/confirm (glass bubble, bottom center). Glass = `Modifier.glass(shape, overScrim=)` (Common.kt, draws its own rim: no extra border) + numbers in `GlassStyle.kt` `Glass` (blur, tint, scrim, rim) also used by dialogs. `GlassDialog.kt` `CanvasDialog` = every dialog.
  `SaveToNotesLayer.kt` (`BottomBubble`, `BubbleRow`), `SwipeAway.kt`, `Pickers.kt`, `CanvasSearchField` (SearchField.kt).
- `RowActions.kt` `rememberRowActions()` = long-press bubble of calendar rows. RULE: anything with a page/menu of
  actions gets the same actions on long press.
- `Timeline.kt`: `TimelineRow` (line on the left, `RowDot`, `TripBlock` for timed events/trains/shifts), day/month
  markers, `TimelineNowMarker` + `PastBarrier`, `RowFlash`/`FlashNewRows`. `SharedItems.kt` = shared elements.
- `StatusLine.kt` `LinkStatus` (old data says so + "Rientra"/"Aggiorna"), `net/Online.kt` connectivity.
- `DetailPage.kt` = THE detail scaffold (back + meta line, bottom `primary` + "Altro"); `WhenHero.kt` first block.
- `HomeScreen.kt` `Urgent.color()` = the attention orange in every theme (don't use `colorScheme.tertiary` for it).

**Home** (`HomeScreen.kt`, `HomeCards.kt`, order in `HomeCard` / `CustomizeScreen.kt`): `InvitesRow` (orange line on
top, only when something needs doing), Adesso/weather (`WeatherBits.kt`, page `WeatherScreen.kt`, pure phrases `weather/WeatherText.kt`), Riprendi, `CalendarCard` (today + tomorrow,
count to month end), `LaterCard` (LaterUi.kt) = "Da fare" (to-dos, habits on top: `HabitTodoRow`, `habitTodoLine`
/`habitFlame` in BriefActions.kt; "Manca oggi" grey, orange from `HABIT_LATE_HOUR`) and "Appunti" (notes, photos,
saved media; archive via long press); full pages `LaterArchiveScreen(kind)`. News card, SIM (`ContiCard`),
Documenti, Dalla posta. Logic in `today/` (`Briefing`, `Upcoming`, `Invites`, `Greeting`, `HomeResume`).

**Calendario** (one calendar for everything): page `NotesScreen.kt`, pure `NotesTimeline.kt` (`dueDays`,
`pastDays`, `timelineEntries`, `looseItems`/`withoutLoose` = undated things go to Da fare/Appunti), data
`notes/NotesStore.kt` (one JSON: tasks, notes, habits, parcels). Dated tasks = events-like (`alert`, `place`,
`notes`, `allDay` dates; only undated tasks are tickable). `ItemDetailScreen.kt` (tasks, dates, parcels, habits),
event pages in `CalendarScreen.kt`. `calendar/` = CalendarContract, `Trips` (train mails → one trip event).
Reminders: `TaskReminderWorker`, `HabitReminderWorker`, `notes/ReminderText.kt`.

**Notes editor**: `NoteEditor.kt` + `RichNote.kt` (`RichNoteField`, `RichToolbar`), pure `notes/NoteLines.kt`
(text/bullet/number/check/heading per line, indent levels, renumber), `notes/NoteFormat.kt` (marks never shown,
`afterEdit` keeps them intact), `notes/NoteHistory.kt` (undo/redo), `NoteTable` (| a | b | rows), `NoteAudio` (audio: links), `NoteVersions` (older texts), `NotePdf`; `NoteDrawing.kt`, `NoteTableUi.kt` (`NoteTableBlock`: cells edited inline), `notes/NotePhotos.kt`. `notes/NoteLists.kt` = note checklists as rows in Home Da fare + Ricomincia/Duplica;
`notes/NotePhotoText.kt` = OCR text of note photos (own file, scanned from MainActivity) for search; `NotesSearch` gives the matching line.
`richText`: one ParagraphStyle (TextIndent = hanging indent) per line, the `\n` between them drawn as ZWSP (a `\n` inside
a paragraph = blank line). Page scroll while typing = `keepLineDelta` in NoteEditor (`keepInView`), not bringIntoView.

**Capture "+"** (`capture/`): `CaptureComposer` (bar morphs into input), `CaptureDestination` chips
Da fare/Appunti/Calendario/Abitudine (never ask the type), `CaptureGuess`, `CaptureSave` (+ undo), `CaptureWhen`,
`CaptureAsk` (questions answered locally), `PhotoAttach` (OCR), `VoiceCapture`/`Dictation`. UI `CaptureComposerLayer.kt`.

**News** (`news/`): free sources, `NewsStories` (pure grouping, unread), `NewsHistory` (last visit), reader
`news/reader/` on `ReaderScreen` (DetailPage), voice `news/voice/` (Piper via sherpa-onnx, AAR downloaded by gradle).

**Media**: `media/CanvasPlayer.kt` = THE player (one ExoPlayer, app queue, `QueueOps.kt` pure); `PlayerService.kt` =
the one MediaSession/notification; `Playback.kt` = what screens show. Spotify = `media/spotify/` (PKCE auth, Web
Playback SDK in a hidden WebView = audio only, `MadeStore` own mixes, playlists edit). YouTube = `media/youtube/`
(NewPipe `YtResolver`, `YtPlayer`, `YtSync` two-way with the web login, `SponsorBlock`, `YtOriginals` = always the
original-language titles). Also `media/radio/` (`RadioHero`), `media/podcast/` (`PodcastHero`), `media/lyrics/`,
`media/SavedDownloads.kt`. Radio page `ListenScreen` and `PodcastPage` use `MediaHero` + flat `mediaGroup` rows too.
Screens: `MediaScreen` (hero/queue, rows Musica `PerTeRow` + Video), `MediaPageKit.kt` (`MediaHero`, `mediaGroup`,
`mediaShelf`, `MediaRow`: pages on the background, no cards), `MediaMarks.kt` (`MediaTile`, `MediaLine`),
`PlayerScreen` (audio = tinted full page; YouTube = `YouTubeWatchScreen`), `QueueScreen` (popup), mini player
`FloatingPlayer.kt` + `MiniTuck.kt`/`MiniDock.kt` (box in 4 corners, edge tab, drag down to close; bars that stay
put mark `Modifier.miniPlayerFloor()`). Any WebView goes through `MediaWebViews.inFrame`.

**Accounts / data from websites**: `net/WebSession.kt` (sealed cookies) + `work/KeepAliveWorker`. `sim/` WindTre
(SIM card + `SimScreen`), `shifts/` Creators Pro shifts + pay (`ShiftParse`, `ShiftPay`, `CreatorsLink`), `mail/`
Gmail read-only (IMAP app password preferred), `tickets/` QR from ticket mails, `trips/` live train/shift
notification (`TrainLive` ViaggiaTreno, `LiveTrip`, `ShiftLive`, RemoteViews in `work/TripLiveWorker`), `docs/`
documents scan. `security/Vault.kt` = Keystore for tokens.

**Widgets** (`widget/`): `WidgetKit.kt` `CanvasWidget` + `Widgets.refresh` (call when data changes), deep links
`Widgets.open` → `OpenRequest`. `DashboardWidget` "Oggi" (weather + hours + one "PROSSIMO" line, `AgendaRows.next`),
`TodoWidget` "Da fare" (habits + to-dos, tick in place, pure `TodoWidgetRows`), `SimWidget` (ring), `TasksWidget`
"Calendario" (`AgendaRows.build`), `NoteWidget` "Nota" (chosen note via "Mostra nel widget", ticks, `NoteWidgetRows`), `MoreWidgets.kt` News/Meteo/Scrivi. Never rename widget classes (placed ones vanish).

**Other**: `search/` (Cerca ovunque),
`work/` (Morning, Refresh, RainAlert, UpdateCheck), `update/` (self-update, `UpdateTrust`, `WhatsNew` from
CHANGELOG), `SettingsScreen.kt` (`SettingsPageScreen(SettingsPage)`: main page = theme + menu rows Le sezioni / L'app),
`AccountsUi.kt` + pure `AccountTiles.kt` (Account page: one row per account, bubble actions; rows live in SimUi/ShiftsUi/MailUi),
`UpdatesScreen.kt`, `adblock/`, `backup/`.

**Tests** `app/src/test/java/com/platinum20/dashboard/`: JVM/Robolectric, one `*Test.kt` per feature, class names
unique. Tours (`ScreenshotTour`, `YouTubeTour`, …) and `LiveChecks` run only with `-Ptour=true` / `-Plive=true`.

## Product rules (Antonio's: don't re-ask, don't undo)

- Everything native in-app: no external browser/app handoff, no service website shown in a WebView. Spotify audio
  only via the official Web Playback SDK. Never bypass paywalls.
- One look: Canvas components, no ElevatedCard, no FABs, no buttons in section headers (search = pull down,
  settings = drag "+" down). One colored hero per page. No serif fonts. UI polish before new features.
- The Canvas row: left = time or icon, middle = name + at most one line, right = value or action.
- One color, one meaning: blue = now + actions, orange = attention, red = delete, rest ink.
- Every page answers one question. Each piece of info lives in one place. Settings = set-once things only.
  Old data says so, with refresh/re-login next to it. Subtitles fit on ONE line.
- Long press on anything with options opens the bubble with those options. Every bubble row has a one-line subtitle.
- "+" and voice never ask for the type. NO money/expense tracking anywhere (shift pay stays).
- Month views show only the current month. Player: one source at a time, no sleep timer, no close button.
- Nav pill animation stays exactly as it is. Only the three springs.
- Rejected, don't re-add: calendar fast scroll / day scrubber, hold+drag on the Calendar card, filter chips,
  stories row in Media, auto diary ("La linea che ricorda"), YouTube Music, One UI ProgressStyle notification.
- Mockups for him: PRIMA vs DOPO side by side, rows stacked vertically, readable on a phone, in
  `/mnt/project-files/<topic>/`.

## Conventions

UI text and comments in Italian, short KDoc saying why. Stores = `object XxxStore` with StateFlow; pure logic in
separate functions so it can be unit-tested; new logic ships with a test. `TopLevelBar` screens need
`@OptIn(ExperimentalMaterial3Api::class)`. Never commit tokens or keys (public repo).

## Build and release (CI only)

- No local Gradle (sandbox can't reach dl.google.com). Verify by dispatching `.github/workflows/build.yml` on your
  branch (unit tests + assembleRelease, ~4 min; branch builds never release).
- Release = on main, `version.properties` with a version that has no GitHub release yet: bump `VERSION_NAME`
  (minor +1) and `VERSION_CODE` (+1), add `## <version>` at the top of `CHANGELOG.md` (bold title + Italian bullets
  for Antonio = release notes). Docs-only changes need no bump.
- Standing approval: push, build, squash-merge to main and release without asking, one small release per fix.
  Exception: while a design is still being iterated, don't publish until he says it's final.
- Parallel threads: right before merging, `git fetch origin main`, merge it, bump past the highest version among
  main, open PRs and active `claude/*` branches, then merge immediately.
- CI errors: GitHub MCP `get_job_logs` with `tail_lines` ~600, grep ` e: ` (Kotlin) or `FAILED` (tests). Runs:
  `curl https://api.github.com/repos/Platinum20/General-Dashboard/actions/runs?branch=<b>&per_page=3`.
- Visual check for risky changes: dispatch `screenshots.yml`; PNGs land on branch `screenshots` under
  `<branch>/<run>/`. In tours never use `performScrollToNode` (hangs), use `performScrollToIndex`.
- Other dispatch-only workflows: `verifica.yml`, `ytcheck.yml`, `logincheck.yml`.
- Notes editor on a real emulator + real keyboard: `notecheck.yml` (androidTest `NoteEditorDeviceCheck`, type with `input text`,
  never `performTextInput`); log + screenshots on branch `screenshots` under `note-check/<run>/`. If every test times out on
  `imeOpen`, the emulator keyboard didn't start: rerun.

## Sandbox gotchas

- `git push --delete` fails (proxy). Use `git fetch origin main && git merge --ff-only origin/main`.
- Maven Central 429 → repo1.maven.org. NewPipeExtractor (JitPack) pinned to the commit the NewPipe app uses:
  follow NewPipe's `gradle/libs.versions.toml`, never an older tag.
- Robolectric: guard GraphicsLayer with `nativeCanvas.isHardwareAccelerated`; don't wait on `Playback.now` across tests.
- Old backlog/UX notes live on the orphan branch `backlog` (mostly stale).
