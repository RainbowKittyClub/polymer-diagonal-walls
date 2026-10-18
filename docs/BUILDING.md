# Building

```
./gradlew build     # jar lands in build/libs/<mod_id>-<version>.jar
```

The house build is documented once for every first-party mod in `rkc/STYLE.md` — versions in
`gradle.properties`, `processResources` templating of `fabric.mod.json` / the mixins json, the
benign `Failed to parse fabric.mod.json` and refmap warnings, linting, datagen, and the dev-server
workflow (`./gradlew runServer` / `stopServer`, `scripts/devserver.sh`). This file covers only where
*this* mod departs from it.

Record here only what applies, and delete the rest:

- **`mavenLocal()` dependencies** — bil, or another first-party mod pulled in by relocation. Name
  what has to be built and `publishToMavenLocal`'d first, in what order, and what a stale copy looks
  like at runtime. This is the most common reason a fresh clone will not build.
- **A datagen step `build` depends on** — the command (`./gradlew generateAssets`), what it derives
  into `src/main/generated`, and when it has to be rerun.
- **A non-obvious prerequisite** — a tool that isn't the Gradle wrapper, a tracked-but-generated
  file, a branch that isn't the default.
  **The name of the built jar** - if the build process produces intermediary jars that pollute `build/libs`.

If `./gradlew build` from a fresh clone really is the whole story, delete everything below the
command.
