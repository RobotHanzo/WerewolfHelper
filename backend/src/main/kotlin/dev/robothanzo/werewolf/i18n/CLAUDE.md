# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`i18n/` — localization. **zh-TW (繁體中文 · 台灣) is the only locale**, but everything player-facing
is keyed so a future locale is a drop-in bundle.

- `Msg` (`@Service`) is the thin wrapper over Spring's `MessageSource`, pinned to the default locale.
  Use `msg.msg(key, *args)` for any player-facing string (Discord embeds/buttons, log lines, API
  errors) — **never hardcode Chinese text** in engine/service/controller code. `Localizable`
  (key + captured params) lets an event carry its args for later rendering.
- `I18nConfig` builds a `ReloadableResourceBundleMessageSource` with basename `classpath:i18n/messages`,
  `setUseCodeAsDefaultMessage(true)` (a missing key renders as the key, not an exception) and no
  system-locale fallback. `DEFAULT_LOCALE` is `zh-TW`.
- **Gotcha:** the bundle file is `messages_zh_TW.properties` (Spring's underscore locale convention),
  even though the locale tag is `zh-TW`. Add new keys there.
- **Why log entries store both key+params and rendered text** (`GameLogEntry`): old records stay
  readable as-is while a future locale switch can re-render history from the key.
