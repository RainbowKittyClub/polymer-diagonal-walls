# Building

```
./gradlew build     # jar lands in build/libs/polymerdiagonalwalls-<version>.jar
```

The house build is documented once for every first-party mod in the workspace's `docs/STYLE.md`.
This file covers only where this mod departs from it.

- **`mavenLocal()` dependency.** kalic0re's `common/command` and `common/text` packages are
  relocated into the jar from `club.rainbowkitty.kalic0re:kalic0re:<kalic0re_version>` in `~/.m2`.
  A fresh clone needs `cd rkc/mods/kalic0re && ./gradlew publishToMavenLocal` at that version
  first; a stale copy shows up as a compile error naming a missing kalic0re class or method.
- **The jar to install** is `polymerdiagonalwalls-<version>.jar`. `-unshaded` and `-sources`
  beside it are not.
- **No datagen.** Lang, tags and the mixin config are static resources.
- **Test layouts** come from `scripts/layouts.sh`, which builds them over the dev server's rcon
  (port 25640, rcon 25642).
