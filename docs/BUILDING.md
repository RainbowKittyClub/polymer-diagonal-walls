# Building

```
./gradlew build     # versions/<v>/build/libs/polymerdiagonalwalls-<version>+<v>.jar, one per version
```

The house build is documented once for every first-party mod in the workspace's `docs/STYLE.md`.
This file covers only where this mod departs from it.

- **Multi-version build** (Stonecutter): 26.2 and 26.3, with 26.2 committed. Each version's keys
  are in `versions/<v>/gradle.properties`.
  - 26.3 builds against Polymer 0.18.4 from Maven.
  - Dev servers are on ports 25640/25642 (26.2) and 25644/25646 (26.3).
  - `scripts/layouts.sh`, `scripts/seal-legs.sh` and `scripts/columns-check.js` take
    `DEV_MC=26.3` for the 26.3 server.
  - `columns-check.js` can't reach 26.3, because ViaFabric has no 26.3 server module for its
    26.1 bots.
- **`mavenLocal()` dependency.** kalic0re's `common/command` and `common/text` packages are
  relocated into the jar from `club.rainbowkitty.kalic0re:kalic0re:<kalic0re_version>` in `~/.m2`,
  one build per version (`0.31.0+26.2`, `0.31.0+26.3`).
  - A fresh clone first needs `./gradlew publishToMavenLocal` in kalic0re, from its multi-version
    build.
  - A stale copy shows up as a compile error naming a missing kalic0re class or method.
- **The jar to install** is `polymerdiagonalwalls-<version>+<v>.jar` for the server's version.
  `-unshaded` and `-sources` beside it are not.
- **No datagen.** Lang, tags and the mixin config are static resources.
- **Test layouts** come from `scripts/layouts.sh`, which builds them over the dev server's rcon
  (port 25640, rcon 25642 on 26.2).
