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
  PullToSearch wrapper, mini player overlay. Bar order: Home, News, Media (Note tab removed in 1.314:
  the calendar route `Routes.NOTES` is pushed over Home via `NavController.openCalendar()`, `barTab` → HOME).
- `settings/AppSettings.kt`: every preference (StateFlow singleton), `HomeCard` order/hidden, theme.
- `ui/theme/Theme.kt`: themes CANVAS (default) and PURO (follows system: white mono / AMOLED),
  compact typography, Inter fonts in `res/font`.

**Shared UI (always reuse, never re-create)**
- `ui/screens/Common.kt`: `TopLevelBar(title, subtitle, onBack=)`, `CanvasCard`, `SectionHeader`,
  `Section`, `RowDivider`, `CanvasChip`, `RoundIconButton`, `IconTile`/`TileAccent`,
  `canvasFieldColors()`, `cardBorder()`, `Modifier.glass()/glassSource()`, `bouncyPress()`,
  `autoFocus()`, `RollingText` (numbers that change), `LoadingPulse` (no spinners anywhere since 1.357), `CanvasButton` (the one button), `OpenableHeader` (primary label + chevron = tappable title that opens a page; plain `SectionHeader` = not tappable), haptics (`rememberHaptic`, `hapticToggle`), `Springs`, `AutoRefresh`,
  `TabReselect`/`rememberReselectListState`, `PlusAction`/`ProvidePlusAction`, `relativeTime`,
  `weatherIcon`.
- `ui/screens/Motion.kt`: motion tokens (`Motion.*`, tab/push transitions, `Modifier.entrance()`
  cascade already built into CanvasCard + SectionHeader). Use these, not ad-hoc tweens. Since 1.347 only three springs:
  `Motion.settle()` (no bounce), `Motion.lively()` (press/appear), `Motion.gesture()` (finger release) + `Motion.fling(dist, v)`
  for swipe-outs at the finger's speed. `bouncyPress` (Common.kt, pure `PressRule`) presses after 80 ms still, innermost only.
- `ui/screens/SharedItems.kt`: shared elements (`Modifier.sharedItem(key)`, `LocalSharedScope` = SharedTransitionLayout around
  the NavHost, `LocalRouteScope` provided only in HOME/NOTES/NOTE_ITEM): row title → ItemDetail title, Calendar card → page.
  `RowActions.kt` = `rememberRowActions()` long-press bubble for any calendar row (Home card + page). `Timeline.kt` `RowFlash`
  (row lights up after tick/save; tick waits `TICK_MS` in place; `FlashNewRows`; the + dot lands on a fresh row), `softStopAtNow`.
  `GestureHints.kt` = one-time gesture hints. `Placeholder`/`PlaceholderList` (Common.kt) instead of spinners.
- `ui/screens/GlassDialog.kt`: `CanvasDialog` (same params as AlertDialog) for every dialog;
  `Modifier.glassDialog()` for other dialog types.
- `ui/screens/LiquidGlass.kt` (API 33+ glass), Haze below. Glass only on overlays outside NavHost.
- `ui/screens/PullToSearch.kt` (pull down = "Cerca ovunque"; also on Media subpages, prefiltered via `SearchFilter.forRoute` + `Routes.search(filter)`), `SearchScreen.kt` + `search/EverywhereSearch.kt`. Media subpages have no search fields or header buttons.
- `ui/screens/SwipeAway.kt`: "drag sideways to close" (rule + `Modifier.swipeAway` + X), shared by mini player and save bubble.
- `ui/screens/Pickers.kt` (date/time/task edit dialogs), `SaveToNotesLayer.kt`/`SaveOnLongPress.kt`
  (long-press bubble: "Salva nelle note", "Aggiungi alla coda"; also holds the shared `BottomBubble`
  scaffold + `BubbleRow`). `ActionBubble.kt`: `ActionBubble.show(BubbleAction…)` = every menu/confirm
  pop-up (glass, bottom center, swipe to close). Use it instead of DropdownMenu or confirm dialogs.

**Calendario unico (1.314, Antonio)**: Note and Spese are no longer pages/cards; ONE calendar holds everything.
Home card `CalendarCard` (HomeCard.DAY "Calendario", HomeCards.kt, via shared `TimelineCard`) = current month marker with
month spend, then ALL of today, then `Upcoming.MAX` next rows; full page = `NotesScreen` (route `notes`, title "Calendario").
Both use `dueDays(withToday = true, notes, expenses)` (today group always first: "Spesa di oggi" row (`DueRow.spend`, 0 too),
today's expenses, overdue items (`overdueFrom`), today's items, undated tasks, notes pinned-first) + `pastDays(expenses=)` +
`timelineEntries(months = true, expenses)` (every month marker gets `monthSpend`/`monthOf`; tap → `Routes.expenses(month)` =
ExpensesScreen month overview). Row taps: `rememberDueOpen()` (HomeCards.kt). Habits = one "Abitudini · N su M" row inside OGGI (`habitsIndex`, 1.357), no Completati section (long press OGGI = Elimina i completati); Home card = today + rest of week (min `Upcoming.MAX`) + "Altre N a <mese>" (`calendarCardCut`). Home cards Adesso/SIM/Revolut/Documenti grow into their page via `sharedItem` (`SharedKeys`). Full page has fixed `CalendarFilter` chips (Spese/Note/Eventi, pure `DueRow.filter()` + `List<DueDay>.only()`); long press on the Home card opens the full page; done tasks sit on the day they were checked (today's under OGGI). Adesso keeps only RAIN/WEATHER/HABIT lines.
**Home ("Oggi")**: `HomeScreen.kt`, `HomeCards.kt` (`CalendarCard`, `TimelineCard`),
`CustomizeScreen.kt`, `WeatherScreen.kt`, `RecapScreen.kt`, `ExpensesScreen.kt` (route
`home/expenses?month=`, `Routes.expenses(YearMonth?)`: month overview opened from a calendar month marker). Logic in `today/`: `Briefing.kt`
(pure: the "Adesso" lines, also used by widget + morning notification; the Home card uses `withTomorrow=false, wholeDay=true`
= every event/task of today, no budget line), `Upcoming.kt` (HomeCard.DAY "Prossimi giorni" card: next 8 items within 400 days (`Upcoming.DAYS`, also Notes' `EVENT_DAYS`) of
events/tasks/dates/parcels/charges, `Upcoming.MAX`=5 rows, drawn with the shared `ui/screens/Timeline.kt` `TimelineRow` (names left, line + day right, `Upcoming.gapDp` = log-scaled gap, `Upcoming.monthBreak` → `TimelineEntry.month` = `TimelineMonthMarker` 'NOVEMBRE' with haptic via `MonthCross`; Timeline.kt also has `ExpenseCategory.icon()/color()`); tomorrow's weather only in WeatherScreen), `Greeting`, `Invites`,
`HomeResume` ("Riprendi" card, kept out of Adesso).
`weather/` (Open-Meteo, `WeatherStore`, `RainAlert`), `calendar/` (CalendarContract,
`QuickEvent`, `CalendarAsk`, `Trips` = Gmail train events merged into one trip, shown as departure + arrival legs (applied in `CalendarRepository.eventsFrom`), ticket details + Google boilerplate stripped on the event page).

**Note**: data in `notes/NotesStore.kt` (one JSON file: tasks, notes, habits,
expenses, parcels). `notes/QuickParse.kt` = Italian natural-language dates. Screens:
`NotesScreen.kt` (= the Calendario page since 1.314, see above; no overview/sections any more), `NotesTimeline.kt` (pure `dueDays` = one group per day + Scaduti/Senza data, `timelineEntries` = Prossimi giorni drawn with the same `TimelineRow` as the Home card; `pastDays` = past 60d above it, page opens scrolled to now; `undatedEntries` = Note page as the same timeline by createdAt (all timelines: past on top, open on OGGI at the top of the screen with room below, `nowIndex` for Note/Spese); pages draw it on the background (no card), `TimelineRow(onLongClick=)` → `ActionBubble` actions per row; `TimelineNowMarker` + `PastBarrier`/`rememberPastBarrier` = 'OGGI' marker with scroll resistance and haptic; `expenseEntries` = Spese month list, drawn in `ExpensesTab` (NotesExtras.kt)),
`DayScrollbar.kt` (reusable time-proportional timeline + fast scroll with tick labels: `DayMark(at=epochDay)` + `DayScroll` pure logic; currently unused since 1.201, notes ordered by `Note.createdAt`), `NotesExtras.kt`, `NotesSearchUi.kt`.
Long notes: `NoteEditorScreen` (NotesScreen.kt) = sheet with only back + meta line on top, bottom bar (list toggle, `notes/NoteText.kt` word count, "Altro" → ActionBubble pin/share/delete).
Detail pages: `DetailPage.kt` = THE full-screen detail scaffold, shaped like the note editor since 1.315 (top: back + `meta` line; bottom: left `primary: DetailAction` (or Modifica), right "Altro" → ActionBubble with `more` + Modifica + Elimina; `DetailTitle`/`DetailRow`), used by the event page and `ItemDetailScreen.kt` (route `notes/item/{kind}/{id}`, `Routes.noteItem`, `rememberOpenItem()`: tasks, dates, parcels, habits, expenses, fixed expenses all open here; edited inline since 1.219: `EditableDetailTitle`, `DetailTapRow` (pickers/ActionBubble), `DetailFieldRow` in DetailPage.kt, wiring in `lineEdit`). `CalendarScreen.kt` now only holds the event detail/edit screens, `NotesExtras.kt`, `NotesSearchUi.kt`.
Reminders: `TaskReminderWorker`, `HabitReminderWorker`.
**Promemoria = date (1.361, Antonio)**: no `Countdown` type any more. A "data da ricordare" is a `TaskItem` with `allDay`
(dueAt = midnight; `TaskItem.date(...)`, `NotesStore.saveDate`; old `countdowns` JSON migrated in `parse`). Every task has the
event's fields: `alert` (`Alert` ON_TIME/DAY_BEFORE/NONE, `alertAt()`; all-day at 9:00), `notes`, `place`, `firstYear` (age).
All-day items never go late (`isLate`), repeating ones roll by themselves (`nextDay`, `withDatesRolled` at start).
`openTasks` = to-dos only (widgets, counters), `openItems` = with dates (Briefing). Shared wording for event + task pages in
`notes/WhenText.kt`; task page rows `taskLines` (ItemDetailScreen) in the same order as the event page: Quando, Tutto il giorno,
Si ripete, Avviso, Luogo, Note. Dated tasks sit under the Eventi calendar chip.

**Capture ("+")**: `capture/CaptureComposer.kt` (open state + text; the nav bar itself morphs into the input, `CanvasNavBar` BAR_TABS/VOICE/WRITE), `CaptureGuess.kt` (picks the
type from text: never ask the user the type), `CaptureSave.kt` (single save path + undo), `PhotoAttach.kt` (paperclip at the left of the WRITE bar: any photo → on-device OCR → `decide`: receipt = expense text, date/parcel code = text in the bar, else note with the photo (`Note.photo` = file in filesDir/note-photos, shown by `NotePhoto` in NotesScreen); was the Spese "scontrino" card until 1.202),
`Dictation.kt`/`VoiceCapture.kt` (in-app speech, no Google dialog), `SavedLink.kt`,
`QuickCaptureActivity.kt` (QS tile, share target). UI: `CaptureComposerLayer.kt` (`CaptureComposerInput` shown inside the bar, `CaptureComposerVeil`, `rememberComposerSend`),
`CaptureFeedbackLayer.kt` (dot flies to the target tab).

**News** (1.356, by story): `news/` (`NewsRepository` free sources only, `NewsStore` (+ `verifyLatest`, `widgetStories`), `NewsRecap` clustering (`cluster` = all stories), `NewsStories` (pure: `group` readable/hidden sources → stories (`NewsStory` = `RecapTheme`), `split` → `NewsFeed` fresh/seen around last visit, `next`, `homeLine`/`markerLabel`), `ReadingSpots` (reader resume),
`NewsHistory` (last visit flow, session `enter`/`holdSession` so returning from the reader keeps the same visit, `settled`), `RecapReader` + `RecapSpeaker` = reading voice), `news/voice/`
(`VoiceStore` Piper voice choice/download (Giorgio = community voice repacked by `voicepack.yml` + `scripts/pack_piper_voice.py` into the prerelease `voci`), `NeuralVoice` sherpa-onnx + AudioTrack; system TTS fallback;
the sherpa AAR is downloaded by `app/build.gradle.kts` into ignored `app/libs/`; `net/HeavyDownloads.kt`
= "solo col Wi-Fi" gate for voices + update APKs; LiveChecks really downloads a voice), `news/reader/` (ArticleExtractor,
PageLoader, ArticleVerifier). `net/WebFetcher.kt` = hidden-WebView fetch fallback for 403 sites.
Screens: `NewsScreen` (Ascolta hero, stories, `NewsVisitMarker` 'ULTIMA VISITA' with mirrored `PastBarrier`, swipe = read, new-arrivals bubble; no source chips: Impostazioni › News › Fonti = `AppSettings.hiddenNewsSources`), shared rows in `NewsStoryUi.kt` (`StoryLead`, `StoryRow`, `storyActions`, `newsSharedKey`), Home card `NewsCard` in HomeScreen (HomeCard.RECAP, label "News"), `ReaderScreen` (on `DetailPage` with `scroll`/`topExtra`; article videos → `news/reader/ArticleVideos` → our player, no WebView; no "pagina originale"), `ArticleScreen` = generic in-app web page for `InAppNav.openWeb` only (parcels etc.). RecapScreen removed in 1.356.

**Media** (Musica / Video / Radio e podcast)
- `media/CanvasPlayer.kt`: THE player (since 1.157). One ExoPlayer whose queue is the app queue
  (pure ops in `QueueOps.kt`: tap = `pick` (plays it, others stay), swipe-remove + `restore` undo; `QueueFill` = "Riempi in automatico" Spotify top-up toggle at the queue bottom; `EngineNow` state); play/playList/add/next/previous/jump/moveTo.
  Spotify plugs in as `ExternalBackend`. `PlayerService.kt` = the one MediaSession/notification
  (SimpleBasePlayer mirroring CanvasPlayer). `Playback.kt` = what screens show (`Playing`,
  `PlaySource`) + speech (RecapSpeaker). `MediaLauncher.kt`: start any `ResumeItem`.
  `MediaHistory.kt` ("Continua"/"Riprendi"). `YtPlayer`, `RadioStore`, `PodcastStore` are thin
  adapters over CanvasPlayer. `VideoPlayerScreen` (route media/video) is only the picker for phone
  videos/files/links: `MediaLauncher.playVideo` plays them on CanvasPlayer (ResumeKind.VIDEO).
- `media/youtube/` (YouTubeWeb/Api/Store, YtHistory, YtFavorites, `SponsorBlock` (hash-prefix fetch, pure `step`/`marks`, 200 ms loop started by `YtPlayer.load`, per-category `SbAction` in `AppSettings.sponsorActions`, Settings › Fa da solo; skip button + bar marks in YouTubeWatchScreen), `YtVotes` (Return YouTube Dislike, `AppSettings.dislikes`); `YtRatings` (1.297) = real Non mi piace toggle + subscribe/unsubscribe button in the watch page, pushed by `YtSync.dislike`/`subscribe`; `YtPremiere` (anteprime: `YtPlayer.load` failure → `check` reads the start time from the watch page → `YtNow.upcoming` → `PremiereWait.kt` countdown + "Avvisami" (`PremiereReminderWorker`) + auto retry; feed badge `badge(YtVideo.startsAt)`); YouTube Music removed from the app in 1.227, Antonio doesn't want it); `YtCommentActions` = like + reply + new comment from the watch page comments, same login/`withAccount` path (re-reads the comment's page via `YtComment.source`, runs YouTube's own comment commands); `YtSync` (1.287) = two-way sync with his real YouTube via the in-app web login (official API can't do WL/history): WL ↔ YouTube videos in Note, likes ↔ `YtFavorites`, history → `YtHistory.mergeRemote` + half-watched into "Continua" (`continueItems` → `MediaHistory.merge`, 1.292), Canvas watches → YouTube history (stats ping); pure `SyncPlan` three-way merge; writes go through `YouTubeWeb.withAccount` (hidden page, `__cvApi` InnerTube calls with SAPISIDHASH); status line in Settings › Account + `player/`
  (NewPipeExtractor `YtResolver`, `YtPlayer` = YouTube sources/quality/subtitles on CanvasPlayer, captions).
- `media/spotify/` (PKCE `SpotifyAuth`, `SpotifyApi`, `SpotifyStore`, `SpotifyMixes` = our "Mix per te" (Spotify-made playlists are 403 for personal apps), `SpotifyPlayer` = own hidden
  WebView with the Web Playback SDK, started in MainActivity.onCreate, audio output only), `media/radio/`, `media/podcast/`, `media/songid/SongId.kt` (AudD).
- `media/MediaWebViews.kt`: any WebView in Compose must go through `WebView.inFrame(ctx)`.
- Screens: `MediaScreen` (1.357: hero + queue peek + Continua + three shelves from `MediaShelves.kt`: Musica/Video/Radio e podcast, title opens the full page; no tiles), `MusicScreen` (Spotify only, one page of horizontal `coverRow`s), `ListenScreen` (one page, no chips: `radioSections` in RadioScreen + `podcastSections`/`PodcastPage` in PodcastScreen),
  `YouTubeFeedScreen` (no tiles since 1.303: only 5 equal-width `CanvasTabChip`s Home/Iscrizioni/Cronologia/Canali/Playlist; Playlist tab = fixed rows Mi piace + Sul telefono (Routes.VIDEO) then YouTube playlists; Più tardi removed (already in Note) (Canali = `YouTubeStore.channels` ordered by pure `channelLines`, tap = route `media/channel/{id}` (since 1.357 playlist `media/playlist/{id}` and Mi piace `media/liked` are real routes too, no in-page state navigation) = ALL the channel's videos newest first, paged by `YtResolver.channelVideos` (NewPipe Videos tab, RSS fallback) + `YouTubeStore.loadMoreChannel` at the list bottom; same page from the watch page channel row = route `media/channel/{id}` `YouTubeChannelScreen`); `AppSettings.autoplayNext` gates the suggested video after the queue ends (`CanvasPlayer.ended`); Escludi Shorts/live = `AppSettings.hideShorts/hideLive` in Settings › Fa da solo, applied by pure `pickFeed`), `YouTubeWatchScreen` (gestures: `SeekBar.kt` = time bar of both players, no seek on tap, only sideways drag, `SeekStack` = repeated double tap +10/+20…; pure `VideoSwipe.kt` = swipe up to fullscreen, fullscreen volume drag + keys, pinch fill/fit), `VideoPlayerScreen`, `PlayerScreen`, `QueueScreen`,
  `FloatingPlayer` (mini player; `MiniDock.kt` = closing the video page flies the video onto the mini player's real thumbnail, `MiniDock.docking` + `holdExit` on the player routes). `PlayerScreen` is the single player route: video layout
  (YouTubeWatchScreen) for YouTube, artwork layout otherwise. Its queue opens as `QueuePopup` (QueueScreen.kt, 1.290: overlay, side panel in landscape so fullscreen video stays); since 1.357 the same popup opens from Media's QueuePeek (no queue page), "Svuota" = long press on "Dopo". Video settings menu = ActionBubble (`showVideoMenu`); loading shows the thumbnail poster. `ui/screens/Reorder.kt` = shared
  drag-to-reorder for LazyColumns (queue, Personalizza).

**Mail** (`mail/`): no inbox UI, only extraction. Two read-only paths, same ids (hex X-GM-MSGID = API id): `GmailImap` (1.323, preferred: Google app password, IMAP over SSL, EXAMINE + `X-GM-RAW` with the same queries, password encrypted with a Keystore key, `rejected` stops retries; pure `ImapParse` + `MimeParse` = raw RFC 822 to text) and `GmailAuth` (gmail.readonly via AuthorizationClient, `needsConsent` = 7-day re-consent while
the Google app is in Testing); `MailStore.sync` picks IMAP first (`Source` interface), `MailStore.connected()`; `MailParse` (pure: Gmail JSON, MIME/HTML to text, mail to `Incoming`), `MailStore`
(sync: new mails into `AutoFill.offer`, important unread for the Home card), `work/MailWorker` (hourly + on
Gmail notification; the listener skips Gmail previews when connected). UI `ui/screens/MailUi.kt` (`FromMailCard` = Home card "Dalla posta": only important unread mail (since 1.191 AutoFill always saves found items straight into Note, no "Aggiungi"; "La tua settimana" card removed),
`GmailAccountRows` in Settings › Account "Posta" (1.328, like SIM/Conto; password dialog `GmailPasswordDialog`), MailScreen route `home/mail/{id}`).

**SIM** (`sim/`, 1.288): `WindTreParse` (pure: any JSON/page text from WindTre area clienti → `SimStatus` giga/minuti/SMS/credito/rinnovo by key names, format not public), `SimStore` (status, linked, last 40 captured responses in filesDir, login responses never saved), `WindTreLink` (JS hook on fetch/XHR via document-start script on `*.windtre.it`, one-time official login WebView, hidden-WebView refresh hourly from the Home card). UI `ui/screens/SimUi.kt` (`SimCard` = HomeCard.SIM, `SimScreen` route `home/sim`, "Condividi i dati letti" to fix the parser from real samples).

**Revolut** (`bank/`, 1.320): `RevolutParse` (pure: any JSON from app.revolut.com → `BankTx` card payments by shape id/amount/startedDate, EUR pocket balance only with explicit `type` CURRENT (money-boxes pockets have no type = savings), amount = `amountWithCharges`, merchant `mcc` → `mccCategory` when the tag is generic; transfers between own pockets, exchanges, top-ups skipped; declined/reverted → `counts=false`), `RevolutStore` (linked/balance/expired, last 30 responses), `RevolutSession` = `net/WebSession.kt` (shared with `sim/WindTreSession`, 1.362: session cookies are memory-only in WebView and sessionStorage lives only in one WebView, so each hidden refresh started logged out; both copied sealed with `Vault` after each good read, cookies put back in `prepare`, sessionStorage put back by a document-start script), `RevolutPockets` (pure: wallet CURRENT/SAVINGS EUR pockets + money-boxes names → `RevolutStore.pockets`, Home card `RevolutCard` = HomeCard.BANK: total + one row per pocket, tap → Spese), `RevolutLink` (same JS hook as WindTre on `*.revolut.com` + `PULL` = GET wallet + `transactions/last?count=300` replaying the page's own API headers; hidden refresh every 14 min (`STALE_MS`) from Home/ExpensesScreen and in background by `work/KeepAliveWorker` (Revolut + WindTre: 15 min periodic + `KeepAliveAlarm` setAndAllowWhileIdle → expedited run so Doze doesn't skip the night, `update()` from both stores' `setLinked` + app start, one-time battery-optimization ask `askBattery`); 401 or back on /start = expired). Movements → `NotesStore.syncBank` → pure `notes/BankSync.kt` (`Expense.bankId`, notification twin merged, deleted ones in `NotesData.bankIgnored`, whole history (`RevolutStore.SINCE`): first pull pages back with `transactions/last?count=300&to=` until empty, then `historyDone`; Revolut `category` > `tag` > merchant via `revolutCategory`, fills only OTHER on existing ones). UI `ui/screens/RevolutUi.kt` (rows in Settings › Account "Conto", login route `settings/revolut`, balance = ExpensesScreen subtitle).

**Documenti** (`docs/`, 1.321): `DocScan.kt` (`DocReader.read` pure: OCR rows → `DocScan` kind/side/number/expiry by printed words, MRZ/TEAM/licence categories = back, expiry = latest date in window), `DocumentsStore` (documents.json + filesDir/documents photos straightened by EXIF, `waitingBack` = photo within 15 min after a front is its back, expiry → `NotesStore.saveDate` "Scade: …" via `countdownId`, `pdf` = cropped front+back on one A4 at real card size (pure `pdfSize`), shared via FileProvider or `savePdf` → MediaStore Downloads; both in "Altro"). `DocCrop.kt` (1.358): `keep(kind)` straightens by OCR line angle (pure `textAngle`) and crops to the card border (pure `edges`, projection profiles + card aspect), whole photo kept as `frontFull`/`backFull` ("Foto intera"); long press a side = Ruota / Foto intera; pre-1.358 docs (`straight=false`) are straightened on first open. Paperclip: `PhotoAttach.decide(waitingBack=)` → `Found.Document`/`Found.Back`, handled in CaptureComposerLayer. UI `ui/screens/DocumentsUi.kt` (`DocumentsCard` = HomeCard.DOCS, hidden when empty; `DocumentScreen` route `home/document/{id}` on DetailPage).

**Other**: `security/Vault.kt` (Keystore AES-GCM for tokens in prefs: `Vault.read/put`, legacy plaintext migrated on read; used by Spotify tokens + AudD token; Gmail password has its own key in GmailImap), `adblock/` (EasyList/uBO engine, `AdBlocker.attach()` on every WebView),
`autofill/` (notification/SMS extractor), `backup/`, `widget/` (DashboardWidget, TasksWidget),
`work/` (Morning, Refresh, RainAlert, UpdateCheck every 15 min + on open), `update/` (APK self-update; `UpdateTrust` = download only from this repo's release URLs + reject an APK with another package/signing cert before install; `WhatsNew` + `ui/screens/WhatsNewLayer.kt` = one-time glass bubble with this version's CHANGELOG section, copied into `BuildConfig.WHATS_NEW` by `app/build.gradle.kts`),
`SettingsScreen.kt` (main page = theme + menu rows with one-line state (`SettingsSummary`); each row opens `SettingsPageScreen(SettingsPage)` on route `settings/page/{page}`: HOME, NEWS, MEDIA (coda, video successivo, Video filters, SponsorBlock row → SPONSORBLOCK page, canzoni, ad block), ALERTS, ACCOUNTS, DATA, INFO; the Note row opens AutoFillScreen directly; every persistent toggle belongs here, not in other screens; `RadioAlarmSetting.kt` = Sveglia con la radio in Notifiche; `AccountConnect.kt` = official Spotify/Google connection from Account + `SpotifyAuthDialog`), `UpdatesScreen.kt`, `LoginScreen.kt`.

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
  `includeGroupByRegex("com\\.github\\.TeamNewPipe.*")`). Since 1.272 it is pinned to the same commit the
  NewPipe app uses (visionOS client, survives the "not a bot" check; v0.26.5 did not): follow NewPipe's
  `gradle/libs.versions.toml`, never go back to an older tag. Extractor errors → Italian via `YtErrors`.
- Robolectric: guard GraphicsLayer use with `nativeCanvas.isHardwareAccelerated`; don't wait on
  `Playback.now` across tests (singleton bound to an earlier test's looper).
- Backlog lives in `BACKLOG.md` on the orphan branch `backlog`.
