# Changelog

All notable changes to Accomplish (called Matter until 0.5). Newest first. Versions match `versionName` in `app/build.gradle.kts`.

## Unreleased

### Changed
- First-run screens no longer assume an SD card: no "Recommended" badge or "No SD card found" line, and the error screen reads "Your tasks can't be reached".

## 0.10.0 · 20 Oct 2026

The first public release, now open source under the GPL-3.0.

### Changed
- The app ID is now `io.github.viggnah.accomplish`, so Android treats this as a new app. Your tasks carry over untouched, because they live on the SD card.

### Added
- Settings are kept in `settings.json` next to your data. A fresh install picks them up when you choose the same folder, so a reinstall no longer resets them.

## 0.9.0 · 19 Oct 2026

### Added
- A proper About at the bottom of Settings: the app icon, version, and how much you've got done since your first task.

### Changed
- Notifications follow Android's guidance. The app icon in violet replaces the per-type icons. The task is the title, and the line below says what's up ("Due in 2 days, Sat 24 Oct", "Untouched for 11 days" or the next step) instead of repeating the task name. Reminders are marked as reminders, so Do Not Disturb and the lock screen treat them properly.
- Tapping a notification goes where the task lives: Small stuff, Home or the Waiting room. "Anything new?" opens a new task.
- The data folder on the SD card is now called Accomplish. The old LifeOrganizer folder is renamed on first launch, with its backups.
- In New task, the top-right button reads "Add" once there's a title, since closing saves. The × only shows when there's nothing to save. The message after reads "Added to …".
- The chosen option in switches (When, size, theme) is solid, so it's easy to see, especially in light mode. The picked day in Coming up is solid too, and the date picker uses the app's violet.
- Coming up lists past-due tasks first, in red.
- Wording tidied to match the buttons and be plainer:
  - History says "Added" instead of "Jotted down".
  - It's always "side quest", never just "quest".
  - Settings has clearer names: "Warm up near a deadline", "Fade untouched ones after", "Time for deadlines and nudges", "Undo and adding", "The + button adds".
  - One-line empty states have no full stop.

## 0.8.1 · 16 Oct 2026

### Changed
- Deadlines due today, tomorrow or already past show in red with the clock icon: a red chip on cards, red text in Small stuff and Coming up.
- Cards are toned down in dark mode, and the frost on untouched cards is softer there.
- The drain meter is paler, so it sits behind the next step instead of competing with it.
- Corners come in three sizes: cards, sheets and panels (24), buttons and fields (14), and chips (fully round).
- Small controls are easier to hit: ticks, ×, steppers, toggles and swatches have at least a 48dp tap area, and buttons are at least 44dp tall.
- Empty screens offer the obvious next step: "New IMP task" in an empty Waiting room, and "Add small stuff" when there's none.
- The task sheet drops the "Remind me" label above "Add a reminder", and the step button reads "Add a step" / "Add another".

## 0.8.0 · 16 Oct 2026

### Added
- Snooze on your reminders: "In 1 hour" or "Tomorrow" (at your reminder hour), right from the notification. The new time shows on the card and in the task's history.

### Changed
- One typeface, Nunito, on a fixed size scale. Fredoka is kept for IMP card titles only, so the cards stand out.
- Labels and chips are in sentence case, with fewer "·" separators. The week strip is lighter, and violet is saved for the main actions.
- Notifications use the system's own layout, tinted with the task's colour. The round badge and the coloured box are gone.
- Reminders show at their time, not up to a minute early.
- History rows show when a task finished and how many steps it took, not the number of events.
- The size chip on Waiting room cards is lowercase, like the other chips.
- Settings › About shows the installed version.

### Fixed
- Changes made while the app was in the background (such as a snooze) show up as soon as you come back.

## 0.7.2 · 15 Oct 2026

### Removed
- Haptics, along with the "Vibrate on taps" setting and the vibration permission. They couldn't be felt on the phone this was built for, so they're out for now.

## 0.7.1 · 14 Oct 2026

### Changed
- Haptics drive the vibration motor directly, so they work even when the phone's own touch feedback is off. The app's "Vibrate on taps" switch is the only control.
- Moving an IMP card by dragging no longer offers undo, since dragging it back is just as easy.

## 0.7.0 · 14 Oct 2026

### Added
- Drag to reorder IMP cards on Home: long-press a card, then drag it to another spot. The long-press wait prevents accidental moves, and the move can be undone.
- Light haptic ticks on taps, and a firmer one when you tick off a step, finish a task or drop a card. They can be turned off in Settings › Looks.

### Changed
- Notifications have colour and character: an icon per type (bell, clock, snowflake, sparkle), the task's colour, a matching round badge, warmer wording ("Renew passport is due in 2 days"), and an expanded view that's a small card like on Home.
- The Spot control is gone from the task sheet. Reorder by dragging, and move to the Waiting room or place on Home with a button at the bottom.
- The "why" field asks "Why does this matter to you?" instead of explaining what happens to it.

## 0.6.1 · 14 Oct 2026

### Fixed
- Removing a step and then closing the sheet offered undo twice. Undo now comes once, when you remove something inside a sheet. Closing just saves, except a move between spots, which still offers undo.

## 0.6.0 · 14 Oct 2026

### Added
- Reminders on any task, including small stuff: "Remind me" in the new-task and edit sheets lets you pick a day, then a time. An upcoming reminder shows on the card ("today 17:00"). Reminders go off on the minute, on their own channel, even in quiet hours, and don't count towards the daily limit. Removing one can be undone.

## 0.5.2 · 14 Oct 2026

### Changed
- "When" is one choice with three consistent answers: Date, This month or Later. Today and This week are gone, since a near-term task usually has a known date. New tasks start on Later. Picking This month or Later replaces a date, with undo.
- "Someday" is now called "Later" everywhere.

## 0.5.1 · 14 Oct 2026

### Fixed
- Removing a next step or clearing a deadline in a sheet now offers undo, and the undo pill shows above open sheets.
- Saved edits and edited app notes can be undone.

### Changed
- Card chips start with a small letter ("this month", "today"), like "due 24 Oct".

## 0.5.0 · 13 Oct 2026

### Added
- Notifications, all switchable in Settings: deadline reminders 2 days before and the day before (optionally for small stuff too), a nudge when an IMP task starts to frost, a weekly review at a random evening time on the day you pick, and an occasional "anything new?" prompt. They respect quiet hours and a daily limit (2 by default), are re-armed after a restart, and use Android's own alarm service, with no extra libraries.
- A week strip on Home: the next seven days, with marks on days with deadlines. Tap it for Coming up.
- Empty spots show "N waiting" when tasks are waiting, and the Waiting room is in the ≡ menu.

### Changed
- Renamed the app to Accomplish.
- New tasks save when you close the sheet, like edits. There's no Save button, and an empty one is discarded. The save can be undone.
- Moving the data location brings your data along instead of starting empty.
- Removed the subtitle link under the Home title.

## 0.4.0 · 13 Oct 2026

### Changed
- Flat look throughout: cards, panels and buttons are solid colours with rounded corners. No gradients, highlights or shadows.
- Removed the floating background shapes, and their setting.
- "Jot it down" is now "New task".
- When and deadline are one choice: picking Today, Week, Month or Later clears the date, and picking a date lets the deadline decide.
- Cards show either the deadline or the rough "when", never both.

### Added
- Tap an app note to edit it.

### Fixed
- Changing "When" on a task with a deadline didn't show on its card.
- Settings panels had a leftover gradient in light mode.
- The text cursor sat on top of the last letter in input fields.

## 0.3.1 · 10 Oct 2026

### Removed
- Explanatory hints and subtitles across the app (e.g. "Saved quietly…", "Nothing is ever deleted", the Small stuff legend). Only prompts you need to act remain, shown when relevant.

### Fixed
- The deadline was shown twice in the task sheet. The date button now carries the date and a × to clear it.

## 0.3.0 · 10 Oct 2026

### Added
- Several planned next steps per IMP task, addable when creating it. The card shows the first and how many follow, and ticking it brings up the next.
- Delete app notes.

### Changed
- Undo appears only when something leaves the screen or can't be put back in place (done, let go, a step ticked, placing, restoring, deleting). In-place changes get no undo.
- Undo is a small centred pill with a countdown ring.
- Small stuff redesigned as plain grouped rows with clear check circles and a pink dot for near deadlines.
- No count badge on App notes in the menu.

## 0.2.0 · 10 Oct 2026

### Added
- App notes: jot ideas and feedback about the app itself.
- Tap an empty spot on Home to create an IMP task straight into it.

### Changed
- Named the app Matter.
- The whole page scrolls, header included.
- The ≡ menu opens as a small pop-up from the button.
- Flat, softly squared buttons and switches.
- When and deadline merged into one row.
- Better contrast in dark mode.
- "Why it matters" is added from the task when you like, no longer prompted.

## 0.1.0 · 10 Oct 2026

First version.
- IMP tasks in a few spots on Home, with frost when left untouched, a drain meter by rough size, and a next step on the card.
- Small stuff out of sight, grouped by when, warming near deadlines and fading when ignored.
- Waiting room, Coming up calendar and History with a full timeline per task.
- Capsules from moving IMP tasks forward: messages from past you, side quests, one-minute lessons.
- Undo for changes, everything configurable, data in a JSON file on the SD card with daily backups.
