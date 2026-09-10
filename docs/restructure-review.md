# Restructure + Optimize + QoL review

Baseline: `45081544c5f96e79c8cb0c6f6649465a5b71dc6d`. Existing repository and Android architecture retained.

## Before → After

| Area | Before | After / reason |
| --- | --- | --- |
| Search session | Repository owns volatile session and separate atomic generation; check and write can interleave | `SearchSessionStore.kt` owns generation, invalidation and publication under one short lock; no network under lock |
| Search cancellation | Generation reserved after dispatch; query clear does not cancel request | Reserve before IO dispatch; immediately invalidate/cancel on editing or clearing; cancellation checked after blocking extraction |
| Pagination | Cancelled loading-more flag can survive; network failure permanently disables more results | Reset loading flags on new query; keep cursor/results and offer explicit retry; use NewPipe `Page.isValid` |
| Compose loading trigger | Near-end state ignores request completion | Observe readiness as well as scroll position, so a page with duplicate items cannot stall solely because list size stays unchanged |
| Stream selection | Repository multiplies target kbps by 1,000, comparing incompatible units | `StreamSelector.kt` uses NewPipe average bitrate in kbps; filters unsupported manifest/non-URL streams and handles unknown values |
| URL cache | Bounded LRU already exists; uses wall clock and counts expired entries | Retain bounded, synchronized LRU; injectable monotonic clock, expiration pruning, generation barrier after clear |
| Playback connection | Playback can resolve before controller exists; connection errors escape callback; cancelled future leaks | Playback awaits serialized connection, callbacks use main executor, cancellation releases the future, errors reach existing snackbar |
| Queue deletion | Removes only order entry; shuffle rebuild resurrects deleted track and retains metadata | Remove track from backing list and reindex order; duplicate occurrences are still supported |
| Playback controls | Repeat availability stale; dismiss state diverges from internal modes; switching mode resumes paused playback | Publish queue controls immediately; dismiss preserves actual modes; retain paused state and resume position when switching |
| Async playback | End-of-track settings read can resume after a newer play command | Generation/state guards prevent old end events advancing a new request; pending playback honors sleep-timer pause |
| CI | APK compilation only | Same `build.yml` runs nine focused JVM regression tests and assembles APK; no local Gradle/Android build |

No new framework, backend, disk audio cache, settings schema, or dependency upgrade. JUnit 4.13.2 is test-only and pinned in `gradle.properties`. Existing NewPipe pin stays v0.26.5; Media3 stays 1.4.1; Compose BOM stays 2024.09.00; NewValve stays 1.5.

The baseline already replaced ConcurrentHashMap with a locked bounded LinkedHashMap. This change preserves thread safety and AtomicInteger generation protection rather than reverting the cache to an unbounded map.

## Side-effect / call-site audit

| Changed API / path | Callers and effects |
| --- | --- |
| `search`, `loadMoreSearchResults`, new `invalidateSearch` | Only `SearchViewModel`; `SearchResultPage` shape unchanged. Single active search remains intentional. Cancelled results cannot update UI. |
| `loadMore(isRetry = false)` / `loadMoreFailed` | `SearchScreen` automatic trigger retains default call; footer alone requests retry. Existing result keys and deduplication retained. |
| `StreamUrlCache.put` generation argument | Only the two repository resolve paths; both updated. `clearStreamCache` and `cachedStreamCount` still serve SettingsViewModel unchanged. |
| `StreamSelector` | Both repository resolve paths; return types stay URL strings. Bitrate setting now affects selection as intended. |
| `connect` | Called by `playCurrent`; eager PlayerViewModel init removed. First play, add-to-empty-queue, retry, next, previous and mode changes all use this path. Existing service stays alive during background playback. |
| `removeFromQueue` | PlayerViewModel → PlayerScreen swipe; adjusts backing indices without changing current item. Shuffle, repeat and subsequent queue adds use the corrected list. |
| Player listener / generation | `onEvents`, `onPlaybackStateChanged`, `onPlayerError`; old end events cannot override later play/dismiss commands. Media IDs also keep favorite identity aligned with metadata. |
| `retryPlayback` | AppNavHost snackbar; explicit retry clears the small URL cache to avoid reusing a rejected URL and resumes only if the current media ID matches the selected track. Other cached URLs will need resolving again. |
| `stopAndDismiss`, timer, mode changes | MiniPlayerBar and PlayerViewModel; preserve mode/speed/repeat/shuffle consistency; timer uses elapsed realtime and prevents a pending resolve from restarting playback. |
| Test dependency / workflow | Only test classpath and build command changed. PR runs do not publish releases. Signing and update workflow unchanged. |

Refactor checkpoint: reviewed after the three work groups (search; stream/cache; player). Extraction is limited to two responsibilities; queue ownership stays in PlayerController.

## Config examples (bad → good)

| Bad | Good used here |
| --- | --- |
| `LinkedHashMap(16, 0.75f, true)` in cache | Capacity/load factor from `AppConstants` |
| Inline cache TTL or limit | Existing `STREAM_CACHE_TTL_MILLIS`, `MAX_STREAM_CACHE_ENTRIES` |
| Inline debounce/retry delay | Existing configured debounce; retry is user initiated with no arbitrary delay |
| New UI message literal in composable | `strings.xml` resource |
| Test dependency version embedded in Gradle dependency | `junitVersion` property |

Test fixtures use explicit test parameters/constants; structural values (zero, list index increments, null/boolean states, API enums) are not user-tunable configuration. Existing unrelated visual dimensions and strings are not broadly rewritten in this bug-fix pass.

## QoL checklist

- [x] Clear query cancels stale work and returns to history.
- [x] New search cannot inherit a stuck load-more spinner.
- [x] Failed next page can be retried without discarding loaded results.
- [x] Audio quality selection uses the correct bitrate unit.
- [x] Deleted queue entries stay deleted across shuffle toggles.
- [x] First play waits for the controller; connection failures use the retry snackbar.
- [x] Repeat updates next/previous availability immediately.
- [x] Switching audio/video retains paused state.
- [x] Timer and cache TTL tolerate system clock adjustments.
- [x] Player errors expose retry and refresh rejected stream URLs.
- [ ] User decision: persist queue/current position across process death (storage and restore behavior).
- [ ] User decision: high-resolution separate audio/video merging (bandwidth and quality policy).
- [ ] User decision: bounded disk audio cache/offline support (storage quota and clearing policy).

## Preserved features and validation limits

Search debounce/pagination/deduplication, swipe gestures, queue insertion/reordering, waveform component, dynamic color, audio/video mode, theme/accent/audio/video settings, auto-advance, cache clearing, automatic/manual update checks, build display, favorites, speed and sleep timer remain in the source. Keystore and `update-newpipe.yml` are byte-for-byte unchanged.

CI compilation and the focused tests do **not** establish that all Android/device behaviors work. No local SDK build, emulator, device playback or live YouTube extraction was run. The final PR links the actual CI result; do not describe this report alone as proof of a passing build.

Device regression scenarios still required:

1. Type A → B quickly, clear during network wait, select history before debounce expires; only latest query should win.
2. Start pagination, immediately search again; spinner recovers. Disconnect/reconnect network and retry footer; existing results remain.
3. Cold start → play; next/previous rapidly; dismiss while resolving; controller must not play a superseded track.
4. Queue A/B/C (including duplicate track IDs), remove B, toggle shuffle on/off, reorder and repeat; deleted occurrence must not return.
5. Pause, switch audio/video repeatedly, seek, change speed, sleep timer expires while resolving.
6. Screen off/background playback, notification/headset next/previous, unplug headphones; inspect waveform with permission granted/denied.
7. All settings, dynamic color, favorites, auto/manual update, cache clear and installed-APK update signing.

TODO(debt): The baseline service's ForwardingPlayer forces queue command getters without forwarding matching command-change events; notification button enablement needs an instrumented service regression before a broader queue/session migration.

TODO(debt): The baseline waveform assumes MediaController transports audio session callbacks. Device validation is required; direct same-process service propagation may be needed. The existing waveform path is retained rather than claiming it has been verified.

TODO(debt): The existing update checker maps network errors to “no update”; a typed update result with settings feedback should be a separate focused fix. No silent update-flow rewrite is included here.

TODO(debt): Blocking NewPipe calls may continue consuming network until they return after cancellation. Generation/cancellation barriers prevent stale state; making the downloader cancellable requires a NewValve downloader ownership change.

## Official references checked before related edits

- [NewPipe v0.26.5 AudioStream](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/stream/AudioStream.java), [ItagItem](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/ItagItem.java), [YoutubeStreamExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.java), [Page](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/Page.java).
- [Media3 1.4.1 MediaController source](https://github.com/androidx/media/blob/1.4.1/libraries/session/src/main/java/androidx/media3/session/MediaController.java), [connection lifecycle](https://developer.android.com/media/media3/session/connect-to-media-app), [1.4.1 release](https://developer.android.com/jetpack/androidx/releases/media3#1.4.1).
- [Compose effects](https://developer.android.com/develop/ui/compose/side-effects), [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping).
- [Coroutine cancellation/resource ownership](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/suspend-cancellable-coroutine.html), [Mutex](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/), [SystemClock](https://developer.android.com/reference/android/os/SystemClock).
- [Android JVM tests](https://developer.android.com/training/testing/local-tests), [JUnit 4.13.2](https://junit.org/junit4/javadoc/4.13.2/org/junit/Assert.html).

## Nine-point delivery checklist

The skill summary named a nine-point checklist without supplying its items; this task-specific checklist is not presented as the missing original.

1. [x] Read real repository and pin the baseline.
2. [x] Present Before → After before edits.
3. [x] Check official API docs / pinned source before relevant changes.
4. [x] Separate concerns without a replacement project or unnecessary framework.
5. [x] Keep new tunable values in config/resources/parameters.
6. [x] Trace changed call sites, listeners, async chains and return types.
7. [x] Preserve signing, dependency pin and automatic NewPipe workflow.
8. [ ] Verify final commit with GitHub Actions `build.yml`; link outcome in PR.
9. [x] Disclose remaining debt and device-only validation rather than claiming untested behavior.
