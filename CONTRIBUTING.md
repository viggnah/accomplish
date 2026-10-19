# Contributing

Thanks for your interest in Accomplish. It's a small personal project, so a few notes keep things easy for everyone.

## Reporting bugs and ideas

Open an [issue](https://github.com/viggnah/accomplish/issues/new/choose). For bugs, include the app version (Settings › About), your phone and Android version, and the steps that led to it.

## Pull requests

- For anything beyond a small fix, open an issue first so we can agree on the approach.
- Keep changes focused, and match the style of the code around them.
- Run `./gradlew :app:testDebugUnitTest` and try the change on a phone or emulator, in light and dark mode.
- Add a line to `CHANGELOG.md` under a new version heading if the change is visible to users.

## Principles

The app stays small and private: no internet permission, no new libraries without a strong reason, and no text on screen that only explains a design decision.

By contributing you agree that your work is released under the [GPL-3.0](LICENSE).
