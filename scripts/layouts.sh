#!/usr/bin/env bash
# Builds and checks the diagonal-walls plan's test layouts on a running dev server, over its rcon.
#
#   scripts/layouts.sh build [layout...]   # clear the strip, forceload it, build (default: all)
#   scripts/layouts.sh check [layout...]   # probe every block of each layout, compare with plan
#   scripts/layouts.sh f-split             # layout F with only the owner's chunk loaded, then both
#   scripts/layouts.sh stats               # rebuild the strip, compare stats with a full probe
#   scripts/layouts.sh list                # each layout's blocks and expected diagonal count
#
# Live updates and the chunk lifecycle (S4, S5), on a server built with `build`:
#
#   scripts/layouts.sh live                # place, break, L, piston and TNT, each sampled per tick
#   scripts/layouts.sh timed <x> <z> <cmd> # one command, then arms and the block at x z per tick
#   scripts/layouts.sh loop [rounds]       # break and replace walls over one rcon connection
#   scripts/layouts.sh unload | reload     # drop every forced chunk until stats reads 0, or force
#                                          # the strip again until it reads the layout total
#   scripts/layouts.sh f-first <owner|partner>   # load one of F's chunks, then the other
#   scripts/layouts.sh f-return            # F's partner chunk demoted and promoted again
#   scripts/layouts.sh drawn               # arms drawn against the diagonals the rule sees
#
# Modded walls, fences and bars (S14), on a dev server with survival's mod set:
#
#   scripts/layouts.sh modded build        # clear the modded row, forceload it, build S-Y
#   scripts/layouts.sh modded check        # probe each, then each owner's arm elements and models
#   scripts/layouts.sh modded sweep        # every modded wall, fence and bars in run/mods as a pair
#   scripts/layouts.sh modded clear        # empty the row again, so stats and drawn see A-R only
#
# S15's load lattices, a checkerboard of one block in one layer, so every block owns two arms:
#
#   scripts/layouts.sh lattice build <wall|fence>   # force, clear and build it, then unforce it
#   scripts/layouts.sh lattice clear <wall|fence>   # force, empty and unforce it again
#   scripts/layouts.sh lattice expect <wall|fence>  # its origin, size, block, holders and arms
#
# MOD_DIR (default: this checkout) and, for a multi-version build, DEV_MC pick the dev server.
# Every layout stands on the seeded flat world's surface, y=-53, in the chunk row z 96..111 at
# x >= 100, away from spawn.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="${MOD_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"

# The workspace's devserver.sh sits some levels up: one more for a worktree than the checkout.
DEVSERVER=""
dir="$MOD_DIR"
while [[ "$dir" != "/" ]]; do
    dir="$(dirname "$dir")"
    if [[ -x "$dir/scripts/devserver.sh" ]]; then
        DEVSERVER="$dir/scripts/devserver.sh"
        break
    fi
done
[[ -n "$DEVSERVER" ]] || { echo "no scripts/devserver.sh above $MOD_DIR" >&2; exit 2; }

# A Stonecutter checkout runs one dev server per version, under versions/<v>/run. DEV_MC picks it,
# as devserver.sh --mc does, and defaults to the committed version.
RUN_DIR="$MOD_DIR/run"
DEVSERVER_ARGS=()
if [[ -f "$MOD_DIR/stonecutter.gradle" ]]; then
    MC="${DEV_MC:-$(grep -m1 '^stonecutter_vcs_version=' "$MOD_DIR/gradle.properties" | cut -d= -f2- | tr -d '[:space:]')}"
    RUN_DIR="$MOD_DIR/versions/$MC/run"
    DEVSERVER_ARGS=(--mc "$MC")
fi

Y=-53
# Strip every layout lives in; later layouts extend it eastward up to x 191.
STRIP=(96 96 191 111)

ORDER=(A B C D E F G H I J K L M N O P Q R)
declare -A EXPECT EXPECT_TALL DESCRIBE BLOCKS

# Each BLOCKS entry is "x z block [dy]" separated by ';', dy blocks above y (default 0).
# EXPECT_TALL, default 0, is how many of a layout's diagonals have tall arms.
DESCRIBE[A]="two cobblestone walls corner to corner"
BLOCKS[A]="100 100 cobblestone_wall;101 101 cobblestone_wall"
EXPECT[A]=1

DESCRIBE[B]="A plus a wall on one shared neighbour (vanilla L)"
BLOCKS[B]="104 100 cobblestone_wall;105 101 cobblestone_wall;105 100 cobblestone_wall"
EXPECT[B]=0

DESCRIBE[C]="2x2 ring of walls"
BLOCKS[C]="108 100 cobblestone_wall;109 100 cobblestone_wall;108 101 cobblestone_wall"
BLOCKS[C]+=";109 101 cobblestone_wall"
EXPECT[C]=0

DESCRIBE[D]="A plus a stone block on one shared neighbour"
BLOCKS[D]="116 100 cobblestone_wall;117 101 cobblestone_wall;117 100 stone"
EXPECT[D]=0

DESCRIBE[E]="cobblestone and brick walls corner to corner"
BLOCKS[E]="120 100 cobblestone_wall;121 101 brick_wall"
EXPECT[E]=1

# Owner in chunk x 9, partner in chunk x 10, running north-east.
DESCRIBE[F]="A across the chunk border at x 159|160"
BLOCKS[F]="159 101 cobblestone_wall;160 100 cobblestone_wall"
EXPECT[F]=1

# Diamond centred on 136, 104 with corners four blocks out: four runs of five walls, sharing
# their corners, so 16 walls and 16 diagonals.
DESCRIBE[G]="diamond pen: four diagonal runs of five walls"
g=""
for i in 0 1 2 3 4; do
    g+="$((132 + i)) $((104 - i)) cobblestone_wall;"   # west corner to north corner
    g+="$((136 + i)) $((100 + i)) cobblestone_wall;"   # north corner to east corner
    g+="$((140 - i)) $((104 + i)) cobblestone_wall;"   # east corner to south corner
    g+="$((136 - i)) $((108 - i)) cobblestone_wall;"   # south corner to west corner
done
BLOCKS[G]="${g%;}"
EXPECT[G]=16

# H-L: fences and panes, in chunks 9 to 11 beside F.
DESCRIBE[H]="two oak fences corner to corner"
BLOCKS[H]="144 100 oak_fence;145 101 oak_fence"
EXPECT[H]=1

DESCRIBE[I]="six oak fences in a diagonal run"
i=""
for k in 0 1 2 3 4 5; do
    i+="$((148 + k)) $((100 + k)) oak_fence;"
done
BLOCKS[I]="${i%;}"
EXPECT[I]=5

DESCRIBE[J]="two glass panes corner to corner"
BLOCKS[J]="164 100 glass_pane;165 101 glass_pane"
EXPECT[J]=1

DESCRIBE[K]="an oak fence and a nether brick fence corner to corner"
BLOCKS[K]="156 100 oak_fence;157 101 nether_brick_fence"
EXPECT[K]=0

# G's diamond in oak fences, centred on 180, 104 (x 176..184, all in chunk 11).
DESCRIBE[L]="G built from oak fences"
l=""
for k in 0 1 2 3 4; do
    l+="$((176 + k)) $((104 - k)) oak_fence;"
    l+="$((180 + k)) $((100 + k)) oak_fence;"
    l+="$((184 - k)) $((104 + k)) oak_fence;"
    l+="$((180 - k)) $((108 - k)) oak_fence;"
done
BLOCKS[L]="${l%;}"
EXPECT[L]=16

# M-R: S6's tall arms, in the row z 108..110.
DESCRIBE[M]="a three-wall diagonal run, two high; the lower two arms tall"
m=""
for k in 0 1 2; do
    m+="$((144 + k)) $((108 + k)) cobblestone_wall;"
    m+="$((144 + k)) $((108 + k)) cobblestone_wall 1;"
done
BLOCKS[M]="${m%;}"
EXPECT[M]=4
EXPECT_TALL[M]=2

DESCRIBE[N]="A under a 2x2 bottom-slab ceiling; tall"
BLOCKS[N]="150 108 cobblestone_wall;151 109 cobblestone_wall"
BLOCKS[N]+=";150 108 smooth_stone_slab 1;151 108 smooth_stone_slab 1"
BLOCKS[N]+=";150 109 smooth_stone_slab 1;151 109 smooth_stone_slab 1"
EXPECT[N]=1
EXPECT_TALL[N]=1

DESCRIBE[O]="A with stone above both walls; tall"
BLOCKS[O]="154 108 cobblestone_wall;155 109 cobblestone_wall;154 108 stone 1;155 109 stone 1"
EXPECT[O]=1
EXPECT_TALL[O]=1

DESCRIBE[P]="A with a torch on each wall; low"
BLOCKS[P]="166 108 cobblestone_wall;167 109 cobblestone_wall;166 108 torch 1;167 109 torch 1"
EXPECT[P]=1

DESCRIBE[Q]="A with a bottom slab above the owner only; low"
BLOCKS[Q]="170 108 cobblestone_wall;171 109 cobblestone_wall;170 108 smooth_stone_slab 1"
EXPECT[Q]=1

DESCRIBE[R]="A with a top slab above each wall; low"
BLOCKS[R]="188 100 cobblestone_wall;189 101 cobblestone_wall"
BLOCKS[R]+=";188 100 smooth_stone_slab[type=top] 1;189 101 smooth_stone_slab[type=top] 1"
EXPECT[R]=1

# S-Y: S14's modded diagonals in the row z 116..118, for a dev server with survival's mod set.
# Not in ORDER, since a server without those mods can't place them. Each lists the item models its
# arm's elements show and the view range of the patch overlay they match.
MODDED_ORDER=(S T U V W X Y)
MODDED_STRIP=(96 112 191 127)
declare -A MODELS RANGE

DESCRIBE[S]="two terrestria volcanic rock walls corner to corner"
BLOCKS[S]="100 116 terrestria:volcanic_rock_wall;101 117 terrestria:volcanic_rock_wall"
EXPECT[S]=1
MODELS[S]="terrestria:-/block/volcanic_rock_wall_side_uvlock_0_0"
MODELS[S]+=" terrestria:-/block/volcanic_rock_wall_side_uvlock_0_180"
RANGE[S]=3.0

DESCRIBE[T]="two Enderscape mirestone walls corner to corner"
BLOCKS[T]="104 116 enderscape:mirestone_wall;105 117 enderscape:mirestone_wall"
EXPECT[T]=1
MODELS[T]="enderscape:-/block/mirestone_wall_side_uvlock_0_0"
MODELS[T]+=" enderscape:-/block/mirestone_wall_side_uvlock_0_180"
RANGE[T]=100.0

DESCRIBE[U]="two terrestria redwood fences corner to corner"
BLOCKS[U]="108 116 terrestria:redwood_fence;109 117 terrestria:redwood_fence"
EXPECT[U]=1
MODELS[U]="terrestria:-/block/redwood_fence_side_uvlock_0_0"
MODELS[U]+=" terrestria:-/block/redwood_fence_side_uvlock_0_180"
RANGE[U]=3.0

DESCRIBE[V]="two woods_and_mires pine fences corner to corner"
BLOCKS[V]="112 116 woods_and_mires:pine_fence;113 117 woods_and_mires:pine_fence"
EXPECT[V]=1
MODELS[V]="woods_and_mires:-/block/pine_fence_side_uvlock_0_0"
MODELS[V]+=" woods_and_mires:-/block/pine_fence_side_uvlock_0_180"
RANGE[V]=3.0

DESCRIBE[W]="two Enderscape veiled fences corner to corner"
BLOCKS[W]="116 116 enderscape:veiled_fence;117 117 enderscape:veiled_fence"
EXPECT[W]=1
MODELS[W]="enderscape:-/block/veiled_fence_side_uvlock_0_0"
MODELS[W]+=" enderscape:-/block/veiled_fence_side_uvlock_0_180"
RANGE[W]=100.0

DESCRIBE[X]="two Enderscape shadoline bars corner to corner"
BLOCKS[X]="120 116 enderscape:shadoline_bars;121 117 enderscape:shadoline_bars"
EXPECT[X]=1
MODELS[X]="enderscape:-/block/shadoline_bars_side enderscape:-/block/shadoline_bars_side_alt"
RANGE[X]=100.0

DESCRIBE[Y]="S with stone above both walls; tall"
BLOCKS[Y]="124 116 terrestria:volcanic_rock_wall;125 117 terrestria:volcanic_rock_wall"
BLOCKS[Y]+=";124 116 minecraft:stone 1;125 117 minecraft:stone 1"
EXPECT[Y]=1
EXPECT_TALL[Y]=1
MODELS[Y]="terrestria:-/block/volcanic_rock_wall_side_tall_uvlock_0_0"
MODELS[Y]+=" terrestria:-/block/volcanic_rock_wall_side_tall_uvlock_0_180"
RANGE[Y]=3.0

rc() {
    "$DEVSERVER" "$MOD_DIR" "${DEVSERVER_ARGS[@]}" rcon "$@"
}

# Every layout's diagonals added up, which is what stats reads with all of them loaded.
layout_total() {
    local layout total=0
    for layout in "${ORDER[@]}"; do
        total=$((total + EXPECT[$layout]))
    done
    echo "$total"
}

# The total arm count stats prints.
stats_arms() {
    rc "diagonals stats" | grep -oE 'Total: [0-9]+ holders, [0-9]+ arms' | grep -oE '[0-9]+ arms' \
        | grep -oE '[0-9]+'
}

# Diagonals the rule sees across every layout block, in loaded chunks only.
probe_total() {
    local layout x z block dy total=0
    for layout in "${ORDER[@]}"; do
        while read -r x z block dy; do
            total=$((total + $(count_diagonals "$(probe "$x" $((Y + ${dy:-0})) "$z")")))
        done < <(blocks_of "$layout")
    done
    echo "$total"
}

# Passes when the arms drawn are exactly the diagonals the rule sees: none missing, none stale.
check_drawn() {
    local drawn seen
    drawn="$(stats_arms)"
    seen="$(probe_total)"
    if [[ "$drawn" == "$seen" ]]; then
        echo "ok   $drawn arms drawn, $seen diagonals seen"
    else
        echo "FAIL $drawn arms drawn, $seen diagonals seen"
        return 1
    fi
}

# Polls stats until it reads $1 arms, for up to $2 seconds (default 120); prints the wait.
await_arms() {
    local want="$1" limit="${2:-120}" got start=$SECONDS
    while (( SECONDS - start < limit )); do
        got="$(stats_arms)"
        if [[ "$got" == "$want" ]]; then
            echo "stats reads $want arms after $((SECONDS - start)) s"
            return 0
        fi
        sleep 1
    done
    echo "timed out: stats reads ${got:-?} arms, want $want" >&2
    return 1
}

# Unique "x z block [dy]" entries of one layout, one per line; a block sorts before what is on it.
blocks_of() {
    tr ';' '\n' <<<"${BLOCKS[$1]}" | awk 'NF' | sort -u
}

# Probe output for the block at x, y, z.
probe() {
    rc "diagonals probe $1 $2 $3"
}

# Diagonals, then tall ones, that one probe printed; rcon joins its lines without a separator.
count_diagonals() {
    grep -oE '(south|north)-east to' <<<"$1" | wc -l
}

count_tall() {
    grep -oE 'tall arm' <<<"$1" | wc -l
}

cmd_build() {
    local layouts=("$@")
    (( ${#layouts[@]} )) || layouts=("${ORDER[@]}")
    rc "forceload add ${STRIP[0]} ${STRIP[1]} ${STRIP[2]} ${STRIP[3]}" >/dev/null
    if (( $# == 0 )); then
        rc "fill ${STRIP[0]} $Y ${STRIP[1]} ${STRIP[2]} $((Y + 3)) ${STRIP[3]} air" >/dev/null
    fi
    local layout x z block dy
    for layout in "${layouts[@]}"; do
        while read -r x z block dy; do
            rc "setblock $x $((Y + ${dy:-0})) $z minecraft:$block" >/dev/null
        done < <(blocks_of "$layout")
        echo "built $layout: ${DESCRIBE[$layout]}"
    done
}

cmd_check() {
    local layouts=("$@")
    (( ${#layouts[@]} )) || layouts=("${ORDER[@]}")
    local layout x z block dy out total tall owned want_tall failed=0
    PROBED_DIAGONALS=0
    PROBED_OWNERS=0
    for layout in "${layouts[@]}"; do
        total=0
        tall=0
        while read -r x z block dy; do
            out="$(probe "$x" $((Y + ${dy:-0})) "$z")"
            owned=$(count_diagonals "$out")
            total=$((total + owned))
            tall=$((tall + $(count_tall "$out")))
            (( owned > 0 )) && PROBED_OWNERS=$((PROBED_OWNERS + 1))
        done < <(blocks_of "$layout")
        PROBED_DIAGONALS=$((PROBED_DIAGONALS + total))
        want_tall=${EXPECT_TALL[$layout]:-0}
        if (( total == ${EXPECT[$layout]} && tall == want_tall )); then
            echo "ok   $layout: $total diagonal(s), $tall tall"
        else
            echo "FAIL $layout: $total diagonal(s), $tall tall; want ${EXPECT[$layout]}, $want_tall"
            failed=1
        fi
    done
    return $failed
}

# Rebuilds every arm in the strip, then checks that stats counts one holder per probed owner and
# one arm per probed diagonal. Holds only while nothing but the layouts stands in the dimension.
cmd_stats() {
    rc "execute positioned 144 $Y 104 run diagonals rebuild 64"
    echo
    cmd_check >/dev/null
    local out holders arms
    out="$(rc "diagonals stats")"
    echo "$out"
    holders=$(grep -oE 'Total: [0-9]+ holders' <<<"$out" | grep -oE '[0-9]+')
    arms=$(grep -oE 'holders, [0-9]+ arms$' <<<"$out" | grep -oE '[0-9]+')
    if [[ "$holders" == "$PROBED_OWNERS" && "$arms" == "$PROBED_DIAGONALS" ]]; then
        echo "ok   stats: $holders holders, $arms arms"
    else
        echo "FAIL stats: $holders holders, $arms arms; probe found $PROBED_OWNERS owners," \
            "$PROBED_DIAGONALS diagonals"
        return 1
    fi
}

# Polls probe on F's owner until its output matches $1, for up to 60 s.
await_f() {
    local want="$1" out i
    for ((i = 0; i < 30; i++)); do
        out="$(rc "diagonals probe 159 $Y 101")"
        if grep -q "$want" <<<"$out"; then
            echo "$out"
            return 0
        fi
        sleep 2
    done
    echo "timed out waiting for '$want'; last: $out" >&2
    return 1
}

# A forced chunk is entity-ticking (level 31): chunks two away are fully loaded (33), three away
# are not (34). Forcing chunk x 7 therefore loads F's owner chunk (x 9) and not its partner (x 10).
cmd_f_split() {
    rc "forceload remove all" >/dev/null
    rc "forceload add 112 96" >/dev/null
    echo "owner chunk only:"
    await_f "owns no diagonals" || return 1
    rc "forceload add 160 96" >/dev/null
    echo "both chunks:"
    await_f "owns 1" || return 1
    rc "forceload add ${STRIP[0]} ${STRIP[1]} ${STRIP[2]} ${STRIP[3]}" >/dev/null
}

# A datapack in the dev world whose functions sample stats once per tick, at the start of each
# tick's scheduled functions: sample k runs before tick k's drain, so it sees drains 1..k-1.
install_pack() {
    local pack="$RUN_DIR/world/datapacks/pdwtest"
    local fn="$pack/data/pdwtest/function"
    [[ -f "$fn/watch_sample.mcfunction" ]] && return 0
    mkdir -p "$fn"
    printf '%s\n' '{"pack":{"description":"diagonal-walls tick sampling",' \
        '"min_format":[107,1],"max_format":107}}' | tr -d '\n' >"$pack/pack.mcmeta"
    # Args: cmd, x, y, z. Sample 0 is taken straight after cmd, in the same instant.
    cat >"$fn/timed.mcfunction" <<'EOF'
scoreboard objectives add pdwtest dummy
data modify storage pdwtest:t arms set value []
data modify storage pdwtest:t walls set value []
$data modify storage pdwtest:t pos set value {x:$(x),y:$(y),z:$(z)}
execute store result storage pdwtest:t start int 1 run diagonals stats
$$(cmd)
scoreboard players set #n pdwtest 0
function pdwtest:take
schedule function pdwtest:sample 1t replace
EOF
    cat >"$fn/sample.mcfunction" <<'EOF'
scoreboard players add #n pdwtest 1
function pdwtest:take
execute if score #n pdwtest matches ..6 run schedule function pdwtest:sample 1t replace
EOF
    # Args: x, y, z. As timed, but for a change made elsewhere: 40 samples, one per tick.
    cat >"$fn/watch.mcfunction" <<'EOF'
scoreboard objectives add pdwtest dummy
data modify storage pdwtest:t arms set value []
data modify storage pdwtest:t walls set value []
$data modify storage pdwtest:t pos set value {x:$(x),y:$(y),z:$(z)}
execute store result storage pdwtest:t start int 1 run diagonals stats
scoreboard players set #n pdwtest 0
function pdwtest:take
schedule function pdwtest:watch_sample 1t replace
EOF
    cat >"$fn/watch_sample.mcfunction" <<'EOF'
scoreboard players add #n pdwtest 1
function pdwtest:take
execute if score #n pdwtest matches ..39 run schedule function pdwtest:watch_sample 1t replace
EOF
    cat >"$fn/take.mcfunction" <<'EOF'
data modify storage pdwtest:t arms append value 0
execute store result storage pdwtest:t arms[-1] int 1 run diagonals stats
data modify storage pdwtest:t walls append value 0
execute store result storage pdwtest:t walls[-1] int 1 \
    run function pdwtest:wall with storage pdwtest:t pos
EOF
    cat >"$fn/wall.mcfunction" <<'EOF'
$return run execute if block $(x) $(y) $(z) #minecraft:walls
EOF
    # Drops every ticket and forces chunk x 7 in one instant, so F's partner chunk is demoted from
    # FULL without ever unloading.
    cat >"$fn/demote_f_partner.mcfunction" <<'EOF'
forceload remove all
forceload add 112 96
EOF
    rc "reload" >/dev/null
    sleep 2
}

# Runs $3 inside the sampling function, watching the block at $1 $Y $2. Prints the arm counts and
# whether a wall stands there: sample 0 straight after the command, then one per tick.
cmd_timed() {
    local x="$1" z="$2" cmd="$3"
    install_pack
    rc "function pdwtest:timed {cmd:\"$cmd\",x:$x,y:$Y,z:$z}" >/dev/null
    sleep 1
    rc "data get storage pdwtest:t" | grep -oE '(start|arms|walls): (\[[^]]*\]|[0-9]+)' \
        | tr '\n' ' '
    echo
}

# Arm counts of the last timed run, sample 0 first.
timed_arms() {
    rc "data get storage pdwtest:t arms" | grep -oE '\[[^]]*\]' | tr -d '[] ' | tr ',' ' '
}

# Checks one timed step: the arm count must move from $1 to $2, staying at $1 through the tick in
# which the wall changed and reading $2 from the first sample after that tick's drain.
expect_step() {
    local label="$1" from="$2" to="$3" x="$4" z="$5" cmd="$6"
    local line arms walls i changed=-1 settled=-1
    line="$(cmd_timed "$x" "$z" "$cmd")"
    read -ra arms <<<"$(timed_arms)"
    read -ra walls <<<"$(rc "data get storage pdwtest:t walls" | grep -oE '\[[^]]*\]' \
        | tr -d '[] ' | tr ',' ' ')"
    for ((i = 1; i < ${#walls[@]}; i++)); do
        if (( changed < 0 && walls[i] != walls[0] )); then
            changed=$i
        fi
    done
    for ((i = 0; i < ${#arms[@]}; i++)); do
        if (( settled < 0 && arms[i] == to )); then
            settled=$i
        fi
    done
    # A command changes the wall before sample 0, so its first drain shows at sample 2; a change
    # made inside tick k (piston, TNT) shows at the sample that also first sees the wall change.
    local want=2
    (( changed > 0 )) && want=$changed
    if (( arms[0] == from && settled == want && arms[${#arms[@]} - 1] == to )); then
        echo "ok   $label: $line"
    else
        echo "FAIL $label: $line(want $from -> $to at sample $want)"
        return 1
    fi
}

# Whether a wall stands at $1 $Y $2.
stands() {
    if rc "execute if block $1 $Y $2 #minecraft:walls" | grep -q passed; then
        echo standing
    else
        echo gone
    fi
}

cmd_live() {
    local base failed=0
    base="$(stats_arms)"
    echo "start: $base arms"
    # Layout A: break and replace each end, then wall the shared neighbour into vanilla's L.
    expect_step "break A's partner" "$base" $((base - 1)) 101 101 "setblock 101 $Y 101 air" \
        || failed=1
    expect_step "place A's partner" $((base - 1)) "$base" 101 101 \
        "setblock 101 $Y 101 cobblestone_wall" || failed=1
    expect_step "break A's owner" "$base" $((base - 1)) 100 100 "setblock 100 $Y 100 air" \
        || failed=1
    expect_step "place A's owner" $((base - 1)) "$base" 100 100 \
        "setblock 100 $Y 100 cobblestone_wall" || failed=1
    expect_step "wall a shared neighbour" "$base" $((base - 1)) 101 100 \
        "setblock 101 $Y 100 cobblestone_wall" || failed=1
    expect_step "unwall it" $((base - 1)) "$base" 101 100 "setblock 101 $Y 100 air" || failed=1

    # A sticky piston under an owner pushes it a block up, off its partner's level, and pulls it
    # back; the redstone block beside the piston powers it.
    rc "setblock 104 $((Y - 1)) 106 sticky_piston[facing=up]" >/dev/null
    rc "setblock 104 $Y 106 cobblestone_wall" >/dev/null
    rc "setblock 105 $Y 107 cobblestone_wall" >/dev/null
    await_arms $((base + 1)) 10 >/dev/null || { echo "FAIL piston pair never joined"; failed=1; }
    expect_step "piston pushes the owner up" $((base + 1)) "$base" 104 106 \
        "setblock 103 $((Y - 1)) 106 redstone_block" || failed=1
    expect_step "piston pulls it back" "$base" $((base + 1)) 104 106 \
        "setblock 103 $((Y - 1)) 106 smooth_stone" || failed=1
    rc "fill 104 $Y 106 105 $Y 107 air" >/dev/null
    rc "setblock 104 $((Y - 1)) 106 smooth_stone" >/dev/null

    # TNT one block west of an owner, at reduced power so the partner may survive.
    await_arms "$base" 10 >/dev/null
    rc "setblock 124 $Y 108 cobblestone_wall" >/dev/null
    rc "setblock 125 $Y 109 cobblestone_wall" >/dev/null
    await_arms $((base + 1)) 10 >/dev/null || { echo "FAIL TNT pair never joined"; failed=1; }
    expect_step "TNT destroys the owner" $((base + 1)) "$base" 124 108 \
        "summon tnt 123.5 $Y 108.5 {fuse:0s,explosion_power:2.5f}" || failed=1
    echo "after TNT: owner $(stands 124 108), partner $(stands 125 109)"
    # The seeded world is smooth stone up to Y - 1.
    rc "fill 121 $Y 105 128 $((Y + 3)) 111 air" >/dev/null
    rc "fill 121 $((Y - 4)) 105 128 $((Y - 1)) 111 smooth_stone" >/dev/null
    return $failed
}

# Breaks and replaces A's partner and one of G's walls per round over one rcon connection, with a
# random pause of up to 30 ms after each command, so some rounds land inside one tick and others
# straddle a drain at every phase. Then waits for the layout total.
cmd_loop() {
    local rounds="${1:-1000}" port password
    port="$(grep -m1 '^rcon.port=' "$RUN_DIR/server.properties" | cut -d= -f2-)"
    password="$(grep -m1 '^rcon.password=' "$RUN_DIR/server.properties" | cut -d= -f2-)"
    local g=() x z block dy
    while read -r x z block dy; do
        g+=("$x $z")
    done < <(blocks_of G)
    python3 - "$port" "$password" "$rounds" "$Y" "${g[@]}" <<'EOF'
import random, socket, struct, sys, time

port, password, rounds, y = int(sys.argv[1]), sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
g = [tuple(map(int, xz.split())) for xz in sys.argv[5:]]

def send(sock, req_id, req_type, payload):
    data = struct.pack('<ii', req_id, req_type) + payload.encode() + b'\x00\x00'
    sock.sendall(struct.pack('<i', len(data)) + data)

def recv(sock):
    head = b''
    while len(head) < 4:
        head += sock.recv(4 - len(head))
    length = struct.unpack('<i', head)[0]
    body = b''
    while len(body) < length:
        body += sock.recv(length - len(body))
    return body

s = socket.create_connection(('127.0.0.1', port), timeout=10)
send(s, 1, 3, password)
recv(s)
random.seed(1)
start = time.time()
changed = 0
for i in range(rounds):
    gx, gz = g[i % len(g)]
    for cmd in (f'setblock 101 {y} 101 air', f'setblock {gx} {y} {gz} air',
                f'setblock 101 {y} 101 cobblestone_wall',
                f'setblock {gx} {y} {gz} cobblestone_wall'):
        send(s, 2, 2, cmd)
        changed += b'Changed the block' in recv(s)
        time.sleep(random.uniform(0, 0.03))
print(f'{rounds} rounds in {time.time() - start:.1f} s; {changed} of {rounds * 4} commands '
      'changed a block')
EOF
    await_arms "$(layout_total)" 10
}

# Drops every forced chunk and waits for every layout's arms to go with them.
cmd_unload() {
    rc "forceload remove all" >/dev/null
    await_arms 0
}

# Forces the strip again and waits for every layout's arms to come back.
cmd_reload() {
    rc "forceload add ${STRIP[0]} ${STRIP[1]} ${STRIP[2]} ${STRIP[3]}" >/dev/null
    await_arms "$(layout_total)"
}

# Loads F's owner chunk or its partner chunk alone, then the other one, checking at each step that
# the arms drawn match the rule. A forced chunk is level 31, so chunk x 7 makes x 9 FULL and leaves
# x 10 out, and chunk x 12 does the opposite.
cmd_f_first() {
    local first="$1" failed=0
    cmd_unload >/dev/null || return 1
    case "$first" in
        owner)
            rc "forceload add 112 96" >/dev/null
            echo "owner chunk only:"
            await_f "owns no diagonals" || return 1
            check_drawn || failed=1
            rc "forceload add 160 96" >/dev/null
            ;;
        partner)
            rc "forceload add 192 96" >/dev/null
            echo "partner chunk only:"
            await_f "is not loaded" || return 1
            check_drawn || failed=1
            rc "forceload add 144 96" >/dev/null
            ;;
        *)
            echo "f-first wants owner or partner" >&2
            return 1
            ;;
    esac
    echo "both chunks:"
    await_f "owns 1" || return 1
    sleep 1
    check_drawn || failed=1
    rc "forceload remove all" >/dev/null
    cmd_reload || failed=1
    return $failed
}

# Demotes F's partner chunk below FULL without unloading it, refreshes the owner so its arm goes,
# then forces the partner again: only a status change says it is back, never a chunk load.
cmd_f_return() {
    install_pack
    cmd_reload >/dev/null || return 1
    rc "function pdwtest:demote_f_partner" >/dev/null
    rc "diagonals rebuild 512" >/dev/null
    echo "partner chunk demoted:"
    await_f "owns no diagonals" || return 1
    # Probe cannot read the demoted chunks x 10 and 11, whose holders stay attached, so only F's
    # arm may go.
    local drawn want=$(($(layout_total) - 1))
    drawn="$(stats_arms)"
    if [[ "$drawn" == "$want" ]]; then
        echo "ok   $drawn arms drawn, only F's gone"
    else
        echo "FAIL $drawn arms drawn, want $want"
        return 1
    fi
    rc "forceload add 160 96" >/dev/null
    echo "partner chunk FULL again:"
    await_f "owns 1" || return 1
    sleep 1
    check_drawn || return 1
    cmd_reload
}

cmd_modded_build() {
    rc "forceload add ${MODDED_STRIP[0]} ${MODDED_STRIP[1]} ${MODDED_STRIP[2]} ${MODDED_STRIP[3]}" \
        >/dev/null
    cmd_modded_clear >/dev/null
    local layout x z block dy out
    for layout in "${MODDED_ORDER[@]}"; do
        while read -r x z block dy; do
            out="$(rc "setblock $x $((Y + ${dy:-0})) $z $block")"
            [[ "$out" == Changed* ]] || echo "FAIL setblock $x $z $block: $out"
        done < <(blocks_of "$layout")
        echo "built $layout: ${DESCRIBE[$layout]}"
    done
}

# Every wall, fence, bars and pane blockstate the jars in run/mods ship outside minecraft:, by id.
modded_ids() {
    local jar
    for jar in "$RUN_DIR"/mods/*.jar; do
        unzip -Z1 "$jar" 2>/dev/null \
            | grep -oE '^assets/[a-z0-9_]+/blockstates/[a-z0-9_]+_(wall|fence|bars|pane)\.json$'
    done | grep -v '^assets/minecraft/' | sed -E 's#assets/([^/]+)/blockstates/(.*)\.json#\1:\2#' \
        | sort -u
}

# Builds each modded id as a south-east pair in the rows z 120 and 124, then checks that it owns
# one diagonal drawn only by item displays, every one of whose models the hosted pack defines.
cmd_modded_sweep() {
    rc "forceload add ${MODDED_STRIP[0]} ${MODDED_STRIP[1]} ${MODDED_STRIP[2]} ${MODDED_STRIP[3]}" \
        >/dev/null
    local pack="$RUN_DIR/polymer/resource_pack.zip" listing
    listing="$(unzip -Z1 "$pack" 2>/dev/null)" || { echo "FAIL no pack at $pack"; return 1; }
    local id x=100 z=120 out items blocks model missing failed=0 count=0
    while read -r id; do
        rc "setblock $x $Y $z $id" >/dev/null
        rc "setblock $((x + 1)) $Y $((z + 1)) $id" >/dev/null
        sleep 0.1
        out="$(rc "diagonals debug arms $x $Y $z")"
        items="$(grep -oE 'item display [^,]+' <<<"$out" | cut -d' ' -f3 | xargs)"
        blocks=$(grep -oE 'block display' <<<"$out" | wc -l)
        missing=""
        for model in $items; do
            grep -qxF "assets/${model%%:*}/items/${model#*:}.json" <<<"$listing" \
                || missing+=" $model"
        done
        if [[ -n "$items" && "$blocks" == 0 && -z "$missing" && "$out" == *"draws 1 arms"* ]]; then
            echo "ok   $id: $items"
        else
            echo "FAIL $id: $out${missing:+; not in the pack:$missing}"
            failed=1
        fi
        count=$((count + 1))
        x=$((x + 3))
        if (( x > 187 )); then
            x=100
            z=$((z + 4))
        fi
    done < <(modded_ids)
    echo "$count modded ids swept"
    return $failed
}

cmd_modded_clear() {
    rc "fill ${MODDED_STRIP[0]} $Y ${MODDED_STRIP[1]} ${MODDED_STRIP[2]} $((Y + 3))" \
        "${MODDED_STRIP[3]} air"
}

# The probe check, then each owner's arm: its elements' item models, as a set, and view ranges
# against the layout's, and every model in the pack this server hosts.
cmd_modded_check() {
    cmd_check "${MODDED_ORDER[@]}"
    local failed=$?
    local pack="$RUN_DIR/polymer/resource_pack.zip" listing
    listing="$(unzip -Z1 "$pack" 2>/dev/null)" || { echo "FAIL no pack at $pack"; return 1; }
    local layout x z block dy out models ranges want model
    for layout in "${MODDED_ORDER[@]}"; do
        read -r x z block dy < <(blocks_of "$layout" | sort -n | head -1)
        out="$(rc "diagonals debug arms $x $Y $z")"
        models="$(grep -oE 'item display [^,]+' <<<"$out" | cut -d' ' -f3 | sort | xargs)"
        ranges="$(grep -oE 'view range [0-9.]+' <<<"$out" | cut -d' ' -f3 | sort -u | xargs)"
        want="$(tr ' ' '\n' <<<"${MODELS[$layout]}" | sort | xargs)"
        if [[ "$models" == "$want" && "$ranges" == "${RANGE[$layout]}" ]]; then
            echo "ok   $layout arm: $models at view range $ranges"
        else
            echo "FAIL $layout arm: got '$models' at '$ranges'; want '$want' at ${RANGE[$layout]}"
            echo "     $out"
            failed=1
        fi
        for model in $want; do
            if ! grep -qxF "assets/${model%%:*}/items/${model#*:}.json" <<<"$listing"; then
                echo "FAIL $layout: the pack has no item definition for $model"
                failed=1
            fi
        done
    done
    return $failed
}

# S15's lattices: origin x and z, side in blocks, block. The wall one is chunks 32..47 square, the
# fence one chunks 64..67 by 32..35; neither is in sight of the other at view distance 10.
declare -A LATTICE
LATTICE[wall]="512 512 256 cobblestone_wall"
LATTICE[fence]="1024 512 64 oak_fence"

# Prints "x0 z0 size block" for lattice $1, or fails for an unknown name.
lattice_of() {
    [[ -n "${LATTICE[${1:-}]:-}" ]] || { echo "usage: lattice <build|clear|expect> <wall|fence>" >&2
        return 1; }
    echo "${LATTICE[$1]}"
}

# Holders and arms a lattice of side n draws: every block west of the east edge owns one or two,
# (n-1)^2 diagonals in all.
cmd_lattice_expect() {
    local x0 z0 n block
    read -r x0 z0 n block < <(lattice_of "$1") || return 1
    echo "$1 lattice: $block from $x0 $z0, $n square, $((n * n / 2)) blocks," \
        "$((n * n / 2 - n / 2)) holders, $(((n - 1) * (n - 1))) arms"
}

# One block at the corner and one diagonal to it make a 2x2 cell, which clones double along x
# and then z; max_block_modifications is raised so the clear and the last clone fit.
cmd_lattice_build() {
    local x0 z0 n block x1 z1 w out
    read -r x0 z0 n block < <(lattice_of "$1") || return 1
    x1=$((x0 + n - 1))
    z1=$((z0 + n - 1))
    rc "gamerule max_block_modifications 1000000" >/dev/null
    rc "forceload add $x0 $z0 $x1 $z1" >/dev/null
    rc "fill $x0 $Y $z0 $x1 $((Y + 1)) $z1 air" >/dev/null
    rc "setblock $x0 $Y $z0 minecraft:$block" >/dev/null
    rc "setblock $((x0 + 1)) $Y $((z0 + 1)) minecraft:$block" >/dev/null
    for ((w = 2; w < n; w *= 2)); do
        out="$(rc "clone $x0 $Y $z0 $((x0 + w - 1)) $Y $((z0 + 1)) $((x0 + w)) $Y $z0")"
        [[ "$out" == *cloned* ]] || { echo "FAIL clone along x at $w: $out"; return 1; }
    done
    for ((w = 2; w < n; w *= 2)); do
        out="$(rc "clone $x0 $Y $z0 $x1 $Y $((z0 + w - 1)) $x0 $Y $((z0 + w))")"
        [[ "$out" == *cloned* ]] || { echo "FAIL clone along z at $w: $out"; return 1; }
    done
    sleep 2
    rc "diagonals stats"
    echo
    rc "forceload remove $x0 $z0 $x1 $z1" >/dev/null
    cmd_lattice_expect "$1"
}

cmd_lattice_clear() {
    local x0 z0 n block x1 z1
    read -r x0 z0 n block < <(lattice_of "$1") || return 1
    x1=$((x0 + n - 1))
    z1=$((z0 + n - 1))
    rc "gamerule max_block_modifications 1000000" >/dev/null
    rc "forceload add $x0 $z0 $x1 $z1" >/dev/null
    rc "fill $x0 $Y $z0 $x1 $((Y + 1)) $z1 air"
    echo
    rc "forceload remove $x0 $z0 $x1 $z1" >/dev/null
}

cmd_list() {
    local layout
    for layout in "${ORDER[@]}"; do
        echo "$layout (${EXPECT[$layout]}, ${EXPECT_TALL[$layout]:-0} tall): ${DESCRIBE[$layout]}"
        echo "    ${BLOCKS[$layout]}"
    done
}

case "${1:-}" in
    build) shift; cmd_build "$@" ;;
    check) shift; cmd_check "$@" ;;
    f-split) cmd_f_split ;;
    stats) cmd_stats ;;
    list) cmd_list ;;
    live) cmd_live ;;
    timed) shift; cmd_timed "$@" ;;
    loop) shift; cmd_loop "$@" ;;
    unload) cmd_unload ;;
    reload) cmd_reload ;;
    f-first) shift; cmd_f_first "$@" ;;
    f-return) cmd_f_return ;;
    drawn) check_drawn ;;
    modded)
        case "${2:-}" in
            build) cmd_modded_build ;;
            check) cmd_modded_check ;;
            sweep) cmd_modded_sweep ;;
            clear) cmd_modded_clear ;;
            *) echo "usage: scripts/layouts.sh modded <build|check|sweep|clear>" >&2; exit 1 ;;
        esac
        ;;
    lattice)
        case "${2:-}" in
            build) cmd_lattice_build "${3:-}" ;;
            clear) cmd_lattice_clear "${3:-}" ;;
            expect) cmd_lattice_expect "${3:-}" ;;
            *) echo "usage: scripts/layouts.sh lattice <build|clear|expect> <wall|fence>" >&2
                exit 1 ;;
        esac
        ;;
    *)
        echo "usage: scripts/layouts.sh <build|check|f-split|stats|list|live|timed|loop|unload" \
            "|reload|f-first|f-return|drawn|modded|lattice> [args...]" >&2
        exit 1
        ;;
esac
