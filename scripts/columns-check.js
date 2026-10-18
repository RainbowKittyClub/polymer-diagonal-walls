#!/usr/bin/env node
'use strict'
// Checks S11's and S12's client collision for wall diagonals from one mineflayer bot's side: which
// shulkers, slimes and empty vehicle displays it is sent, where, on what, at what scale and peek,
// and when they come and go as it moves around layouts A, F and G. It cannot tell whether they
// block; that is the user's check in a vanilla client. Needs the dev server up with
// `scripts/layouts.sh build` done, and ViaFabric + ViaBackwards in run/mods. It borrows
// mineflayer and the helpers of the workspace's scripts/loadtest (`npm install` there once). Run
// with the sandbox off: RCON and the bot use loopback.
//
//   node scripts/columns-check.js [--mod-dir <checkout or worktree>]
const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const { parseArgs } = require('node:util')

const { values: opts } = parseArgs({
  options: {
    'mod-dir': { type: 'string', default: path.resolve(__dirname, '..') },
    version: { type: 'string', default: '26.1' }
  }
})

// The workspace's scripts/loadtest sits some levels up: one more for a worktree than the checkout.
function findLoadtest (from) {
  for (let dir = path.resolve(from); dir !== path.dirname(dir); dir = path.dirname(dir)) {
    const candidate = path.join(dir, 'scripts', 'loadtest')
    if (fs.existsSync(path.join(candidate, 'lib', 'devserver.js'))) return candidate
  }
  throw new Error(`no scripts/loadtest above ${from}`)
}
const LOADTEST = findLoadtest(opts['mod-dir'])
const load = createRequire(path.join(LOADTEST, 'package.json'))
const mineflayer = load('mineflayer')
const { Rcon } = load('./lib/rcon')
const { readDevServer, sleep, requireViaFabric, countFailedPackets } = load('./lib/devserver')
const { fixResourcePackReplies } = load('./lib/flyer')

const BOT = 'cwA'
const Y = -53
// WallFamily's layout: column and slime centres from the corner point, three 0.5-tall shulkers.
const OFFSETS = [-0.125, 0.125]
const SHULKERS_PER_COLUMN = 3
const SHULKER_STEP = 0.5
const SHULKER_SCALE = 0.25
const SLIME_SCALE = 0.25 / 0.52
const PER_DIAGONAL = { shulker: 6, slime: 2, vehicle: 8 }
// Metadata indices as 26.1 numbers them (minecraft-data metadataKeys).
const SHARED_FLAGS = 0
const SILENT = 4
const SHULKER_PEEK = 17
const INVISIBLE_BIT = 0x20
// The scale attribute is registry id 25 in 26.1, but minecraft-data's 26.1 table lacks
// mining_efficiency, movement_efficiency and oxygen_bonus, so it decodes 25 under this name.
const SCALE_KEY = 'submerged_mining_speed'
// Layout A's owner and its holder position (bottom centre of the owner).
const A = { x: 100, z: 100, dz: 1 }
const A_ORIGIN = { x: 100.5, z: 100.5 }
// Layout F runs north-east from chunk x 9 into chunk x 10.
const F = { x: 159, z: 101, dz: -1 }
const F_ORIGIN = { x: 159.5, z: 101.5 }
const G_CENTRE = { x: 136.5, z: 104.5 }
// Pen G's owners: two north-east runs and two south-east ones, (132, 104) owning one of each.
const G_LIST = [
  ...[0, 1, 2, 3].map(i => ({ x: 132 + i, z: 104 - i, dz: -1 })),
  ...[0, 1, 2, 3].map(i => ({ x: 139 - i, z: 105 + i, dz: -1 })),
  ...[0, 1, 2, 3].map(i => ({ x: 136 + i, z: 100 + i, dz: 1 })),
  ...[0, 1, 2, 3].map(i => ({ x: 135 - i, z: 107 - i, dz: 1 }))
]
const G_DIAGONALS = G_LIST.length
// ColumnRange: admit within 8, release past 10, rechecked once a second.
const ADMIT = 8
const RELEASE = 10
const RECHECK_MS = 1000
const EPS = 1e-6

const results = []
function record (leg, ok, detail) {
  results.push({ leg, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${leg}${detail ? ` — ${detail}` : ''}`)
}

async function main () {
  const server = readDevServer(opts['mod-dir'])
  requireViaFabric(server.runDir)
  const rcon = await Rcon.connect({ port: server.rconPort, password: server.rconPassword })
  const cmd = async c => (await rcon.send(c)).trim()
  const logFile = path.join(server.runDir, 'logs', 'latest.log')
  const failedBefore = countFailedPackets(logFile)

  // Arms, and so column sites, appear only through a rebuild until S4 and S5.
  await cmd('execute positioned 140 -53 104 run diagonals rebuild 64')
  let bot = await join(server)
  try {
    await cmd(`gamemode creative ${BOT}`)
    const tp = (x, z) => cmd(`execute in minecraft:overworld run tp ${BOT} ${x} ${Y} ${z} 0 0`)

    // Far from A, inside its chunk's tracking: the arm comes, no columns.
    await tp(A_ORIGIN.x, A_ORIGIN.z + 24)
    await sleep(3000)
    const farA = near(bot, A_ORIGIN, 4)
    record('far: 24 blocks from A the bot gets A\'s arm but none of its columns',
      farA.arms === 1 && farA.riders.length === 0 && farA.vehicles.length === 0,
      describe(farA))

    // Between admit and release: still none.
    await tp(A_ORIGIN.x, A_ORIGIN.z + ADMIT + 1)
    await sleep(RECHECK_MS * 2.5)
    const edgeA = near(bot, A_ORIGIN, 4)
    record(`edge: ${ADMIT + 1} blocks out (inside the release range) nothing is offered`,
      edgeA.riders.length === 0, describe(edgeA))

    // Inside: all of A's within a recheck, laid out as WallFamily says.
    let mark = Date.now()
    await tp(A_ORIGIN.x, A_ORIGIN.z + 5.5)
    const arrivalMs = await until(() => near(bot, A_ORIGIN, 4).riders.length >= 8, RECHECK_MS * 2.5)
    const inA = near(bot, A_ORIGIN, 4)
    record('near: 5.5 blocks from A, its 6 shulkers, 2 slimes and 8 vehicles arrive within a recheck',
      arrivalMs != null && arrivalMs - mark <= RECHECK_MS + 500 && counts(inA, 1),
      `${arrivalMs == null ? 'never' : `${arrivalMs - mark} ms`}; ${describe(inA)}`)
    checkLayout('A', inA, [A])

    // Hysteresis: back to the edge keeps them.
    await tp(A_ORIGIN.x, A_ORIGIN.z + ADMIT + 1)
    await sleep(RECHECK_MS * 2.5)
    record(`hold: moving out to ${ADMIT + 1} blocks keeps every column`,
      counts(near(bot, A_ORIGIN, 4), 1), describe(near(bot, A_ORIGIN, 4)))

    // Debug toggle: invisibility clears on every rider, then comes back.
    await cmd('diagonals debug columns')
    await sleep(500)
    const shown = near(bot, A_ORIGIN, 4).riders.every(e => !invisible(e))
    await cmd('diagonals debug columns')
    await sleep(500)
    const hidden = near(bot, A_ORIGIN, 4).riders.every(e => invisible(e))
    record('debug columns: the first toggle clears every rider\'s invisible flag, the second sets it',
      shown && hidden, `shown ${shown}, hidden again ${hidden}`)

    // Out past release: gone within a recheck.
    mark = Date.now()
    await tp(A_ORIGIN.x, A_ORIGIN.z + RELEASE + 6)
    const goneMs = await until(() => near(bot, A_ORIGIN, 4).riders.length === 0, RECHECK_MS * 2.5)
    record(`leave: past ${RELEASE} blocks every rider and vehicle is removed within a recheck`,
      goneMs != null && near(bot, A_ORIGIN, 4).vehicles.length === 0,
      `${goneMs == null ? 'never' : `${goneMs - mark} ms`}; ${describe(near(bot, A_ORIGIN, 4))}`)

    // Relog standing near A: the chunk send hands them over at once.
    await tp(A_ORIGIN.x, A_ORIGIN.z + 5.5)
    await until(() => near(bot, A_ORIGIN, 4).riders.length >= 8, RECHECK_MS * 2.5)
    await quit(bot)
    await sleep(1500)
    bot = await join(server)
    mark = Date.now()
    const rejoinMs = await until(() => near(bot, A_ORIGIN, 4).riders.length >= 8, RECHECK_MS * 3)
    record('relog: rejoining 5.5 blocks from A gets its columns back',
      rejoinMs != null && counts(near(bot, A_ORIGIN, 4), 1),
      `${rejoinMs == null ? 'never' : `${rejoinMs - mark} ms after spawn`}; ` +
      describe(near(bot, A_ORIGIN, 4)))
    checkLayout('A after relog', near(bot, A_ORIGIN, 4), [A])

    // Pen G: sixteen diagonals.
    await tp(G_CENTRE.x, G_CENTRE.z)
    await until(() => near(bot, G_CENTRE, 6).riders.length >= G_DIAGONALS * 8, RECHECK_MS * 3)
    const inG = near(bot, G_CENTRE, 6)
    record(`pen G: from its centre the bot holds ${G_DIAGONALS} diagonals' columns, slimes and vehicles`,
      counts(inG, G_DIAGONALS), describe(inG))
    checkLayout('G', inG, G_LIST)

    // Layout F: north-east, its columns standing in the partner's chunk.
    await tp(F_ORIGIN.x, F_ORIGIN.z + 5)
    await until(() => near(bot, F_ORIGIN, 4).riders.length >= 8, RECHECK_MS * 3)
    const inF = near(bot, F_ORIGIN, 4)
    record('F: 5 blocks from the chunk-border pair, its columns arrive', counts(inF, 1), describe(inF))
    checkLayout('F', inF, [F])

    // A 2000-block trip and back.
    await tp(G_CENTRE.x + 2000, G_CENTRE.z)
    await sleep(3000)
    const away = near(bot, G_CENTRE, 6)
    await tp(G_CENTRE.x, G_CENTRE.z)
    await until(() => near(bot, G_CENTRE, 6).riders.length >= G_DIAGONALS * 8, RECHECK_MS * 4)
    const back = near(bot, G_CENTRE, 6)
    record('trip: 2000 blocks away G\'s columns go, and back at G they all return',
      away.riders.length === 0 && counts(back, G_DIAGONALS), `away ${describe(away)}; back ${describe(back)}`)
    checkPairs('G after the trip', back)
  } finally {
    await quit(bot)
  }
  // With nobody near, every holder is dropped at the next recheck; the toggle reports how many
  // holders it reached, and the second one puts visibility back.
  await sleep(RECHECK_MS * 2.5)
  const built = /on (\d+) holders/.exec(await cmd('diagonals debug columns'))?.[1]
  await cmd('diagonals debug columns')
  rcon.close()
  record('drop: once the bot has left, no column holder is left built', built === '0', `${built} built`)
  const failed = countFailedPackets(logFile) - failedBefore
  record('server log: no "Failed to handle packet" during the run', failed === 0, `${failed} new`)
  const fails = results.filter(r => !r.ok).length
  console.log(`\n${results.length - fails}/${results.length} passed`)
  process.exitCode = fails === 0 ? 0 : 1

  // Each expected seat of a wall diagonal, from WallFamily's numbers.
  function seatsOf ({ x, z, dz }) {
    const corner = { x: x + 1, z: z + (dz > 0 ? 1 : 0) }
    const shulkers = []
    const slimes = []
    for (const t of OFFSETS) {
      const bx = corner.x + t
      const bz = corner.z + dz * t
      for (let k = 0; k < SHULKERS_PER_COLUMN; k++) shulkers.push({ x: bx, y: Y + k * SHULKER_STEP, z: bz })
      slimes.push({ x: bx, y: Y, z: bz })
    }
    return { shulkers, slimes }
  }

  // Every vehicle carries one rider spawned where WallFamily puts it, at the right scale and data.
  function checkLayout (label, seen, diagonals) {
    const want = diagonals.map(seatsOf)
    const wantShulkers = want.flatMap(w => w.shulkers)
    const wantSlimes = want.flatMap(w => w.slimes)
    const vehicleAt = (seat, kind) => seen.vehicles.find(v => same(v, seat) && riderOf(v)?.name === kind)
    const shulkerSeats = wantShulkers.filter(seat => vehicleAt(seat, 'shulker') != null).length
    const slimeSeats = wantSlimes.filter(seat => vehicleAt(seat, 'slime') != null).length
    record(`${label}: every shulker and slime rides a vehicle at its WallFamily seat`,
      shulkerSeats === wantShulkers.length && slimeSeats === wantSlimes.length,
      `shulker seats ${shulkerSeats}/${wantShulkers.length}, slime seats ${slimeSeats}/${wantSlimes.length}`)
    checkPairs(label, seen)
  }

  // Spawn points, scale, peek and flags of every rider, read against its vehicle.
  function checkPairs (label, seen) {
    let spawnOk = 0
    let scaleOk = 0
    let dataOk = 0
    const bad = []
    for (const vehicle of seen.vehicles) {
      const rider = riderOf(vehicle)
      if (rider == null || vehicle.passengers.length !== 1) {
        bad.push(`vehicle ${vehicle.id} passengers ${JSON.stringify(vehicle.passengers)}`)
        continue
      }
      const drop = rider.name === 'shulker' ? 0.5 : 0
      if (near1(rider.x, vehicle.x) && near1(rider.z, vehicle.z) && near1(rider.y, vehicle.y - drop)) spawnOk++
      else bad.push(`${rider.name} ${rider.id} spawned ${fmt(rider)} under ${fmt(vehicle)}`)
      const scale = Object.keys(rider.attrs).length === 1 ? rider.attrs[SCALE_KEY] : undefined
      const wantScale = rider.name === 'shulker' ? SHULKER_SCALE : SLIME_SCALE
      if (scale != null && Math.abs(scale - wantScale) < 1e-6) scaleOk++
      else bad.push(`${rider.name} ${rider.id} scale ${scale}`)
      const peekOk = rider.name !== 'shulker' || rider.meta[SHULKER_PEEK] === 100
      if (peekOk && invisible(rider) && rider.meta[SILENT] === true) dataOk++
      else bad.push(`${rider.name} ${rider.id} meta ${JSON.stringify(rider.meta)}`)
    }
    const n = seen.vehicles.length
    record(`${label}: each rider spawns at its seat (shulkers 0.5 below), scale 0.25 wide, invisible, silent, shulkers peek 100`,
      n > 0 && spawnOk === n && scaleOk === n && dataOk === n,
      `${n} vehicles: spawn ${spawnOk}, scale ${scaleOk}, data ${dataOk}${bad.length ? `; ${bad.slice(0, 3).join('; ')}` : ''}`)
  }

  function riderOf (vehicle) {
    return vehicle.passengers.length === 1 ? bot.tracked.get(vehicle.passengers[0]) : null
  }
}

// The column entities and arms within `r` blocks (horizontally) of `centre`.
function near (bot, centre, r) {
  const all = [...bot.tracked.values()].filter(e => Math.hypot(e.x - centre.x, e.z - centre.z) <= r)
  const displays = all.filter(e => e.name === 'block_display')
  return {
    riders: all.filter(e => e.name === 'shulker' || e.name === 'slime'),
    shulkers: all.filter(e => e.name === 'shulker'),
    slimes: all.filter(e => e.name === 'slime'),
    vehicles: displays.filter(e => e.passengers.length > 0),
    arms: displays.filter(e => e.passengers.length === 0 && e.meta[23] != null).length
  }
}

function counts (seen, diagonals) {
  return seen.shulkers.length === PER_DIAGONAL.shulker * diagonals &&
    seen.slimes.length === PER_DIAGONAL.slime * diagonals &&
    seen.vehicles.length === PER_DIAGONAL.vehicle * diagonals
}

function describe (seen) {
  return `${seen.shulkers.length} shulkers, ${seen.slimes.length} slimes, ${seen.vehicles.length} vehicles, ` +
    `${seen.arms} arms`
}

function invisible (entity) {
  return ((entity.meta[SHARED_FLAGS] ?? 0) & INVISIBLE_BIT) !== 0
}

function same (a, b) {
  return near1(a.x, b.x) && near1(a.y, b.y) && near1(a.z, b.z)
}

function near1 (a, b) {
  return Math.abs(a - b) < EPS
}

function fmt (e) {
  return `(${e.x}, ${e.y}, ${e.z})`
}

// Polls `test` every 50 ms; the time it first held, or null after `timeoutMs`.
async function until (test, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (test()) return Date.now()
    await sleep(50)
  }
  return test() ? Date.now() : null
}

// A bot that keeps every entity it is sent as the packets give it, before mineflayer reads them.
function join (server) {
  const bot = mineflayer.createBot({
    host: '127.0.0.1',
    port: server.serverPort,
    username: BOT,
    version: opts.version,
    auth: 'offline',
    viewDistance: 4
  })
  fixResourcePackReplies(bot._client)
  bot.once('end', () => { bot.hasEnded = true })
  const tracked = new Map()
  bot.tracked = tracked
  const client = bot._client
  const names = {}
  client.on('spawn_entity', packet => {
    if (names.byId == null) {
      names.byId = Object.fromEntries(Object.values(bot.registry.entitiesByName).map(e => [e.id, e.name]))
    }
    tracked.set(packet.entityId, {
      id: packet.entityId,
      name: names.byId[packet.type] ?? String(packet.type),
      x: packet.x,
      y: packet.y,
      z: packet.z,
      meta: {},
      attrs: {},
      passengers: [],
      at: Date.now()
    })
  })
  client.on('entity_destroy', packet => {
    for (const id of packet.entityIds) tracked.delete(id)
  })
  client.on('entity_metadata', packet => {
    const entity = tracked.get(packet.entityId)
    if (entity == null) return
    for (const item of packet.metadata) entity.meta[item.key] = item.value
  })
  client.on('set_passengers', packet => {
    const entity = tracked.get(packet.entityId)
    if (entity != null) entity.passengers = packet.passengers
  })
  client.on('entity_update_attributes', packet => {
    const entity = tracked.get(packet.entityId)
    if (entity == null) return
    for (const property of packet.properties) entity.attrs[property.key] = property.value
  })
  return new Promise((resolve, reject) => {
    bot.once('spawn', () => resolve(bot))
    bot.once('kicked', reason => reject(new Error(`kicked: ${JSON.stringify(reason)}`)))
    bot.once('error', reject)
  })
}

function quit (bot) {
  return new Promise(resolve => {
    if (bot.hasEnded) return resolve()
    bot.once('end', resolve)
    bot.quit()
  })
}

main().catch(err => {
  console.error(err)
  process.exit(2)
})
