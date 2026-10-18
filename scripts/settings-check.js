#!/usr/bin/env node
'use strict'
// Checks S9's settings from two mineflayer bots' side, on a dev server without rkcore-common, so
// server settings go through their fallback game rules and show_arms through the fallback
// /settings. Both bots stand in pen G; each leg changes one setting and times how soon the bots
// are sent the change:
//
//   show_arms: pdwA turns it off and loses G's arms but keeps their columns, while pdwB keeps
//     everything; a relog keeps pdwA's arms away; turning it on brings them back.
//   view_range: 64 sends every arm a view range of 1, a rebuilt arm included; 128 puts 2 back.
//   enabled: false takes every arm and column from both bots, true brings them back.
//   collision_range: pdwB, 12 blocks from layout A, gets A's columns at 16 and loses them at 8.
//
// Needs the dev server up with `scripts/layouts.sh build` done, and ViaFabric + ViaBackwards in
// run/mods. It borrows mineflayer and the helpers of the workspace's scripts/loadtest (`npm
// install` there once). Run with the sandbox off: RCON and the bots use loopback.
//
//   node scripts/settings-check.js [--mod-dir <checkout or worktree>]
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

const MOD = 'polymerdiagonalwalls'
const Y = -53
// Metadata indices as 26.1 numbers them (minecraft-data metadataKeys).
const VIEW_RANGE = 17
const BLOCK_STATE = 23
// A display's view range when its data never set one (Display.java).
const DEFAULT_VIEW_RANGE = 1
const G_CENTRE = { x: 136.5, z: 104.5 }
const G_ARMS = 16
// Six shulkers and two slimes per wall diagonal.
const WALL_RIDERS = 8
const A_ORIGIN = { x: 100.5, z: 100.5 }
// Past A's default release range of 10 and inside 16.
const B_FROM_A = 12
// One G owner, broken and placed again to rebuild its arm.
const G_OWNER = { x: 132, z: 104 }
const RECHECK_MS = 1000
// Long enough for a packet the server sent in the same tick.
const PROMPT_MS = 500

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
  const rule = (key, value) => cmd(`gamerule ${MOD}:${key} ${value}`)
  const logFile = path.join(server.runDir, 'logs', 'latest.log')
  const failedBefore = countFailedPackets(logFile)
  const listenerErrorsBefore = countMatches(logFile, /A change listener of/)

  for (const [key, value] of [['enabled', true], ['seal_gaps', true], ['view_range', 128],
    ['collision_range', 8]]) {
    await rule(key, value)
  }
  await cmd('execute positioned 140 -53 104 run diagonals rebuild 64')
  let botA = await join(server, 'pdwA')
  const botB = await join(server, 'pdwB')
  const tp = (bot, x, z) =>
    cmd(`execute in minecraft:overworld run tp ${bot.username} ${x} ${Y} ${z} 0 0`)
  const showArms = (bot, value) =>
    cmd(`execute as ${bot.username} run settings ${MOD} set show_arms ${value}`)
  try {
    for (const bot of [botA, botB]) await cmd(`gamemode creative ${bot.username}`)
    await showArms(botA, true)
    await showArms(botB, true)
    await tp(botA, G_CENTRE.x, G_CENTRE.z)
    await tp(botB, G_CENTRE.x, G_CENTRE.z)
    const full = bot => {
      const seen = near(bot, G_CENTRE, 6)
      return seen.arms.length === G_ARMS && seen.riders === G_ARMS * WALL_RIDERS
    }
    await until(() => full(botA) && full(botB), RECHECK_MS * 4)
    record('start: both bots in pen G hold its 16 arms and their columns',
      full(botA) && full(botB), `A ${describe(botA)}; B ${describe(botB)}`)
    record('start: every arm has view range 2 (128 blocks)',
      allViewRange([botA, botB], 2), viewRanges([botA, botB]))

    // show_arms: one player only, columns untouched.
    let mark = Date.now()
    const reply = await showArms(botA, false)
    let at = await until(() => near(botA, G_CENTRE, 6).arms.length === 0, PROMPT_MS * 4)
    await sleep(PROMPT_MS)
    record('show_arms false: pdwA loses every arm of G, keeps every column; pdwB keeps both',
      at != null && near(botA, G_CENTRE, 6).riders === G_ARMS * WALL_RIDERS && full(botB),
      `${since(mark, at)}; reply "${reply}"; A ${describe(botA)}; B ${describe(botB)}`)
    record('show_arms get: the fallback /settings answers false for pdwA',
      /show_arms is false/.test(await cmd(`execute as pdwA run settings ${MOD} get show_arms`)))

    await quit(botA)
    await sleep(1500)
    botA = await join(server, 'pdwA')
    await until(() => near(botA, G_CENTRE, 6).riders === G_ARMS * WALL_RIDERS, RECHECK_MS * 4)
    await sleep(PROMPT_MS)
    record('relog: pdwA rejoins in G with show_arms still false, gets the columns and no arm',
      near(botA, G_CENTRE, 6).arms.length === 0 &&
        near(botA, G_CENTRE, 6).riders === G_ARMS * WALL_RIDERS, `A ${describe(botA)}`)

    mark = Date.now()
    await showArms(botA, true)
    at = await until(() => near(botA, G_CENTRE, 6).arms.length === G_ARMS, PROMPT_MS * 4)
    record('show_arms true: pdwA gets G\'s 16 arms back where it stands',
      at != null && full(botA), `${since(mark, at)}; A ${describe(botA)}`)

    // view_range: arms already drawn, then one built under the new value.
    mark = Date.now()
    await rule('view_range', 64)
    at = await until(() => allViewRange([botA, botB], 1), PROMPT_MS * 4)
    record('view_range 64: every arm both bots hold now has view range 1',
      at != null, `${since(mark, at)}; ${viewRanges([botA, botB])}`)
    await cmd(`setblock ${G_OWNER.x} ${Y} ${G_OWNER.z} air`)
    await sleep(PROMPT_MS)
    await cmd(`setblock ${G_OWNER.x} ${Y} ${G_OWNER.z} cobblestone_wall`)
    await until(() => full(botA) && full(botB), RECHECK_MS * 3)
    record('view_range 64: an arm rebuilt after the change also has view range 1',
      full(botA) && full(botB) && allViewRange([botA, botB], 1), viewRanges([botA, botB]))
    mark = Date.now()
    await rule('view_range', 128)
    at = await until(() => allViewRange([botA, botB], 2), PROMPT_MS * 4)
    record('view_range 128: every arm is back at view range 2', at != null,
      `${since(mark, at)}; ${viewRanges([botA, botB])}`)

    // enabled: everything goes from both bots, and comes back.
    mark = Date.now()
    await rule('enabled', false)
    const empty = bot => {
      const seen = near(bot, G_CENTRE, 6)
      return seen.arms.length === 0 && seen.riders === 0 && seen.vehicles === 0
    }
    at = await until(() => empty(botA) && empty(botB), PROMPT_MS * 4)
    const stats = await cmd('diagonals stats')
    record('enabled false: both bots lose every arm, column and vehicle of G; stats reads 0',
      at != null && /Total: 0 holders, 0 arms/.test(stats),
      `${since(mark, at)}; A ${describe(botA)}; B ${describe(botB)}; "${stats}"`)
    await sleep(RECHECK_MS * 2.5)
    record('enabled false: a recheck later neither bot has been given anything back',
      empty(botA) && empty(botB), `A ${describe(botA)}; B ${describe(botB)}`)
    mark = Date.now()
    await rule('enabled', true)
    at = await until(() => near(botA, G_CENTRE, 6).arms.length === G_ARMS &&
      near(botB, G_CENTRE, 6).arms.length === G_ARMS, PROMPT_MS * 4)
    const columnsAt = await until(() => full(botA) && full(botB), RECHECK_MS * 3)
    record('enabled true: both bots get G\'s arms back at once and its columns within a recheck',
      at != null && columnsAt != null,
      `arms ${since(mark, at)}, columns ${since(mark, columnsAt)}; A ${describe(botA)}; ` +
        `B ${describe(botB)}`)

    // collision_range: pdwB stands 12 blocks south of A.
    await tp(botB, A_ORIGIN.x, A_ORIGIN.z + B_FROM_A)
    await sleep(RECHECK_MS * 2.5)
    record(`collision_range 8: ${B_FROM_A} blocks from A, pdwB holds A's arm but none of its columns`,
      near(botB, A_ORIGIN, 4).arms.length === 1 && near(botB, A_ORIGIN, 4).riders === 0,
      `B ${describe(botB, A_ORIGIN, 4)}`)
    mark = Date.now()
    await rule('collision_range', 16)
    at = await until(() => near(botB, A_ORIGIN, 4).riders === WALL_RIDERS, RECHECK_MS * 2.5)
    record('collision_range 16: pdwB gets A\'s columns within a recheck', at != null,
      `${since(mark, at)}; B ${describe(botB, A_ORIGIN, 4)}`)
    mark = Date.now()
    await rule('collision_range', 8)
    at = await until(() => near(botB, A_ORIGIN, 4).riders === 0, RECHECK_MS * 2.5)
    record('collision_range 8 again: pdwB loses them within a recheck', at != null,
      `${since(mark, at)}; B ${describe(botB, A_ORIGIN, 4)}`)
  } finally {
    await rule('enabled', true)
    await rule('view_range', 128)
    await rule('collision_range', 8)
    await quit(botA)
    await quit(botB)
  }
  rcon.close()
  const failed = countFailedPackets(logFile) - failedBefore
  const listenerErrors = countMatches(logFile, /A change listener of/) - listenerErrorsBefore
  record('server log: no "Failed to handle packet" and no failed change listener',
    failed === 0 && listenerErrors === 0, `${failed} packet, ${listenerErrors} listener`)
  const fails = results.filter(r => !r.ok).length
  console.log(`\n${results.length - fails}/${results.length} passed`)
  process.exitCode = fails === 0 ? 0 : 1
}

// The arms and column entities within `r` blocks (horizontally) of `centre`.
function near (bot, centre, r) {
  const all = [...bot.tracked.values()].filter(e => Math.hypot(e.x - centre.x, e.z - centre.z) <= r)
  const displays = all.filter(e => e.name === 'block_display')
  return {
    arms: displays.filter(e => e.passengers.length === 0 && e.meta[BLOCK_STATE] != null),
    riders: all.filter(e => e.name === 'shulker' || e.name === 'slime').length,
    vehicles: displays.filter(e => e.passengers.length > 0).length
  }
}

function describe (bot, centre = G_CENTRE, r = 6) {
  const seen = near(bot, centre, r)
  return `${seen.arms.length} arms, ${seen.riders} riders, ${seen.vehicles} vehicles`
}

function viewRangeOf (arm) {
  return arm.meta[VIEW_RANGE] ?? DEFAULT_VIEW_RANGE
}

function allViewRange (bots, want) {
  return bots.every(bot => {
    const arms = near(bot, G_CENTRE, 6).arms
    return arms.length === G_ARMS && arms.every(arm => Math.abs(viewRangeOf(arm) - want) < 1e-6)
  })
}

function viewRanges (bots) {
  return bots.map(bot => {
    const counts = {}
    for (const arm of near(bot, G_CENTRE, 6).arms) {
      counts[viewRangeOf(arm)] = (counts[viewRangeOf(arm)] ?? 0) + 1
    }
    return `${bot.username} view ranges ${JSON.stringify(counts)}`
  }).join('; ')
}

function since (mark, at) {
  return at == null ? 'never' : `${at - mark} ms`
}

function countMatches (file, pattern) {
  if (!fs.existsSync(file)) return 0
  return fs.readFileSync(file, 'utf8').split('\n').filter(line => pattern.test(line)).length
}

// Polls `test` every 20 ms; the time it first held, or null after `timeoutMs`.
async function until (test, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (test()) return Date.now()
    await sleep(20)
  }
  return test() ? Date.now() : null
}

// A bot that keeps every entity it is sent as the packets give it, before mineflayer reads them.
function join (server, username) {
  const bot = mineflayer.createBot({
    host: '127.0.0.1',
    port: server.serverPort,
    username,
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
      z: packet.z,
      meta: {},
      passengers: []
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
