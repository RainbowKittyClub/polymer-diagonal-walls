#!/usr/bin/env bash
# Builds and checks the diagonal-walls plan's test layouts on a running dev server, over its rcon.
#
#   scripts/layouts.sh build [layout...]   # clear the strip, forceload it, build (default: all)
#   scripts/layouts.sh check [layout...]   # probe every block of each layout, compare with plan
#   scripts/layouts.sh f-split             # layout F with only the owner's chunk loaded, then both
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

ORDER=(A B C D E F G)
declare -A EXPECT DESCRIBE BLOCKS

# Each BLOCKS entry is "x z block" triples separated by ';'.
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

rc() {
    "$DEVSERVER" "$MOD_DIR" rcon "$@"
}

# Unique "x z block" triples of one layout, one per line.
blocks_of() {
    tr ';' '\n' <<<"${BLOCKS[$1]}" | awk 'NF' | sort -u
}

# Diagonals that probe printed for one block; rcon joins its lines without a separator.
probe_count() {
    rc "diagonals probe $1 $Y $2" | grep -oE '(south|north)-east to' | wc -l
}

cmd_build() {
    local layouts=("$@")
    (( ${#layouts[@]} )) || layouts=("${ORDER[@]}")
    rc "forceload add ${STRIP[0]} ${STRIP[1]} ${STRIP[2]} ${STRIP[3]}" >/dev/null
    if (( $# == 0 )); then
        rc "fill ${STRIP[0]} $Y ${STRIP[1]} ${STRIP[2]} $((Y + 3)) ${STRIP[3]} air" >/dev/null
    fi
    local layout x z block
    for layout in "${layouts[@]}"; do
        while read -r x z block; do
            rc "setblock $x $Y $z minecraft:$block" >/dev/null
        done < <(blocks_of "$layout")
        echo "built $layout: ${DESCRIBE[$layout]}"
    done
}

cmd_check() {
    local layouts=("$@")
    (( ${#layouts[@]} )) || layouts=("${ORDER[@]}")
    local layout x z block total failed=0
    for layout in "${layouts[@]}"; do
        total=0
        while read -r x z block; do
            total=$((total + $(probe_count "$x" "$z")))
        done < <(blocks_of "$layout")
        if (( total == ${EXPECT[$layout]} )); then
            echo "ok   $layout: $total diagonal(s)"
        else
            echo "FAIL $layout: $total diagonal(s), want ${EXPECT[$layout]}"
            failed=1
        fi
    done
    return $failed
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
        echo "$layout (${EXPECT[$layout]}): ${DESCRIBE[$layout]}"
        echo "    ${BLOCKS[$layout]}"
    done
}

case "${1:-}" in
    build) shift; cmd_build "$@" ;;
    check) shift; cmd_check "$@" ;;
    f-split) cmd_f_split ;;
    list) cmd_list ;;
    *)
        echo "usage: scripts/layouts.sh <build|check|f-split|list> [layout...]" >&2
        exit 1
        ;;
esac
