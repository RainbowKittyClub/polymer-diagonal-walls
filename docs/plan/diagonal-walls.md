# Diagonal walls, fences and panes

A server-side Fabric mod, `polymerdiagonalwalls`, that joins two walls, fences or panes standing
corner to corner with a diagonal arm drawn by Polymer block display entities. Each arm gets
invisible, client-only shulker columns that a vanilla client collides with, plus tiny slimes that
ease players along it. Every other entity gets server-side collision boxes, so a vanilla client sees
and bumps into every diagonal with no mod or pack installed.

Verified sources: vanilla claims against `libs/decompiledjava/minecraft-26.2/` and the model JSON in
the 26.2 client jar. Polymer claims against `libs/polymer` at `9a3e8150` (0.17.5+26.2), ppb against
`rkc/forks/polymer-patch-bundle` `rkc/26.2` `1758312`, kalic0re against `main` `7a4fdbb` (0.31.0),
and WorldEdit against the bytecode of the live `worldedit-mod-7.4.4.jar`. Shulker and slime
collision was tried with real entities on a vanilla 26.2 scratch server on 2026-10-18, judged by
the user in a vanilla client (see "Reference: Collision spike"). Seven things are unverified, and
each is flagged in the stage that settles it:

- the display transform's rotation sign and how 45° faces shade;
- whether Lithium's collision sweep calls the context form of `getCollisionShape`;
- how Axiom writes blocks;
- how the mods' bridged side models sit in an item display, and how Enderscape's overgrown walls'
  glowing element renders there;
- whether a non-ticking holder sends element changes without a manual `tick()`;
- whether virtual shulkers block like the spike's real ones and never rubber-band;
- how a fence arm looks with the post its block display can't drop.

Stage order: S1 → S2 → S3. Then three branches can be built in any order: S4 and S5 (live updates
and chunk lifecycle, both on S3), S6 (tall arms, on S3) and S7 → S8 (collision, then pathfinding,
on S2 only). S9 (settings) needs S3 and S7. S10 (fence and pane arms) needs S3; S11 → S12
(shulker columns, then slimes, for every family) need S10; S13 (mob collision for fences and panes)
needs S7 and S10. S14 (PPB and Enderscape blocks) needs S6, S10 and S11. S15 (load and editors)
needs S4, S5 and S11. S13 makes the feature complete for vanilla blocks, S14 for modded ones, and
S16 ships it.

---

## Findings that reshape the work

**Vanilla posts alone hold only full-size players at a wall, and nobody at a fence or pane, so
every family gets client collision.** A wall post is 8 px wide (`WallBlock.java:71`) with 24 px
collision (`:67`). Two wall posts standing corner to corner leave a gap that a box narrower than 0.5
fits through (see "Reference: Gap arithmetic"). That stops a 0.6-wide player, but not one shrunk
below scale 0.83. A full-size player's box can also reach 0.35 past the arm's centre line, beyond
its 0.1875 half-width, so it pokes right through the arm. Fence posts are
4 px (`FenceBlock.java:37`) and pane posts 2 px (`IronBarsBlock.java:30`), leaving gaps of 0.75 and
0.875 that anyone walks through. Display entities can't give the client collision; shulkers can.

**A vanilla client treats shulkers as solid, so invisible client-only shulkers can be an arm's
collision.** Only shulkers, boats and happy ghasts block a player (`canBeCollidedWith`,
`Shulker.java:471`, `AbstractBoat.java:119`, `HappyGhast.java:577`). A shulker's box is `scale`
wide and `scale × (1 + peek)` tall (`Shulker.java:259`, `:211`). Columns of three 0.25 shulkers,
fully open and 1.5 tall, tile an arm from post corner to post corner: two columns for a wall, three
for a fence. Every box touches its neighbours only at corners, which no box of any width can pass.
In the spike, three fence columns blocked a vanilla client at every scale the game allows. A wall's
two columns cover exactly S7's server-side corner boxes, so the client and the server hold the same
shape. Two traps shape S11: a shulker snaps to the block centre unless it rides something
(`Shulker.java:329`), and it closes its lid whenever its block position changes after its first
tick (`:337`).

**No block state changes, so the mod needs no Polymer block, no registry entry and no
migration.** Every diagonal endpoint already raises its post: `shouldRaisePost` returns true for
any wall that is not a straight run (`WallBlock.java:223`), and the S2 rule only admits such walls.
The client therefore already holds the right state. The mod adds virtual entities, a server-side
shape and one block tag. Nothing is saved to the world, and removing the jar leaves no trace.

**The diagonal rule reads only the two endpoint states.** A connects to B = A + (dx, 0, dz) when
both are diagonal walls and all four of their sides facing the two shared neighbours are `NONE`.
Walls connect to every `#minecraft:walls` neighbour (`WallBlock.java:108`), so any change at a
shared neighbour that matters reaches A or B as a state change of their own. That has two
consequences. A block change at P can only touch the diagonals of P itself, plus P's lower
neighbour for arm height (S6). And arms can never cross: if both shared neighbours are walls,
vanilla closes the 2×2 ring and neither diagonal qualifies.

**Vanilla pathfinding deliberately walks narrow mobs between diagonal posts.**
`WalkNodeEvaluator.java:172` sets `canPassBetweenPosts` for any mob narrower than 0.5 when both
cardinal nodes are `FENCE`. If S7 seals the gap without S8, chickens and baby animals will keep
pathing into the corner and pressing against it. Fliers never route through it: the fly evaluator
needs both cardinal nodes at malus ≥ 0 (`FlyNodeEvaluator.java:149`), and `FENCE` is −1
(`PathType.java:11`). Swimmers never do either: a waterlogged wall, fence or pane is a `BLOCKED`
swim node (`SwimNodeEvaluator.java:132`, `WallBlock.java:101`, `CrossCollisionBlock.java:91`), and
a swim diagonal needs both sides open (`:62`). The walk evaluator tests a candidate node with the
mob's own collision context (`WalkNodeEvaluator.java:364`), so it sees S7's boxes. Only floor
height reads the cached shape (`:219`), and no mob stands on a wall.

**One 4×4 px box in each endpoint's corner seals the gap for any entity width.** Post plus corner
box in A, and the mirror image in B, form a chain of boxes that touch only at corners. No
positive-width axis-aligned box can pass such a chain (see "Reference: Gap arithmetic"). The corner
box also sits inside the visual arm, so nothing collides with empty air.

**The block-change hook has to be `LevelChunk.setBlockState`, not `Level.setBlock`.** WorldEdit
7.4.4, live on survival, writes through `CoreMcWorldNativeAccess` → `LevelChunk.setBlockState`
directly. `Level.setBlock` reaches the same method (`Level.java:230`). Polymer hooks it too
(`WorldChunkMixin.java:87`, `:99`).

**Modded walls, fences and bars reach clients as vanilla stand-ins, so their arms must be item
displays of the mods' own models.** PPB and enderscape-patch both send a stand-in state:
`sandstone_wall` for a wall, `birch_fence` for a fence, and an empty-model copper bars state for
bars. They then draw the real look with one item display per model part (see "Reference: Modded
blocks on survival"). Polymer re-encodes every block state inside entity data per player
(`ByteBufCodecsEntriesMixin.java:65`, `EntityDataSerializers.java:72`). So a block display of a
modded state shows the stand-in, or nothing at all for bars. Both mods already put their models in
the pack as items (`<namespace>:-/block/<model>`), and survival requires the pack, so S14 can point
item displays at them directly. Live, that covers 27 walls, 16 fences and one set of bars.

## How these stages get tested

The dev server runs through `scripts/devserver.sh rkc/mods/polymer-diagonal-walls …` on port 25640,
rcon 25642, both free on 2026-10-18. It uses the flat creative world that `gradle/devserver.gradle`
seeds. Test layouts get built by `scripts/layouts.sh` in the mod repo: rcon `setblock` lines at x ≥
100, away from spawn. The layouts are:

| Layout | Blocks | Expected diagonal |
|---|---|---|
| A | two cobblestone walls corner to corner | one, owned by the west wall |
| B | A plus a wall on one shared neighbour | none (vanilla L) |
| C | 2×2 ring of walls | none |
| D | A plus a stone block on one shared neighbour | none (A connects to its face) |
| E | cobblestone and brick walls corner to corner | one, owner's material |
| F | A placed across a chunk border | one, whichever chunk loads first |
| G | a diamond pen: four diagonal runs of five walls | sixteen |
| H | two oak fences corner to corner | one |
| I | six oak fences in a diagonal run | five |
| J | two glass panes corner to corner | one |
| K | an oak fence and a nether brick fence corner to corner | none (vanilla never joins them) |
| L | G built from oak fences | sixteen |
| M | a three-wall diagonal run, two high | four; the lower two tall |
| N | A under a 2×2 bottom-slab ceiling | one, tall |
| O | A with stone above both walls | one, tall |
| P | A with a torch on each wall | one, low |
| Q | A with a bottom slab above the owner only | one, low |
| R | A with a top slab above each wall | one, low |

The user does the visual checks in the dev client, and gets a short "what you'll see" before each
round. A vanilla 26.2 client (no Fabric) joins the dev server for the no-mod view. That works
because the mod adds no registry entries. Mob, item and player-movement checks go through mineflayer
at `scripts/loadtest/polymer-diagonal-walls.js`, with the Via jar in `run/mods` (house pattern) and
`execute if entity` counts over rcon. From S7 on, `run/mods` also holds
`lithium-fabric-0.25.3+mc26.2`, the build on survival. A stage isn't done until its "Done when" has
been observed, so a failure at stage N points at stage N, not the ones before it.

Column collision is also checked at reduced player size (`/attribute @s minecraft:scale
base set <s>`, down to the minimum 0.0625). A client draws no F3+B hitbox for an invisible entity
(`EntityHitboxDebugRenderer.java:38`), so S11 adds an op command that shows the columns.

## S1 — Scaffold `polywalls` — **done**

**Build.** This folder holds only this plan, and `git clone` refuses a non-empty target, so move the
plan aside first:

```bash
cd rkc/mods
mv polywalls .polywalls-plan
git clone ssh://git@github.com/RainbowKittyClub/mod-template_fabric polywalls
(cd polywalls && ./setup.sh)
mkdir -p polywalls/docs/plan && mv .polywalls-plan/docs/plan/diagonal-walls.md polywalls/docs/plan/
rmdir .polywalls-plan/docs/plan .polywalls-plan/docs .polywalls-plan
```

Answers for `setup.sh`: mod id `polywalls`, entry class `Polywalls`. Then:

- delete the example `content/`, `polymer/` and `example_block` assets;
- drop the bil hard dependency from `fabric.mod.json` and remove `bil_version` (nothing animates);
- set `polymer_version=0.17.5+26.2` to match live;
- set `dev_server_port=25644` and `dev_rcon_port=25646`;
- add the mod to the `mods` map in `scripts/lint/build.gradle`;
- add a row to the workspace `CLAUDE.md` table.

**Done when.** `./gradlew build` writes `build/libs/polywalls-0.1.0.jar`. `scripts/devserver.sh
rkc/mods/polywalls start` boots with `polywalls` in the mod list and `… mixins` reports 0 failures.
`./gradlew lintAll` in `scripts/lint` lints the new mod.

### As built

The user scaffolded the repo from the template as `rkc/mods/polymer-diagonal-walls` instead of the
clone dance above: mod id `polymerdiagonalwalls`, entry class `PolymerDiagonalWalls`, Unlicense,
dev port 25640 and rcon 25642 (not 25644/25646), all in the initial commit. The rest of the Build
list then went in on top:

- the example `content/` and `polymer/` packages, every `example_block` asset, loot table and
  mineable tag, and the datagen source set with `enable_datagen`/`datagen_client` are gone, since
  the mod needs no datagen;
- bil is out of `fabric.mod.json` and `gradle.properties`, and so is
  `fabric_permissions_api_version`, which only bil's block in the shared build reads;
- `fabric.mod.json` depends on `polymer-core` and `polymer-virtual-entity` instead of suggesting
  four Polymer modules, because the arms are virtual entities;
- `polymer_version=0.17.5+26.2`, plus `kalic0re_version=0.31.0` and
  `kalic0re_shared_packages=command,text` for S2's command gate and text.

`./gradlew build` writes `build/libs/polymerdiagonalwalls-0.1.0.jar`; a dev-server boot lists
`polymerdiagonalwalls 0.1.0` among 54 mods, with Polymer 0.17.5+26.2, and `mixins` reports none.
The lint entry in `scripts/lint/build.gradle` and the `CLAUDE.md` row are workspace edits made
outside this repo; `checkstyleTarget` on `src/main/java` passes.

## S2 — Diagonal rule and probe command — **done**

**Build.** `wall/DiagonalRule` holds static, side-effect-free methods:

- `isDiagonalWall(state)` tests the tag `polymerdiagonalwalls:diagonal_walls`. Its data file holds
  `#minecraft:walls`, and server owners remove walls from it to opt them out.
- `connects(a, b, dx, dz)` applies the endpoint rule from the findings. It only joins walls at the
  same y, like vanilla.
- `owned(level, pos)` returns the up to two diagonals a wall owns. The owner is the west endpoint,
  so each wall owns its (+1, ±1) diagonals and every arm has exactly one owner.
- `all(level, pos)` returns all four diagonals, for S7.

Partner reads go through `level.getChunkSource().getChunkNow(cx, cz)`. An unloaded partner means no
diagonal, and never a chunk load.

`/diagonals probe <pos>` prints a wall's owned diagonals. It is gated through kalic0re
`CommandPermissions` on node `polymerdiagonalwalls.command.probe` (fallback op 2). Shade kalic0re
`command,text`.

**Done when.** After `scripts/layouts.sh`, `probe` at each layout's owner prints exactly the
"Expected diagonal" column of the layout table. For F, it prints the diagonal once both chunks are
loaded, and none while the partner's chunk is unloaded (`/forceload`, players away).

### As built

The rule is family-pluggable, so S10 adds fences and panes without touching it. It lives in
`diagonal/`, not `wall/`:

- `DiagonalFamily` answers `admits(state)`, `sideOpen(state, side)` and `joins(a, b)`;
  `DiagonalRule.register` adds one, and `familyOf` and `isDiagonalBlock` ask every family.
- `wall/WallFamily` is the walls' family. Its `admits` is the planned `isDiagonalWall`: a
  `WallBlock` in `polymerdiagonalwalls:diagonal_walls`, which holds `#minecraft:walls`.
- `connects(a, b, dx, dz)` is as planned. `owned` and `all` return `Diagonal` records, which name
  the west end, `dz` and both states. Each also takes a `BlockReader`, so S7 can read through a
  `BlockGetter`. `BlockReader.loaded(level)` reads through `getChunkNow`, which answers `null` off
  the server thread.

`/diagonals probe <pos>` prints block ids, so E's materials read unambiguously, and returns the
owned count. Its node is `polymerdiagonalwalls.command.probe`, as planned, which leaves the root
literal out of kalic0re's `<modId>.command.<path>`. `scripts/layouts.sh build` forceloads the strip
x 96..191, z 96..111, because 26.2 has no spawn chunks and a headless server loads nothing without a
ticket. Every layout stands at y −53, the seeded flat world's surface.

`scripts/layouts.sh check` probes every block of A–G and matches the table: A one (west wall
owns it), B, C and D none, E one (`cobblestone_wall` owns it, partner `brick_wall`), F one. G gave
sixteen, not the twelve this table first said: four five-wall runs sharing their corners are
sixteen walls and sixteen diagonals. The table now says sixteen for G and for L.

For F, `scripts/layouts.sh f-split` forces only chunk x 7. That leaves the owner's chunk x 9 at
level 33 (full, not ticking) and the partner's chunk x 10 at level 34. Probe then printed the owner
and "owns no diagonals"; forcing chunk x 10 gave "owns 1" on the next command. rcon joins a
command's messages with no separator, so `check` counts diagonals by match, not by line.

## S3 — Arm displays — **in progress**

**Build.** `display/ArmHolder extends ElementHolder` holds one `BlockDisplayElement` per owned
diagonal (one or two):

- **State.** The owner block's default state with `up=false,north=low,south=low`, everything else
  `NONE`, not waterlogged. That renders a seamless 16 px bar, because the side model has no south
  face (`template_wall_side`).
- **Transform.** As in "Reference: Arm geometry".
- **Culling.** `setDisplayWidth(1.25f)` and `setDisplayHeight(1f)`, so the client can frustum-cull
  arms. Culling is off when either value is 0 (`Display.java:393`).
- **View range.** 2.0 (128 blocks), revisited in S9.

Attach with the public constructor `new ChunkAttachment(holder, chunk, pos, false)`
(`ChunkAttachment.java:29`). Do not use `ChunkAttachment.of`: it calls `world.getChunk`, which loads
a missing chunk, and falls back to a warning (`:50`). The holder is non-ticking, because nothing
animates.

`display/ArmIndex` keeps one map per `ServerLevel`, keyed by chunk and then by owner
`BlockPos.asLong`. `refresh(level, pos)` recomputes `owned`. If there are none, it destroys the
holder; if anything changed, it destroys and recreates the holder rather than mutating elements in
place. Recreating sidesteps the open question of whether a non-ticking holder sends element changes
without a manual `tick()`, at the cost of one remove-and-add per edit. In this stage arms appear
only through `/diagonals rebuild <radius>`; S4 and S5 make them automatic. `/diagonals stats`
prints the holder and arm counts.

**Done when.** The user sees all of this in the dev client after `rebuild`:

- arms on A, E, F and G, none on B–D;
- from all eight horizontal angles and from above, no arm end showing outside a post;
- no flicker where an arm meets a post top or the ground;
- texture running along the arm;
- arms lit like the posts beside them, at noon and under a torch at night.

The vanilla client sees the same. Two unknowns get settled here: if arms run along the wrong
diagonal, flip θ in the reference; if the 45° faces shade visibly differently from the posts, note
it in As built.

## S4 — Live updates — **done**

**Build.** `mixin/LevelChunkMixin` hooks `LevelChunk.setBlockState(BlockPos, BlockState, int)` at
`RETURN` with a non-null return value, on server levels only. If the old or new state is a diagonal
wall, it marks P dirty. If the state below P is a diagonal wall (same column, same chunk), it marks
`P.below()` dirty for S6. The dirty set is a concurrent per-level long set, drained at
`ServerTickEvents.END_LEVEL_TICK`. For each dirty P, the drain refreshes P (as owner) and P's two
possible owners, `P + (−1, 0, ±1)`.

Nothing reads another chunk inside `setBlockState`. That follows the same rule as the
`createElementHolder` deadlock in `project_polymer_26_2_gotchas`.

**Done when.** Each of these changes the arm within one tick in the dev client:

- placing B diagonal to A adds the arm;
- breaking either wall removes it;
- a wall placed on a shared neighbour removes the arm and vanilla shows the L, and removing that
  wall brings the arm back;
- a sticky piston pushing A away removes the arm, and retracting brings it back;
- TNT destroying one end removes it.

An rcon place-and-break loop of 1000 rounds ends with `stats` equal to the layout total.

### As built

The hook is as planned, and passes on only a non-null return, the replaced state. It hands off to
`lifecycle/ArmUpdates.blockChanged`, which marks P when its old or new state belongs to any family,
not only walls, and marks `P.below()` the same way. The per-level set is a `LongOpenHashSet`
behind a lock rather than a concurrent set. The drain gathers P and P + (−1, 0, ±1) for every
mark into one set first, so a position marked many times in a tick is refreshed once.

`scripts/layouts.sh live` checks each step through a datapack it writes into the dev world. The
pack samples `stats` at the start of each tick's scheduled functions, before that tick's drain.
Every step moved the arm count at the first sample after the tick that changed the wall:

- Breaking and placing either end of A, and walling A's shared neighbour into vanilla's L and
  clearing it: a command changes the wall between ticks, and the arm moved in the next tick's drain.
- A sticky piston under an owner pushing it up off its partner's level and pulling it back, and TNT
  at power 2.5 one block west of an owner: the wall changed inside a tick, and the arm moved in that
  same tick's drain. The TNT left the partner standing.

A mineflayer bot watched the same steps. For command changes it received the arm's spawn or destroy
0–1 ms after the block update. For changes inside a tick, the arm came first. The chunk pass that
broadcasts block changes runs before block events, entities and block entities
(`ServerLevel.java:401`, `:404`, `:450`), so their changes go out in the next tick's pass. The arm
came 47–50 ms ahead for the TNT and the piston's return. For the push, the moved block sends no
update (`PistonBaseBlock.java:309`, flags 324), so the client animates from the block event, which
arrives in the same tick as the destroy.

`scripts/layouts.sh loop 1000` broke and replaced A's partner and one G wall per round over one rcon
connection, pausing 0–30 ms at random after each command. All 4000 commands changed a block over
61 s, and `stats` then read the layout total: 19 arms on 18 holders.

## S5 — Chunk load and unload — **done**

**Build.** `ServerChunkEvents.CHUNK_LOAD` (`(ServerLevel, LevelChunk, boolean)`,
`fabric-lifecycle-events-v1` 4.1.9) only enqueues the chunk position. At drain, if `getChunkNow`
still returns the chunk, each section whose `getStates().maybeHas(DiagonalRule::isDiagonalWall)` is
scanned. Every wall found gets refreshed, along with its two west-side owners. That covers an owner
in an older neighbour chunk whose partner was missing until now.

`CHUNK_UNLOAD` drops that chunk's index map; Polymer has already destroyed its holders
(`WorldChunkMixin.java:111`). Two traps apply:

- `CHUNK_LOAD` can fire inside a `ServerLevel` constructor. bannerpoint's banner chunks do this,
  which caused the player-portals 0.1.0 NPE. So the handler touches no level storage.
- C2ME may raise the event off the server thread, which is why the queue is concurrent.

**Done when.**

- Arms are present after a `/tp` 2000 blocks away and back, and after a dev-server restart.
- Layout F works when either chunk is loaded first (teleport in from both sides).
- With every layout chunk unloaded, `stats` reads 0.
- `isn't loaded` (`ChunkAttachment.java:56`) never appears in the log.

### As built

Built as planned, with three additions:

- **Return to FULL.** A chunk demoted from FULL to INACCESSIBLE stays in memory, and its completed
  FULL future is never cleared (`GenerationChunkHolder.java:164`). Promoting it again therefore runs
  no FULL task and raises no `CHUNK_LOAD`. A `FULL_CHUNK_STATUS_CHANGE` out of INACCESSIBLE queues
  the chunk as well. `scripts/layouts.sh f-return` demotes F's partner chunk, rebuilds so the
  owner's arm goes, then forces the partner again. The arm came back; with the listener disabled
  it stayed gone (18 arms drawn, 19 diagonals seen).
- **Waiting for the chunk.** A queued chunk that `getChunkNow` cannot return yet is retried for up
  to 20 drains, in case the load event fires before FULL is visible. It never happened on the dev
  server.
- **Unload.** `CHUNK_UNLOAD` queues the drop as well, and the drain drops before it scans, so a
  chunk that unloads and loads again within one tick is rebuilt. An unload cancels a queued load.

C2ME 0.4.1-beta.1, on survival, replaces the chunk system and raises these events itself
(`LifecycleEventInvoker`): `CHUNK_LOAD` only the first time a chunk reaches FULL, and
`FULL_CHUNK_STATUS_CHANGE` on every promotion and demotion. Each path that raises one of the three
events asserts the main thread first (its `ServerAccessible` and `ReadFromDisk` statuses), so that
version raises none of them off the server thread.

Checked on the dev server, with forceload standing in for a player except where a bot is named:

- `scripts/layouts.sh unload` (`forceload remove all`): `stats` read 0 within a second, and
  `reload` brought back all 19 arms.
- After each of three restarts, `stats` read 19 with no `rebuild`. `scripts/layouts.sh drawn`, which
  compares arms drawn with the diagonals `probe` sees across every layout block, matched.
- `f-first owner` forces chunk x 7, so F's owner chunk is FULL and its partner's is not.
  `f-first partner` forces chunk x 12, the reverse. In both, drawn matched seen with one chunk and
  again once the other loaded, and F's owner then owned 1.
- A bot stood at the layouts with nothing forced and saw 19 arms. Teleported 2000 blocks east, it
  held no arm and `stats` read 0 within a second. Teleported back, it saw 19 again.
- `isn't loaded` appears in none of the run's logs. The mod never calls `ChunkAttachment.of`.

## S6 — Tall arms and stacked diagonal walls — **in progress**

**Build.** `DiagonalRule.tall(level, a, b, dx, dz)` returns true in either of two cases:

- above A and above B are diagonal walls joined by the same diagonal (a stacked run);
- the down-faces of both blocks above (`getCollisionShape(…).getFaceShape(DOWN)`, as at
  `WallBlock.java:205`) cover a diagonal test strip. The strip is 2 px boxes along the centre line
  from the post to the cell corner, the diagonal counterpart of `TEST_SHAPES_WALL` (`:47`).

A tall arm uses `north=tall,south=tall`. S4's `P.below()` mark already covers changes above.
The tall side model runs to y 16, so S3's lift of ε with y scale 1 − ε would put a tall arm's top
exactly on the post top, z-fighting where the arm end sits inside the post. Lift a tall arm by
ε/2 instead (found while building S3).

**Done when.**

- A two-high stacked diagonal run shows a continuous face, with no 2 px gap at 14/16.
- Diagonals under a slab ceiling or full blocks show tall arms; under air or torches they show low
  arms.
- Breaking the upper wall drops the lower arm to low within a tick.

## S7 — Solid gap for non-players — **planned**

**Build.** `mixin/WallBlockMixin` hooks `WallBlock.getCollisionShape(BlockState, BlockGetter,
BlockPos, CollisionContext)` (`WallBlock.java:96`) at `RETURN`. It applies when the context is an
`EntityCollisionContext` whose entity (`EntityCollisionContext.java:82`) is neither a player nor
carrying one (as passenger or controller, the better-barriers rule). In that case it ORs the corner
box of each of the wall's diagonals (`DiagonalRule.all`, reading through the given `BlockGetter`)
into the vanilla shape. Shapes are cached lazily per state × 4-bit diagonal mask.

Players keep the vanilla shape so the server's movement check agrees with the client; any extra
server-only shape near players means "moved wrongly" rubber-banding. The empty-context cache that
`isCollisionShapeFullBlock` and suffocation read (`BlockBehaviour.java:678`) stays vanilla.

Lithium replaces the block-collision sweep. It is unknown whether it reaches the context form for
walls; the legs below run with Lithium installed to settle that.

**Done when**, with Lithium in `run/mods`:

1. Pen G holds 10 chickens, 10 baby cows, 5 rabbits and 5 cats. After `tick sprint 24000`, an
   `execute if entity` count finds all 30 inside. The same run with the mixin disabled (S9's
   `seal_gaps false`, or a build without it) finds some outside, which proves the test can fail.
2. Items summoned with motion into a gap bounce back.
3. An arrow shot into a gap hits the corner.
4. A mineflayer bot walks into the corner where the box sits, and the log shows no `moved wrongly`.

## S8 — Pathfinding stops routing through sealed gaps — **planned**

**Build.** `mixin/WalkNodeEvaluatorMixin` hooks `isDiagonalValid(Node, Node, Node)`
(`WalkNodeEvaluator.java:167`) at `HEAD`, cancellable. When both side nodes are `FENCE` and
`DiagonalRule.connects` holds between them, it returns false. It reads states through
`currentContext.getBlockState` (`PathfindingContext.java:32`).
`AmphibiousNodeEvaluator` inherits the fix (`:10`), and fliers need none (findings). If Lithium
overwrites this method, `devserver.sh … mixins` will show it.

**Done when.** A chicken in pen G follows a bot holding wheat seeds outside, nearest a sealed gap.
It stops at the wall instead of walking into the corner again and again, and the user watches both
cases: with the mixin disabled, it jitters in the corner.

## S9 — Settings — **planned**

**Build.** Use kalic0re `config` 0.31.0 (`ModConfig.create`, `ModConfig.java:61`), shade
`rule,config,text,command`, and follow polytrains `rule/ModGameRules.java:24` as the worked example.

| Scope | Key | Kind | Default |
|---|---|---|---|
| server | `enabled` | toggle | true |
| server | `seal_gaps` | toggle (S7 and S8) | true |
| server | `view_range` | range 64–512 blocks | 128 |
| player | `show_arms` | toggle | true |

`ArmHolder.startWatching` refuses a player whose `show_arms` is false, following
`VoidDeepslateBlock.java:312`. A change re-checks the holders in that player's tracked chunks.

**Done when.**

- `/settings polymerdiagonalwalls set enabled false` removes every arm within a tick, and pen G
  leaks again; `true` restores both.
- With `view_range 64`, arms vanish past 64 blocks at 100% entity distance.
- One player's `show_arms false` hides arms for them only, and a second player still sees them.
- On a dev server without rkcore-common, `/gamerule polymerdiagonalwalls:enabled false` does the
  same.

## S10 — Fence and pane diagonals — **in progress**

**Build.** Add two families to `DiagonalRule`, each joining only what vanilla joins cardinally:

- fences through `FenceBlock.connectsTo` (`FenceBlock.java:59`), so wooden fences join each other
  and nether brick joins only nether brick (`isSameFence`, `:66`);
- panes and bars through `IronBarsBlock.attachsTo` (`IronBarsBlock.java:101`).

The endpoint rule is the walls' rule with boolean sides: all four sides facing the shared neighbours
are `false`. The tags are `polymerdiagonalwalls:diagonal_fences` (holding `#minecraft:fences`) and
`polymerdiagonalwalls:diagonal_panes` (every `IronBarsBlock`).

The arm is a block display of the owner's state with `north=true,south=true` and the other sides
`false`. It sits centred on the corner point, turned 45° and stretched along its length so its ends
hide inside both posts: 1.1875 for a fence (18.75% stretch) and 1.375 for a pane (37.5%), because a
pane post is only 2 px (see "Reference: Fence and pane geometry"). A fence's blockstate draws its
post under every state, so a fence arm shows an extra post at the corner point. That is where S11's
middle column stands, so the look and the collision agree there.

**Done when.**

- `probe` prints the expected diagonals for layouts H–L.
- In the dev client, from every angle, no bar or panel end shows outside a post.
- The user has judged the corner post on layout H and the stretched glass on layout J.

## S11 — Client collision columns — **planned**

**Build.** `collision/ShulkerColumns` gives each diagonal columns of three shulkers at scale 0.25,
fully open, 1.5 tall, with the invisible flag set. Each family places them at offsets from the
corner point along the diagonal, post corner to post corner, every box touching its neighbours
only at corners:

- **Wall:** two columns at ±0.125, between post corners at ±0.25.
- **Fence:** three columns at −0.25, 0 and +0.25, between post corners at ±0.375 (spike layout
  L12).

Each shulker rides its own empty `BlockDisplayElement`, because a free shulker snaps to the block
centre (`Shulker.java:329`).

Three things Polymer doesn't do for us:

- **Scale.** polymer-virtual-entity sends no attributes, so the holder sends a
  `ClientboundUpdateAttributesPacket` for scale 0.25 itself on `startWatching`. Check that packet's
  26.2 constructors.
- **Lid.** Seating a passenger moves it off its spawn snap. When that lands it in another block,
  the client closes the lid (`Shulker.java:337`). Spawn each shulker 0.5 below its seat so the snap
  lands in the seat's own block, or re-send the peek once it is seated, as the spike did.
- **Range.** The client ticks shulkers as mobs, so a column holder accepts a watcher only within
  `collision_range` blocks (server setting, default 8) and re-checks once a second.

Panes are 1.0 tall (`IronBarsBlock.java:30`) and their arm spans 0.875 on each axis, which 0.25
columns don't tile. Four columns of 0.21875 at ±0.109 and ±0.328 do; settle the stack height by
testing as the spike did.

`/diagonals debug columns` (op) toggles the invisible flag so F3+B can show the boxes.

The server gets nothing new for players, so it can't refuse a move over these. The spike's
rubber-banding below scale 0.28 came from the server's copy of its real shulkers, through the silent
collision check at `ServerGamePacketListenerImpl.java:1156`.

**Done when**, in a vanilla client at full size and at scales 0.5, 0.25, 0.1 and 0.0625:

- no wall, fence or pane diagonal in layouts A, E and H–J lets you through;
- a full-size player walking into a wall arm stops at its face instead of poking through;
- no wall or fence diagonal can be jumped;
- nothing rubber-bands;
- after a relog, and after teleporting away and back, every column still stops a jump.

## S12 — Slimes that ease players along the arm — **planned**

**Build.** Two virtual slimes per diagonal sit on the ground, each spanning a post corner to the
corner point:

- **Wall:** 0.25 across at offsets ±0.125, scale 0.25 / 0.52 (`EntityTypes.java:858`).
- **Fence:** 0.375 across at ±0.1875.
- **Pane:** 0.4375 across at ±0.21875.

They are invisible, and each rides an empty display so it can't drift. A slime isn't solid to the
client, but the local player pushes away from any living entity it overlaps
(`LivingEntity.java:3209` → `Entity.java:1882`). That is about 0.03 blocks a tick, enough to keep a
player sliding along the arm off the column corners. They use the same holders and range as S11.
Size-0 slimes deal no damage (`AbstractCubeMob.java:250`).

**Done when.** The user finds walking along layout I and a five-wall run in layout G with S12 as
smooth as the spike's L13, and finds that both catch on the column corners with S12 off.

## S13 — Mob collision for fences and panes — **planned**

**Build.** Extend S7's mixin to `CrossCollisionBlock.getCollisionShape`
(`CrossCollisionBlock.java:81`) with the same entity rule: no players, and nothing carrying one.
Each endpoint ORs in the parts of S11's column squares that lie in its own cell: the outer column
whole, plus the middle column's quarter. The two quarters meet at the corner point, so the chain
stays sealed. Heights match vanilla: 24 px for fences, 16 for panes. S8 already covers fences, which
are `FENCE` nodes (`WalkNodeEvaluator.java:536`). Panes are `BLOCKED` nodes
(`CrossCollisionBlock.java:91`), so vanilla never routes between them.

**Done when.** Layout L holds S7's animals through `tick sprint 24000`, items bounce off a fence
gap, and an arrow hits one.

## S14 — PPB and Enderscape walls, fences and bars — **planned**

**Build.** The rule already admits PPB's and Enderscape's diagonals. Every one is a vanilla
`WallBlock`, `FenceBlock` or `IronBarsBlock`, in the vanilla tags; Enderscape's etched magnia walls
subclass `WallBlock` only to add a polarity. What changes is the arm. When an owner's Polymer
stand-in differs from its own state, the arm is drawn with `ItemDisplayElement`s instead of a block
display:

- **Parts.** One element per side part of the owner's blockstate for the arm state:
  `north=low,south=low,up=false` for a wall, `north=true,south=true` for a fence or bars. Read the
  parts the way the mods' overlays do, and leave out the post part, so modded fence arms have no
  extra post.
- **Model.** Each element shows an item whose `ITEM_MODEL` is the mod's bridged model,
  `<namespace>:-/block/<model>`, such as `enderscape:-/block/mirestone_wall_side`. The pack
  already carries these, so the mod ships no item definitions. Use the plain `_side` model, not
  the overlays' pre-rotated `_uvlock_` copies; uvlock means nothing on a diagonal.
- **Orientation.** Context `NONE` and yaw 180, as the overlays use (ppb `BlockStateModel.java:63`),
  with the 45° transform on top.
- **View range.** Match the owner's overlay: 3.0 for PPB, 100 for enderscape-patch.

The models are the mods' copies grown 0.08 px on every side, so an arm matches the overlay around
it; bars aren't grown. Tall arms (S6) use the `_side_tall` models. Survival requires the pack
(`config/polymer/auto-host.json`, `required: true`), so every watcher has these models. A server
that doesn't require the pack would need a no-pack fallback, which isn't planned.

Collision needs nothing new. The columns follow the stand-in's vanilla geometry, which is what the
client collides with. S7's and S13's mixins see the real modded state for mobs: Polymer swaps in
the stand-in only for a `ServerPlayer` context (`BlockStateBaseMixin.java:29`). Its other collision
hook never runs for these classes, because they override `getCollisionShape` without calling super
(`BlockBehaviourMixin.java:34`, `WallBlock.java:96`, `CrossCollisionBlock.java:81`). Keep every
added box inside the cell's 0..1 footprint. `largeCollisionShape` comes only from the cached
empty-context shape, and `BlockCollisions` skips neighbouring cells without it
(`BlockBehaviour.java:931`, `BlockCollisions.java:90`).

The dev server needs survival's mod set, copied from `data/mods`:

- the RKC Polymer bundle, repacked as the chest worktree did;
- `polymer-patch-bundle` and `enderscape-polymer-patch`;
- Enderscape and the PPB-covered mods, with their dependencies.

It also needs `data/polymer/cached_client_jars/`, which ppb's wood sounds read on first boot.

**Done when.** On that dev server, these diagonal pairs: a terrestria volcanic rock wall, an
Enderscape mirestone wall, a terrestria redwood fence, a woods_and_mires pine fence, an
Enderscape veiled fence and `shadoline_bars`. Each one:

- shows the mod's own texture on the arm, with no sandstone or birch anywhere;
- meets the mod's overlay without a seam, and a modded fence arm shows no corner post;
- blocks a player at every scale, as S11 does for vanilla blocks.

## S15 — Load and editor check — **planned**

**Build.** Nothing new unless the numbers ask for it. The stress layout is a checkerboard of walls
16 chunks square: every wall has four diagonal partners, so each owns two arms, which comes to 256
arms per chunk layer. Profile with spark `--save-to-file` and decode locally, never upload (see
`scripts/loadtest/README.md`). Measure:

- drain time per wall-bearing chunk;
- holders and arms;
- the worst tick on a 2000-block teleport into the lattice;
- dev-client FPS there (the user);
- the virtual entities a nearby watcher receives: 17 per wall diagonal (6 shulkers, 2 slimes,
  their 8 vehicles and the arm), and 23 per fence diagonal (9 shulkers, 2 slimes, 11 vehicles and
  the arm), measured in the wall lattice and in a fence lattice.

Then check editors. WorldEdit `//set`, `//paste` of a diagonal schematic and `//undo` should update
arms through the S4 hook. Axiom 6.0.3's write path is unknown, so build in it and record whether
arms follow or only refresh on chunk reload.

**Done when.**

- The numbers above are recorded in As built.
- The drain causes no tick over 50 ms on the teleport. If it does, cap the drain per tick and
  measure again.
- WorldEdit edits update arms within a tick.
- Axiom's behaviour is recorded.

## S16 — Live deploy — **planned**

**Build.** Release 0.1.0, and deploy only after the user names the go-ahead. Copy the jar into
survival's `data/mods/` under a dot-name, `mv` it into place, then run `scripts/restart.sh survival
"…"`. Nothing persists, so rollback is removing the jar.

**Done when.**

- `docker compose logs survival 2>&1 | grep -icE 'Mixin apply failed|InvalidInjectionException'`
  prints 0.
- `rcon-cli "diagonals stats"` counts holders after a player visits a walled village.
- The user sees arms at a known diagonal wall.

## Open decisions

- **Mixed-material arms.** Default is one display in the owner's material. The alternative is two
  half arms, one per endpoint's material, at twice the entity count. Settled by looking at layout E
  after S3.
- **Default view range and `collision_range`.** Settled by the S15 numbers.
- **Cross-family diagonals**, such as a wall to iron bars or a fence to a fence gate, modded gates
  included. Default: same family only. Settled by asking after S10.
- **The fence arm's post.** If the extra post at the corner point looks wrong after S10, vanilla
  fences can take S14's item-display path instead, with the mod's own item definitions pointing at
  `minecraft:block/<wood>_fence_side`. Survival requires the pack, so that reaches every survival
  player. Settled by the user's look at layout H.
- **Lobby install.** The lobby is a void world. Settled by the user before S16.

## Reference: Wall geometry

Model (26.2 client jar):

- `template_wall_post`: `[4,0,4]–[12,16,12]`.
- `template_wall_side`: `[5,0,0]–[11,14,8]`, faces down, up, north, west and east; it has no south
  face. `template_wall_side_tall` runs to y 16.
- Sides use `uvlock: true` in every wall blockstate.

Shapes (`WallBlock.java`):

- outline: `makeShapes(16, 14)` (`:66`);
- collision: `makeShapes(24, 24)` (`:67`): post `column(8, 0, h)` (`:71`), sides 6 wide along z
  0..11 (`:73`);
- post rule at `:210–230`;
- skylight passes through unless waterlogged (`:261`).

`Block.column` centres on 8 (`Block.java:180`).

## Reference: Gap arithmetic

Posts in A and B are `[0.25,0.75]²` and `[1.25,1.75]²`. An axis-aligned box `[x,x+w]×[z,z+w]`
clears A when x ≥ 0.75 or z ≥ 0.75, and clears B when x+w ≤ 1.25 or z+w ≤ 1.25. At x = z = 0.75
both hold for w ≤ 0.5. That matches vanilla's own 0.5 at `WalkNodeEvaluator.java:172`; players at
0.6 can't pass. Fence posts (`[0.375,0.625]`) let w ≤ 0.75 through, and pane posts
(`[0.4375,0.5625]`) let w ≤ 0.875 through.

In general, two squares on the diagonal whose nearest corners are g apart on each axis let a box
through when its width is at most g. A spike column at the corner point therefore leaves
0.375 − w/2 on each side of a fence. Columns that touch corner to corner leave nothing.

Adding corner boxes `[0.75,1]²` in A and `[1,1.25]²` in B changes the clearances to x ≥ 1 or z ≥ 1,
and x+w ≤ 1 or z+w ≤ 1. The feasible regions are {x ≥ 1, z ≤ 1−w} and {z ≥ 1, x ≤ 1−w}. For any
w > 0 they are disjoint closed sets, so no path joins them. A corner box's far corners lie
0.25/√2 ≈ 0.177 from the arm's centre line, inside its 0.1875 half-width.

## Reference: Arm geometry

The centres are √2 ≈ 1.414 apart. A post's corner sits 0.25·√2 ≈ 0.354 from its centre along the
diagonal. The arm's corners are 0.1875 to each side, so its end must stay within
0.354 − 0.1875 ≈ 0.166 of the centre. That sets the minimum length at 1.414 − 2·0.166 ≈ 1.082. Use
1.125 (18 px): a 12.5% stretch instead of 41%.

The bar model spans x 5..11/16 and z 0..1. Map p ↦ T + R_y(θ)·S·(p − c), where:

- c = (0.5, 0, 0.5);
- S = (1 − ε, 1 − ε, 1.125);
- θ = +45° for south-east (+1, +1) and −45° for north-east (+1, −1). JOML `rotateY` sends +z to
  (sin θ, cos θ), unverified in-world (S3).

The entity sits at the A–B midpoint, nudged 1/16 toward A, so its light cell is A (a wall, never
opaque). T undoes that nudge and lifts the bar by ε, so no face is coplanar with a post's top or
bottom. `DisplayElement.setTransformation(Matrix4fc)` (`DisplayElement.java:26`) takes the matrix.
The client draws a display within viewRange × 64 × entity-distance scaling (`Display.java:403`).

## Reference: Fence and pane geometry

Models (26.2 client jar):

- `fence_post`: `[6,0,6]–[10,16,10]`.
- `fence_side`: bars `[7,6,0]–[9,9,9]` and `[7,12,0]–[9,15,9]`, 2 px wide.
- `template_glass_pane_post`: `[7,0,7]–[9,16,9]`; `template_glass_pane_side`: `[7,0,0]–[9,16,7]`.

Collision: fences are `CrossCollisionBlock(4, 16, 4, 16, 24)` (`FenceBlock.java:37`), and panes
and bars are `(2, 16, 2, 16, 16)` (`IronBarsBlock.java:30`). The outline arms are full-height slabs
(`CrossCollisionBlock.java:46`).

Arm length: a fence post's corner sits 0.177 from its centre along the diagonal and a bar is 0.0625
to each side, so a bar end must stay within 0.114 of the centre. That gives a minimum length of
1.186; use 1.1875. A pane post's corner sits 0.088 from its centre, so a panel end must stay within
0.026, giving a minimum of 1.362; use 1.375.

Post corners from the corner point, on each axis: walls ±0.25, fences ±0.375, panes ±0.4375.

## Reference: Collision spike

A vanilla 26.2 server with no mods ran on 2026-10-18. Each layout used real shulkers riding empty
block displays; the user tested in a vanilla client and reported each result.

| Layout | Collision | Result |
|---|---|---|
| L1, L5 | fence or pane pair, nothing added | walked through |
| L2 | one 0.25 column, three high, at the corner | blocked; jumpable until the lids were fixed |
| L3 | one 0.1875 column, four high | blocked |
| L4 | panes, one 0.25 column, two high | blocked |
| L8 | seven 0.0625 columns, twelve high, gap slimes | rubber-banded at 0.35, let 0.1 through |
| L10 | fence run, 0.25 columns, slimes in the gaps | smooth; blocked at 0.5, let ≤ 0.35 through |
| L12, L13 | three 0.25 columns, three high, two ground slimes | blocked at every scale; chosen |

L12 rubber-banded at scale 0.283 and below, and the server logged no `moved wrongly`. That points to
the silent check at `ServerGamePacketListenerImpl.java:1156` against the server's copy of the
shulkers, which virtual entities don't have.

Behaviour seen in the spike:

- A passenger sits exactly at a display vehicle's position.
- `summon` centres whole-number x and z on the block; the spike wrote `206.0`.
- The server re-centres a growing shulker box (`Entity.java:3397`), which closed the lids of
  shulkers resting on whole-number y. That code is server-only, so it doesn't apply to virtual
  shulkers.
- Real slimes need a difficulty above Peaceful (`EntityTypes.java:862`); virtual ones don't.
- The crosshair stops on the columns, as a vanilla fence's outline does, but a click there is an
  entity click, so the fence can't be mined through a column.

## Reference: Modded blocks on survival

Verified on 2026-10-18 against the live jars. Those are ppb `rkc/26.2` `1758312` and
enderscape-patch `rkc/26.2` `237e837`, with Enderscape 3.0.2 and factorytools 0.11.2, the copy
nested in both patch jars.

| Source | Walls | Fences | Gates | Bars and panes |
|---|---|---|---|---|
| PPB: terrestria, traverse, cinderscapes, woods_and_mires | 6 | 13 | 13 | 0 |
| enderscape-patch: Enderscape | 21 | 3 | 3 | 1 |

The PPB walls are terrestria's volcanic rock family. Its fences come from terrestria (9), traverse,
cinderscapes (2) and woods_and_mires. Blockus is not installed. The Enderscape bars are
`shadoline_bars`, and its fences are veiled, celestial and murublight (`#minecraft:wooden_fences`).
Terraform's small logs have connection properties but aren't `CrossCollisionBlock`s, so they are
out of scope.

**Stand-ins:**

- Walls: `sandstone_wall` (ppb `PolymerBlockHelper.java:76`, `StateCopyFactoryBlock.java:30`;
  enderscape-patch `StateCopyFactoryBlock.java:32`).
- Fences: `birch_fence` (ppb `PolymerBlockHelper.java:78`).
- Gates: `birch_fence_gate`.
- Bars: an empty-model waxed copper bars state (ppb `BarsPolymerBlock`,
  `PolymerBlockHelper.java:89`; enderscape-patch `MapPolymerBlock.java:36`).

The patch copies the vanilla block's properties onto the stand-in, and the choice doesn't depend on
the player. Enderscape's mapping is in `EnderscapeBlocksMixin.java:58–93`. enderscape-patch shares
no code with ppb.

**Overlays:**

- PPB uses its own `BlockStateModel.midRange().shifty()` (`BlockStateModel.java:63–95`, view range
  3.0). enderscape-patch uses factorytools `ShiftyBlockStateModel.longRange` (view range 100).
- Both draw one item display per multipart part: a `trial_key` whose `ITEM_MODEL` is the bridged
  model, with context `NONE` and yaw 180.
- A uvlock part uses a pre-rotated `<model>_uvlock_<x>_<y>` copy.
- "shifty" is a per-position jitter of at most 0.0005 blocks against z-fighting, with no scaling.
- Nothing filters watchers by pack.

**Pack:**

- Both mods' models are in the pack under their own namespace (ppb `ResourceHelper.java:37`,
  `EnderscapePolymerPatch.java:55`).
- They are re-parented to copies grown 0.08 px on every side (ppb `ResourcePackGenerator.java:84`;
  enderscape-patch `ResourcePackGenerator.java:156`). Bars aren't grown.
- Every model is bridged as an item, `<namespace>:-/block/<model>` (`ResourcePackExtras.java:117`).
  The live pack has 5575 such entries.

The RKC Polymer fork changes none of this.

## Reference: Polymer attachment lifecycle

- `ChunkAttachment`'s constructor attaches straight away (`ChunkAttachment.java:29–39`). A
  non-ticking one never calls `holder.tick()` (`:89–94`).
- `BlockBoundAttachment.of` returns null unless the block is a `BlockWithElementHolder`
  (`BlockBoundAttachment.java:36–44`). Walls aren't, hence `ChunkAttachment` plus our own index.
- Chunk unload (`setLoaded(false)`) destroys every holder in the chunk (`WorldChunkMixin.java:111`).
- Watching starts on `sendChunk` and stops on `dropChunk` (`ChunkDataSenderMixin.java:21`, `:28`).
