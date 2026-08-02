# Plugin Hub submission guide

Reference for submitting **Bestiary** to the [RuneLite Plugin Hub](https://github.com/runelite/plugin-hub).
Tracks issue **#84**. Do the final submission only after every release PR has merged to `main`.

## Compliance checklist

| Requirement | Status |
|---|---|
| Build system is Gradle, `runeLiteVersion = 'latest.release'` | ✅ `build.gradle` |
| Java 11 bytecode target | ✅ `options.release.set(11)` |
| BSD 2-Clause `LICENSE` present | ✅ |
| `runelite-plugin.properties` (displayName, author, description, tags, plugins) | ✅ |
| Icon ≤ 48×72 | N/A — sidebar icon is drawn programmatically (`BestiaryPlugin.buildPanelIcon`); no `icon.png` shipped |
| No native code / `sqlite-jdbc` | ✅ storage is plain JSON (`BestiaryStore`) |
| All HTTP via injected `OkHttpClient` | ✅ `WikiImageService` uses RuneLite's shared client |
| No blocking network/disk IO on the client thread | ✅ wiki fetch runs on `CompletableFuture.runAsync` |
| Network access disclosed | ✅ `wikiImages` config option description + README "Data & privacy" |
| Network access opt-in / off by default | ✅ `config.wikiImages()` defaults `false`, gates every fetch site |
| No reflection (beyond Gson `TypeToken`) | ✅ see audit below |
| No `getResourceAsStream` / runtime resource or code loading | ✅ none present |
| No external processes (`Runtime.exec` / `ProcessBuilder`) | ✅ none present |
| No player info sent over HTTP | ✅ only monster names are sent to the wiki |
| Dev cheats excluded from release | ✅ live on the `dev` branch only; `main` ships none |

### Reflection / resource-loading audit (issue #84)

Full-tree grep for `Class.forName`, `setAccessible`, `getDeclared*`, `getMethod`, `getResource`,
`getResourceAsStream`, `Runtime.getRuntime`, `ProcessBuilder`, raw `HttpURLConnection`:

- **No matches** for any prohibited API.
- The only `reflect` imports are `com.google.gson.reflect.TypeToken` / `java.lang.reflect.Type`,
  used for Gson generic deserialisation — standard and permitted.
- The sidebar icon is generated in code, so there is no bundled image resource to load.

## The submission file

The hub PR adds a single file `plugins/bestiary` (no extension) to `runelite/plugin-hub`:

```properties
repository=https://github.com/iTheFish/RuneLite-Bestiary-Plugin.git
commit=<40-char commit hash on this repo's main after all release PRs merge>
```

Fill `commit` with the final `main` HEAD:

```bash
git rev-parse main   # after PRs #154 (username) + this branch are merged
```

## Submission steps

1. Merge all pending release PRs to `main` here (username change, this prep branch, monster/species pass).
2. `git rev-parse main` → copy the 40-char hash.
3. Fork `runelite/plugin-hub`, add `plugins/bestiary` with the two lines above.
4. Open a PR to `runelite/plugin-hub`. Automated + human review runs on submission **and every update**
   (bump `commit` to publish a new version later).
5. Keep this repo public and the referenced commit reachable on `main`.
