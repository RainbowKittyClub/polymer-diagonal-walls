#!/usr/bin/env node
'use strict'
// The diagonal-walls plan's S7 and S13 player legs and S8 lure leg, with one mineflayer bot on a
// running dev server whose layouts are built (scripts/layouts.sh build). Corner and lure use pen
// G's north-east gap, between the walls at (137, 101) and (138, 102), whose corner point is
// (138, 102).
//
//   corner: the bot walks into that gap's corner from inside and from outside. Passes when the
//     server holds it where the corner boxes sit (players keep vanilla shapes), with no forced move
//     and no "moved wrongly" logged.
//   cross: the bot walks straight through fence gap H and pane gap J, both wide enough for it under
//     vanilla shapes. Passes when the server lets it through with no forced move and no "moved
//     wrongly"; on a client, the columns stop it, and mineflayer collides with no entity.
//   lure: a chicken starts in the pen's centre with the bot outside the gap holding wheat seeds,
//     once per seal mode in --modes (default on,boxes,off). With "on" the chicken should stop
//     inside, off the corner; "boxes" (/diagonals debug paths off) pins it in the corner, and "off"
//     (game rule seal_gaps false) lets it through, the two controls.
//
// Run with the sandbox off (RCON and the bot use loopback). The bot joins as 26.1, so run/mods
// needs ViaFabric and ViaBackwards (rkc/mods/artifacts/run/mods has both).
//
//   node scripts/seal-bots.js [--mod-dir <worktree>] [--legs corner,cross,lure]
//       [--modes on,boxes,off]
const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const { parseArgs } = require('node:util')

const { values: opts } = parseArgs({
  options: {
    'mod-dir': { type: 'string', default: path.resolve(__dirname, '..') },
    legs: { type: 'string', default: 'corner,cross,lure' },
    modes: { type: 'string', default: 'on,boxes,off' },
    version: { type: 'string', default: '26.1' },
    seconds: { type: 'string', default: '15' }
  }
})

// The workspace's mineflayer harness sits some levels up: one more for a worktree.
function findLoadtest (from) {
  for (let dir = path.resolve(from); dir !== path.dirname(dir); dir = path.dirname(dir)) {
    const candidate = path.join(dir, 'scripts', 'loadtest')
    if (fs.existsSync(path.join(candidate, 'node_modules', 'mineflayer'))) return candidate
  }
  throw new Error(`no scripts/loadtest with node_modules above ${from}`)
}
const LOADTEST = findLoadtest(opts['mod-dir'])
const harness = createRequire(path.join(LOADTEST, 'package.json'))
const mineflayer = harness('mineflayer')
const { Vec3 } = harness('vec3')
const { Rcon } = harness('./lib/rcon')
const { readDevServer, sleep, requireViaFabric, countWrongMoves } = harness('./lib/devserver')
const { fixResourcePackReplies, endClientTicks } = harness('./lib/flyer')

const BOT = 'pdwbot'
const FLOOR_Y = -53
// The gap: walls A (137, 101) and B (138, 102). Local coordinates put A at the origin.
const A = { x: 137, z: 101 }
const CORNER = { x: 138, z: 102 }
// Each end's corner box in world coordinates, as CornerBoxes adds them.
const BOXES = [
  { minX: 137.75, maxX: 138, minZ: 101.75, maxZ: 102 },
  { minX: 138, maxX: 138.25, minZ: 102, maxZ: 102.25 }
]
const PLAYER_HALF_WIDTH = 0.3
// Fence pair H and pane pair J, each running south-east to its corner point.
const CROSSINGS = [
  { name: 'fence H', corner: { x: 145, z: 101 } },
  { name: 'pane J', corner: { x: 165, z: 101 } }
]

const results = []
function record (leg, ok, detail) {
  results.push({ leg, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${leg} — ${detail}`)
}

let cmd
async function main () {
  const server = readDevServer(opts['mod-dir'])
  requireViaFabric(server.runDir)
  const rcon = await Rcon.connect({ port: server.rconPort, password: server.rconPassword })
  cmd = async c => (await rcon.send(c)).trim()
  const logFile = path.join(server.runDir, 'logs', 'latest.log')
  const legs = opts.legs.split(',')

  await cmd('kill @e[tag=pdw_pen]')
  await cmd('kill @e[tag=pdw_lure]')
  const bot = await join(server)
  try {
    await cmd(`gamemode creative ${BOT}`)
    if (legs.includes('corner')) await cornerLeg(bot, logFile)
    if (legs.includes('cross')) await crossLeg(bot, logFile)
    if (legs.includes('lure')) {
      for (const mode of opts.modes.split(',')) await lureLeg(bot, mode)
    }
  } finally {
    await cmd('kill @e[tag=pdw_lure]')
    await setSeal('on')
    bot.quit()
    rcon.close()
  }
  const failed = results.filter(r => !r.ok).length
  console.log(`\n${results.length - failed}/${results.length} legs passed`)
  process.exitCode = failed > 0 ? 1 : 0
}

// Walks the bot into the gap's corner from one side and reads back where the server holds it.
async function cornerLeg (bot, logFile) {
  await setSeal('on')
  const sides = [
    // Inside the pen, south-west of the line: the deepest spot is local (0.95, 1.05).
    { name: 'inside', start: new Vec3(137.3, FLOOR_Y, 102.8), aim: new Vec3(138.7, FLOOR_Y, 101.3) },
    // Outside, north-east of the line: the deepest spot is local (1.05, 0.95).
    { name: 'outside', start: new Vec3(138.8, FLOOR_Y, 101.3), aim: new Vec3(137.3, FLOOR_Y, 102.7) }
  ]
  for (const side of sides) {
    await teleport(bot, side.start)
    const wrongBefore = countWrongMoves(logFile)
    const forcedBefore = bot.forced
    await bot.lookAt(side.aim.offset(0, 1.62, 0), true)
    bot.setControlState('forward', true)
    await sleep(2500)
    // Sidestep both ways while pressing in, so the box rubs along both posts.
    for (const strafe of ['left', 'right', 'left']) {
      bot.setControlState(strafe, true)
      await sleep(700)
      bot.setControlState(strafe, false)
    }
    bot.setControlState('forward', false)
    await sleep(1000)
    const pos = await serverPosition(BOT)
    const overlaps = BOXES.filter(box => overlapsBox(pos, box)).length
    const wrong = countWrongMoves(logFile) - wrongBefore
    const forced = bot.forced - forcedBefore
    const local = `(${(pos.x - A.x).toFixed(3)}, ${(pos.z - A.z).toFixed(3)})`
    record(`corner ${side.name}`, overlaps > 0 && wrong === 0 && forced === 0,
      `server holds the bot at local ${local}, over ${overlaps} corner box(es); ` +
      `${forced} forced move(s), ${wrong} "moved wrongly"`)
  }
}

// Walks the bot from the north-east straight along each crossing's line through its corner point.
async function crossLeg (bot, logFile) {
  await setSeal('on')
  for (const crossing of CROSSINGS) {
    const { x, z } = crossing.corner
    await teleport(bot, new Vec3(x + 1.2, FLOOR_Y, z - 1.2))
    const wrongBefore = countWrongMoves(logFile)
    const forcedBefore = bot.forced
    await bot.lookAt(new Vec3(x - 3, FLOOR_Y + 1.62, z + 3), true)
    bot.setControlState('forward', true)
    await sleep(1200)
    bot.setControlState('forward', false)
    await sleep(1000)
    const pos = await serverPosition(BOT)
    // How far past the corner point the walk went, and how far it drifted off its line.
    const past = ((x - pos.x) + (pos.z - z)) / Math.SQRT2
    const drift = ((pos.x - x) + (pos.z - z)) / Math.SQRT2
    const wrong = countWrongMoves(logFile) - wrongBefore
    const forced = bot.forced - forcedBefore
    record(`cross ${crossing.name}`,
      past > 1 && Math.abs(drift) < 0.1 && wrong === 0 && forced === 0,
      `server holds the bot ${past.toFixed(2)} past the corner point, ${drift.toFixed(3)} off ` +
      `its line; ${forced} forced move(s), ${wrong} "moved wrongly"`)
  }
}

// Lures a chicken toward the bot across the gap and measures how it moves.
async function lureLeg (bot, mode) {
  await setSeal(mode)
  await cmd('kill @e[tag=pdw_lure]')
  await teleport(bot, new Vec3(140.5, FLOOR_Y, 99.5))
  await cmd(`item replace entity ${BOT} weapon.mainhand with minecraft:wheat_seeds`)
  // From the pen's centre, so the chicken has to walk to the gap.
  await cmd('summon chicken 136.5 -53 104.5 {PersistenceRequired:1b,Tags:["pdw_lure"]}')
  const samples = []
  const end = Date.now() + Number(opts.seconds) * 1000
  while (Date.now() < end) {
    samples.push(await serverPosition('@e[tag=pdw_lure,limit=1]'))
    await sleep(200)
  }
  // Path length over a run of samples, horizontal only.
  const travel = from => {
    let sum = 0
    for (let i = from + 1; i < samples.length; i++) {
      sum += Math.hypot(samples[i].x - samples[i - 1].x, samples[i].z - samples[i - 1].z)
    }
    return sum
  }
  // The last two thirds, after the walk to the gap: what a standing chicken does there.
  const settled = Math.floor(samples.length / 3)
  const toCorner = samples.slice(settled).map(p => Math.hypot(p.x - CORNER.x, p.z - CORNER.z))
  const nearest = Math.min(...toCorner)
  const first = samples[0]
  const last = samples.at(-1)
  const lineSide = (last.x - A.x) - (last.z - A.z) > 0 ? 'outside' : 'inside'
  // on: stands off the corner; boxes: pinned in it, the control; off: through the gap.
  const expected = {
    on: lineSide === 'inside' && nearest > 0.5 && travel(settled) < 0.05,
    boxes: lineSide === 'inside' && nearest < 0.4,
    off: lineSide === 'outside'
  }
  record(`lure seal=${mode}`, expected[mode] ?? false,
    `${samples.length} samples over ${opts.seconds} s: ` +
    `from (${first.x.toFixed(2)}, ${first.z.toFixed(2)}) to ${lineSide} ` +
    `(${last.x.toFixed(3)}, ${last.z.toFixed(3)}); ` +
    `travelled ${travel(0).toFixed(2)} in all and ${travel(settled).toFixed(3)} once settled, ` +
    `nearest the corner point then ${nearest.toFixed(3)}`)
}

// Seals gaps and routes mobs around them (on), seals them alone (boxes), or leaves them open (off).
async function setSeal (mode) {
  await cmd(`gamerule polymerdiagonalwalls:seal_gaps ${mode !== 'off'}`)
  await cmd(`diagonals debug paths ${mode === 'boxes' ? 'off' : 'on'}`)
}

// Whether a player standing at pos overlaps the box's footprint.
function overlapsBox (pos, box) {
  return pos.x - PLAYER_HALF_WIDTH < box.maxX && pos.x + PLAYER_HALF_WIDTH > box.minX &&
    pos.z - PLAYER_HALF_WIDTH < box.maxZ && pos.z + PLAYER_HALF_WIDTH > box.minZ
}

async function teleport (bot, pos) {
  await cmd(`execute in minecraft:overworld run tp ${BOT} ${pos.x} ${pos.y} ${pos.z} 0 0`)
  await sleep(1500)
}

// The entity's position as the server holds it.
async function serverPosition (selector) {
  const reply = await cmd(`data get entity ${selector} Pos`)
  const match = /\[(-?[\d.E-]+)d, (-?[\d.E-]+)d, (-?[\d.E-]+)d\]/.exec(reply)
  if (match == null) throw new Error(`no position for ${selector}: ${reply}`)
  return { x: Number(match[1]), y: Number(match[2]), z: Number(match[3]) }
}

function join (server) {
  const bot = mineflayer.createBot({
    host: '127.0.0.1',
    port: server.serverPort,
    username: BOT,
    version: opts.version,
    auth: 'offline',
    physicsEnabled: true,
    viewDistance: 4
  })
  fixResourcePackReplies(bot._client)
  endClientTicks(bot)
  bot.forced = 0
  return new Promise((resolve, reject) => {
    bot.once('spawn', () => {
      // The first forced move is the spawn teleport; count only those after it.
      bot.on('forcedMove', () => { bot.forced++ })
      resolve(bot)
    })
    bot.once('kicked', reason => reject(new Error(`kicked: ${JSON.stringify(reason)}`)))
    bot.once('error', reject)
  })
}

main().catch(err => {
  console.error(err)
  process.exitCode = 1
})
