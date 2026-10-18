# Reading Coach: notes for Claude

## Shipping changes: push straight to `main`, no PR

The owner has given standing permission: commit and push every change directly to `main`
yourself. Do not open a pull request, do not ask for approval or a merge, and do not leave work on
a feature branch. This holds even if the session was started on a different branch: finish the
work, then push it to `main` (`git push origin HEAD:main`, after pulling/rebasing on the latest
`main`).

This is a private family app, not a public one, so a breaking change is acceptable. Do a quick
check if it's easy, but never hold a push or ask the owner first; just ship it and fix forward.

## How updates reach the phone

- Content (stories, questions, lessons, tips, UI) lives in `app/src/main/assets/index.html`. The
  app downloads it from `main` on every launch, so a push to `main` is the whole release.
- Native Android changes (manifest, Java, icons, version) trigger the APK workflow
  (`.github/workflows/apk.yml`), which publishes to the `latest` release. Always bump
  `versionCode` in `app/build.gradle` for these: app v1.4+ compares it with its own version on
  launch and shows an "Update" button on the home screen, which downloads the release and opens
  Android's install screen (one confirm tap; Android requires it). Tell the owner to open the app
  and tap Update.
- The voice is natural recorded clips in `app/src/main/assets/voice/`, made by
  `tools/make_voice.py` (Kokoro TTS, voice `af_heart`). The owner disliked the robotic phone voice,
  so whenever you add or change a spoken line (anything passed to `say()`), rerun that script and
  commit the clips. Lines without a clip fall back to the phone's text-to-speech (`VOICE` settings).
