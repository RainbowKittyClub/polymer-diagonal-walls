#!/usr/bin/env bash
# Runs the diagonal-walls plan's S7 and S13 legs on a running dev server, over its rcon: items and
# arrows into one gap (wall A, fence H or pane J), and animals penned in diamond G (walls) or L
# (fences) through a tick sprint. Build the layouts first (scripts/layouts.sh build). Each leg runs
# with the current paths switch unless given a mode, and prints which side of the diagonal
# everything ended on. Modes: on seals gaps and routes mobs around them, boxes seals them but leaves
# vanilla routing (/diagonals debug paths off).
#
#   scripts/seal-legs.sh items [on|boxes] [A|H|J]              # three items slid into the gap
#   scripts/seal-legs.sh arrows [on|boxes] [A|H|J]             # two arrows shot into the gap
#   scripts/seal-legs.sh pen [on|boxes] [ticks] [G|L]          # 30 animals, sprint (default 24000)
#   scripts/seal-legs.sh pen-count [G|L]                       # where those animals are now
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
# Corner point of each gap layout. Each diagonal runs south-east from the owner one block west and
# north of it, so its line is x - z = GAP_X - GAP_Z and its north-east side lies above that.
declare -A GAP_X=([A]=101 [H]=145 [J]=165) GAP_Z=([A]=101 [H]=101 [J]=101)
# Pens G and L are diamonds whose posts lie at L1 distance PEN_RADIUS from the centre cell.
declare -A PEN_X=([G]=136 [L]=180) PEN_Z=([G]=104 [L]=104)
PEN_RADIUS=4

rc() {
    "$DEVSERVER" "$MOD_DIR" rcon "$@"
}

set_mode() {
    case "${1:-}" in
        "") ;;
        on|boxes)
            rc "diagonals debug paths $([[ $1 == boxes ]] && echo off || echo on)" >/dev/null
            ;;
        *) echo "no mode $1; want on or boxes" >&2; exit 2 ;;
    esac
}

# "x y z" of the first entity matching selector $1, or nothing.
position() {
    rc "data get entity $1 Pos" | sed -nE 's/.*\[(-?[0-9.E-]+)d, (-?[0-9.E-]+)d, (-?[0-9.E-]+)d\].*/\1 \2 \3/p'
}

# Fails unless $1 names a gap layout, or $2 a pen.
require_gap() {
    [[ -n "${GAP_X[$1]:-}" ]] || { echo "no gap layout $1; want A, H or J" >&2; exit 2; }
}

require_pen() {
    [[ -n "${PEN_X[$1]:-}" ]] || { echo "no pen layout $1; want G or L" >&2; exit 2; }
}

# Which side of gap $1's line a point "x y z" ($2) is on.
gap_side() {
    awk -v cx="${GAP_X[$1]}" -v cz="${GAP_Z[$1]}" '{ d = ($1 - cx) - ($3 - cz)
        print (d > 0 ? "north-east" : "south-west") " (offset " d ")" }' <<<"$2"
}

cmd_items() {
    set_mode "${1:-}"
    local gap="${2:-A}" t x z pos
    require_gap "$gap"
    # Aimed through the owner's boxes, the corner point and the partner's, from the north-east.
    for t in -0.15 0 0.15; do
        rc "kill @e[tag=pdw_item]" >/dev/null
        x=$(awk -v c="${GAP_X[$gap]}" -v t="$t" 'BEGIN { print c + t + 0.7 }')
        z=$(awk -v c="${GAP_Z[$gap]}" -v t="$t" 'BEGIN { print c + t - 0.7 }')
        rc "summon item $x $ITEM_Y $z {Item:{id:\"minecraft:stone\",count:1},Motion:[-0.4d,0.0d,0.4d],Tags:[\"pdw_item\"],PickupDelay:32767s}" >/dev/null
        sleep 3
        pos="$(position "@e[tag=pdw_item,limit=1]")"
        echo "$gap item aimed at t=$t: from north-east, stopped at $pos, $(gap_side "$gap" "$pos")"
    done
    rc "kill @e[tag=pdw_item]" >/dev/null
}

cmd_arrows() {
    set_mode "${1:-}"
    local gap="${2:-A}" t x z pos
    require_gap "$gap"
    for t in -0.15 0.1; do
        rc "kill @e[tag=pdw_arrow]" >/dev/null
        x=$(awk -v c="${GAP_X[$gap]}" -v t="$t" 'BEGIN { print c + t + 1.5 }')
        z=$(awk -v c="${GAP_Z[$gap]}" -v t="$t" 'BEGIN { print c + t - 1.5 }')
        rc "summon arrow $x $ARROW_Y $z {Motion:[-0.8d,0.0d,0.8d],Tags:[\"pdw_arrow\"],pickup:0b}" >/dev/null
        sleep 2
        pos="$(position "@e[tag=pdw_arrow,limit=1]")"
        echo "$gap arrow aimed at t=$t: from north-east, came to rest at $pos," \
            "$(gap_side "$gap" "$pos")"
    done
    rc "kill @e[tag=pdw_arrow]" >/dev/null
}

# The interior cells of pen $1, |dx| + |dz| <= 3 from its centre cell, as "x z" lines.
pen_cells() {
    local dx dz
    for dx in -3 -2 -1 0 1 2 3; do
        for dz in -3 -2 -1 0 1 2 3; do
            if (( ${dx#-} + ${dz#-} <= 3 )); then
                echo "$((PEN_X[$1] + dx)) $((PEN_Z[$1] + dz))"
            fi
        done
    done
}

cmd_pen() {
    set_mode "${1:-}"
    local ticks="${2:-24000}" pen="${3:-G}"
    require_pen "$pen"
    local mobs=() i
    for i in $(seq 10); do mobs+=("chicken {}"); done
    for i in $(seq 10); do mobs+=("cow {Age:-24000,AgeLocked:1b}"); done
    for i in $(seq 5); do mobs+=("rabbit {}"); done
    # Tamed to an absent owner: a stray cat hunts the rabbits (Cat.java:120).
    for i in $(seq 5); do mobs+=("cat {Owner:[I;1,2,3,4]}"); done
    mapfile -t cells < <(pen_cells "$pen")

    rc "kill @e[tag=pdw_pen]" >/dev/null
    rc "kill @e[type=item,x=$((PEN_X[$pen] - 16)),y=-60,z=88,dx=40,dy=20,dz=40]" >/dev/null
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
    pen_count "$pen"
}

# The world's game time in ticks.
gametime() {
    rc "time query gametime" | grep -oE '[0-9]+' | head -1
}

# Counts the penned animals by which side of pen $1's posts they stand on.
pen_count() {
    local pen="${1:-G}" mobs=() i n type
    require_pen "$pen"
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
        d=$(awk -v cx="${PEN_X[$pen]}.5" -v cz="${PEN_Z[$pen]}.5" '{ x = $1 - cx; z = $3 - cz; print (x < 0 ? -x : x) + (z < 0 ? -z : z) }' <<<"$pos")
        if awk -v d="$d" -v r="$PEN_RADIUS" 'BEGIN { exit !(d < r) }'; then
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
    echo "within $pen's bounding box: $(rc "execute if entity @e[tag=pdw_pen,x=$((PEN_X[$pen] - 4)),y=-54,z=$((PEN_Z[$pen] - 4)),dx=8,dy=3,dz=8]")"
}

case "${1:-}" in
    items) shift; cmd_items "$@" ;;
    arrows) shift; cmd_arrows "$@" ;;
    pen) shift; cmd_pen "$@" ;;
    pen-count) shift; pen_count "$@" ;;
    *)
        echo "usage: scripts/seal-legs.sh <items|arrows|pen|pen-count> [on|boxes] [ticks]" \
            "[layout]" >&2
        exit 1
        ;;
esac
