# Changelog

## Unreleased

- EchoLyrics reclaims the foreground for a 15-second window after playback starts or the track changes, so Amazon's Now Playing screen no longer stays in front. It stands down whenever a Settings, Debug, or main screen is already visible, and makes no extra Spotify requests.
- Added horizontal swipe gestures: swipe left for next track, swipe right for previous track.
- Swipes are ignored when they start on the seek bar, so dragging to seek no longer changes tracks.
- Switched the bundled font to Figtree SemiBold (600).
- Added an optional accent-colour background with a subtle vertical gradient and contrast-safe highlight colour.

## v3

- Added adaptive playback polling with paused/idle backoff, near-track-end polling, Retry-After handling, and debug interval events.
- Added next-track queue lookup and one-time lyric prefetch.
- Added library save/unsave, shuffle, repeat, and seek controls with the required Spotify scopes.
- Added low-brightness idle behavior and configurable idle timeout.
- Added 15/30/45/60-minute sleep timers with safe pause handling.
- Added an in-memory 100-event debug log with a clear action.
- Expanded the separate scrollable Settings page with v3 options.
- Removed ambient clock/date mode from the v3 UI.
- Preserved API 25 compatibility and verified the debug build on the Echo Show.

## v2

- Added the separate Settings page, full scrollable synced lyrics, playback controls, seek slider, lyrics/art visibility, idle settings, and night mode.
- Added LRCLIB plus lyrics.ovh fallback and capped lyric disk cache.
- Fixed Fire OS API 25 login, OAuth redirect, and control-response crashes.
