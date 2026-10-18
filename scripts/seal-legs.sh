#!/usr/bin/env bash
# Runs the diagonal-walls plan's S7 legs on a running dev server, over its rcon: items and arrows
# into layout A's gap, and animals penned in layout G through a tick sprint. Build the layouts first
# (scripts/layouts.sh build). Each leg runs under the current /diagonals debug seal mode unless
# given one, and prints which side of the wall line everything ended on.
#
#   scripts/seal-legs.sh items [on|boxes|off]          # three items slid into A's gap
#   scripts/seal-legs.sh arrows [on|boxes|off]         # two arrows shot into A's gap
#   scripts/seal-legs.sh pen [on|boxes|off] [ticks]    # 30 animals in G, sprint (default 24000)
#   scripts/seal-legs.sh pen-count                     # where those animals are now
#
# MOD_DIR (default: this checkout) picks the dev server.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="${MOD_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"

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
# Items start just above the floor, arrows at post height.
ITEM_Y=-52.95
ARROW_Y=-52.4
# Layout A's diagonal runs from (100, 100) to (101, 101): its line is x = z, the north-east side
# x > z, and the corner point (101, 101).
A_CORNER=101
# Layout G is a diamond of walls whose centres lie at L1 distance 4 from G's centre.
G_X=136.5
G_Z=104.5
G_RADIUS=4

rc() {
    "$DEVSERVER" "$MOD_DIR" rcon "$@"
}

set_mode() {
    [[ -n "${1:-}" ]] && rc "diagonals debug seal $1" >/dev/null
    return 0
}

# "x y z" of the first entity matching selector $1, or nothing.
position() {
    rc "data get entity $1 Pos" | sed -nE 's/.*\[(-?[0-9.E-]+)d, (-?[0-9.E-]+)d, (-?[0-9.E-]+)d\].*/\1 \2 \3/p'
}

# Which side of A's line x = z a point "x y z" is on.
a_side() {
    awk '{ d = $1 - $3; print (d > 0 ? "north-east" : "south-west") " (x - z = " d ")" }' <<<"$1"
}

cmd_items() {
    set_mode "${1:-}"
    local t x z pos
    # Aimed through A's corner box, the corner point and B's corner box, from the north-east.
    for t in -0.15 0 0.15; do
        rc "kill @e[tag=pdw_item]" >/dev/null
        x=$(awk -v c="$A_CORNER" -v t="$t" 'BEGIN { print c + t + 0.7 }')
        z=$(awk -v c="$A_CORNER" -v t="$t" 'BEGIN { print c + t - 0.7 }')
        rc "summon item $x $ITEM_Y $z {Item:{id:\"minecraft:stone\",count:1},Motion:[-0.4d,0.0d,0.4d],Tags:[\"pdw_item\"],PickupDelay:32767s}" >/dev/null
        sleep 3
        pos="$(position "@e[tag=pdw_item,limit=1]")"
        echo "item aimed at t=$t: from north-east, stopped at $pos, $(a_side "$pos")"
    done
    rc "kill @e[tag=pdw_item]" >/dev/null
}

cmd_arrows() {
    set_mode "${1:-}"
    local t x z pos
    for t in -0.15 0.1; do
        rc "kill @e[tag=pdw_arrow]" >/dev/null
        x=$(awk -v c="$A_CORNER" -v t="$t" 'BEGIN { print c + t + 1.5 }')
        z=$(awk -v c="$A_CORNER" -v t="$t" 'BEGIN { print c + t - 1.5 }')
        rc "summon arrow $x $ARROW_Y $z {Motion:[-0.8d,0.0d,0.8d],Tags:[\"pdw_arrow\"],pickup:0b}" >/dev/null
        sleep 2
        pos="$(position "@e[tag=pdw_arrow,limit=1]")"
        echo "arrow aimed at t=$t: from north-east, came to rest at $pos, $(a_side "$pos")"
    done
    rc "kill @e[tag=pdw_arrow]" >/dev/null
}

# The interior cells of G, |dx| + |dz| <= 3 from its centre cell, as "x z" lines.
pen_cells() {
    local dx dz
    for dx in -3 -2 -1 0 1 2 3; do
        for dz in -3 -2 -1 0 1 2 3; do
            if (( ${dx#-} + ${dz#-} <= 3 )); then
                echo "$((136 + dx)) $((104 + dz))"
            fi
        done
    done
}

cmd_pen() {
    set_mode "${1:-}"
    local ticks="${2:-24000}"
    local mobs=() i
    for i in $(seq 10); do mobs+=("chicken {}"); done
    for i in $(seq 10); do mobs+=("cow {Age:-24000,AgeLocked:1b}"); done
    for i in $(seq 5); do mobs+=("rabbit {}"); done
    # Tamed to an absent owner: a stray cat hunts the rabbits (Cat.java:120).
    for i in $(seq 5); do mobs+=("cat {Owner:[I;1,2,3,4]}"); done
    mapfile -t cells < <(pen_cells)

    rc "kill @e[tag=pdw_pen]" >/dev/null
    rc "kill @e[type=item,x=120,y=-60,z=88,dx=40,dy=20,dz=40]" >/dev/null
    local n type nbt cell
    for n in "${!mobs[@]}"; do
        type="${mobs[$n]%% *}"
        nbt="${mobs[$n]#* }"
        cell="${cells[$((n % ${#cells[@]}))]}"
        nbt="${nbt%\}}"
        [[ "$nbt" == "{" ]] || nbt+=","
        nbt+="PersistenceRequired:1b,Tags:[\"pdw_pen\",\"pdw_pen$n\"]}"
        rc "summon $type ${cell% *}.5 $Y ${cell#* }.5 $nbt" >/dev/null
    done
    echo "summoned $(rc "execute if entity @e[tag=pdw_pen]")"

    local start now
    start=$(gametime)
    rc "tick sprint $ticks" >/dev/null
    while :; do
        sleep 5
        now=$(gametime)
        (( now - start >= ticks )) && break
    done
    echo "sprinted $((now - start)) ticks"
    pen_count
}

# The world's game time in ticks.
gametime() {
    rc "time query gametime" | grep -oE '[0-9]+' | head -1
}

# Counts the penned animals by which side of G's walls they stand on.
pen_count() {
    local mobs=() i n type
    for i in $(seq 10); do mobs+=(chicken); done
    for i in $(seq 10); do mobs+=(cow); done
    for i in $(seq 5); do mobs+=(rabbit); done
    for i in $(seq 5); do mobs+=(cat); done
    local inside=0 outside=0 missing=0 pos d
    declare -A out_by_type=()
    for n in "${!mobs[@]}"; do
        pos="$(position "@e[tag=pdw_pen$n,limit=1]")"
        if [[ -z "$pos" ]]; then
            missing=$((missing + 1))
            continue
        fi
        d=$(awk -v cx="$G_X" -v cz="$G_Z" '{ x = $1 - cx; z = $3 - cz; print (x < 0 ? -x : x) + (z < 0 ? -z : z) }' <<<"$pos")
        if awk -v d="$d" -v r="$G_RADIUS" 'BEGIN { exit !(d < r) }'; then
            inside=$((inside + 1))
        else
            outside=$((outside + 1))
            type="${mobs[$n]}"
            out_by_type[$type]=$(( ${out_by_type[$type]:-0} + 1 ))
            echo "  outside: $type $n at $pos (L1 $d)"
        fi
    done
    echo "inside $inside, outside $outside, missing $missing"
    for type in "${!out_by_type[@]}"; do
        echo "  outside by type: $type ${out_by_type[$type]}"
    done
    echo "within G's bounding box: $(rc "execute if entity @e[tag=pdw_pen,x=132,y=-54,z=100,dx=8,dy=3,dz=8]")"
}

case "${1:-}" in
    items) shift; cmd_items "$@" ;;
    arrows) shift; cmd_arrows "$@" ;;
    pen) shift; cmd_pen "$@" ;;
    pen-count) pen_count ;;
    *)
        echo "usage: scripts/seal-legs.sh <items|arrows|pen> [on|boxes|off] [ticks]" >&2
        exit 1
        ;;
esac
