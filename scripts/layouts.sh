#!/usr/bin/env bash
# Builds and checks the diagonal-walls plan's test layouts on a running dev server, over its rcon.
#
#   scripts/layouts.sh build [layout...]   # clear the strip, forceload it, build (default: all)
#   scripts/layouts.sh check [layout...]   # probe every block of each layout, compare with plan
#   scripts/layouts.sh f-split             # layout F with only the owner's chunk loaded, then both
#   scripts/layouts.sh stats               # rebuild the strip, compare stats with a full probe
#   scripts/layouts.sh list                # each layout's blocks and expected diagonal count
#
# MOD_DIR (default: this checkout) picks the dev server. Every layout stands on the seeded flat
# world's surface, y=-53, in the chunk row z 96..111 at x >= 100, away from spawn.
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

rc() {
    "$DEVSERVER" "$MOD_DIR" rcon "$@"
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
    *)
        echo "usage: scripts/layouts.sh <build|check|f-split|stats|list> [layout...]" >&2
        exit 1
        ;;
esac
