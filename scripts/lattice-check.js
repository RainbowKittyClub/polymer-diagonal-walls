#!/usr/bin/env node
'use strict'
// S15's load and editor legs, with one mineflayer bot as the player. `teleport` brings the bot
// 2000 blocks into a lattice from `scripts/layouts.sh lattice build`, profiling the server thread
// with spark (saved to file, decoded here by scripts/loadtest/SparkReport.java, never uploaded),
// and counts what the bot is sent. `worldedit` runs WorldEdit //set, //paste of a saved schematic,
// //undo and //redo as the bot, sampling the arm count per tick through `layouts.sh timed`. Needs
// ViaFabric + ViaBackwards, spark and WorldEdit in run/mods. Run with the sandbox off.
//
//   node scripts/lattice-check.js [--mod-dir <dir>] [--lattices wall,fence] [--legs teleport,worldedit]
//       [--slow-ms 50]
const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')
const { createRequire } = require('node:module')
const { parseArgs } = require('node:util')

const { values: opts } = parseArgs({
  options: {
    'mod-dir': { type: 'string', default: path.resolve(__dirname, '..') },
    lattices: { type: 'string', default: 'wall,fence' },
    legs: { type: 'string', default: 'teleport,worldedit' },
    'slow-ms': { type: 'string', default: '50' },
    version: { type: 'string', default: '26.1' }
  }
})

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
const { readDevServer, findSparkJar, waitForProfile, sleep, requireViaFabric, countFailedPackets } =
  load('./lib/devserver')
const { fixResourcePackReplies } = load('./lib/flyer')

const BOT = 'pdwL'
const Y = -53
const AWAY = 2000
// As layouts.sh LATTICE gives them, with each family's virtual entities per diagonal in range.
const LATTICES = {
  wall: { x0: 512, z0: 512, n: 256, shulkers: 6, frogs: 2 },
  fence: { x0: 1024, z0: 512, n: 64, shulkers: 9, frogs: 2 }
}
// SparkReport's --match: this mod, Polymer and the chunk system around them.
const MATCH = '^(club\\.rainbowkitty\\.polymerdiagonalwalls\\.|eu\\.pb4\\.polymer\\.|' +
  'net\\.minecraft\\.server\\.level\\.(ChunkMap|ServerChunkCache|ChunkHolder|PlayerChunkSender)\\.)'
// WorldEdit zone: chunks 13..25 by 6..9, clear of every layout strip. A 16-square checkerboard
// pasted 32 east, and a 64-square one pasted 68 east, past what one drain's budget covers; the
// gaps keep any diagonal from joining a copy to its source.
const WE = { x0: 208, z0: 96, x1: 415, z1: 159 }
const WE_SOURCE = { x: 210, z: 100, n: 16 }
const WE_PASTE = { x: 242, z: 100 }
const WE_BIG = { x: 272, z: 96, n: 64 }
const WE_BIG_PASTE = { x: 340, z: 96 }

const results = []
function record (leg, ok, detail) {
  results.push({ leg, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${leg}${detail ? ` — ${detail}` : ''}`)
}

async function main () {
  const server = readDevServer(opts['mod-dir'])
  requireViaFabric(server.runDir)
  const sparkJar = opts.legs.includes('teleport') ? findSparkJar(server.runDir) : null
  const rcon = await Rcon.connect({ port: server.rconPort, password: server.rconPassword })
  const cmd = async c => (await rcon.send(c)).trim()
  const logFile = path.join(server.runDir, 'logs', 'latest.log')
  const failedBefore = countFailedPackets(logFile)
  const bot = await join(server)
  try {
    await cmd(`op ${BOT}`)
    await cmd(`gamemode creative ${BOT}`)
    const tp = (x, z) => cmd(`execute in minecraft:overworld run tp ${BOT} ${x} ${Y} ${z} 0 0`)
    if (opts.legs.includes('teleport')) {
      for (const name of opts.lattices.split(',')) {
        await teleportLegs(name, LATTICES[name], { cmd, tp, bot, server, sparkJar })
      }
    }
    if (opts.legs.includes('worldedit')) {
      await worldEditLegs({ cmd, tp, bot })
    }
    const failed = countFailedPackets(logFile) - failedBefore
    record('server log: no "Failed to handle packet" during the run', failed === 0, `${failed} new`)
  } finally {
    await quit(bot)
    rcon.close()
  }
  const passed = results.filter(r => r.ok).length
  console.log(`\n${passed}/${results.length} passed`)
  process.exit(passed === results.length ? 0 : 1)
}

async function teleportLegs (name, lattice, { cmd, tp, bot, server, sparkJar }) {
  const n = lattice.n
  const holders = n * n / 2 - n / 2
  const arms = (n - 1) * (n - 1)
  // A free cell beside the centre: (i + j) odd holds no block.
  const centre = { x: lattice.x0 + n / 2 + 1.5, z: lattice.z0 + n / 2 + 0.5 }
  const far = { x: centre.x + AWAY, z: centre.z }

  await tp(far.x, far.z)
  const base = await settle(cmd)
  console.log(`${name}: away, stats settled at ${base.holders} holders, ${base.arms} arms`)

  for (const mode of ['all', 'slow']) {
    const sparkArgs = mode === 'all'
      ? '--interval 1 --ignore-sleeping'
      : `--interval 1 --only-ticks-over ${opts['slow-ms']}`
    await cmd('spark profiler cancel').catch(() => {})
    const profileStart = Date.now()
    await cmd(`spark profiler start ${sparkArgs}`)
    await sleep(2000)
    const start = Date.now()
    await tp(centre.x, centre.z)
    const worst = sampleWorstTick(cmd, 12000)
    const indexed = await untilStats(cmd, s => s.holders - base.holders === holders, 60000)
    const sent = await until(() => near(bot, centre, n).arms >= arms, 60000)
    const worstMs = await worst
    const seen = near(bot, centre, n)
    await cmd('spark profiler stop --save-to-file')
    const profile = await waitForProfile(server.runDir, profileStart)
    const report = sparkReport(sparkJar, profile)
    const label = `${name} teleport (${mode} ticks)`
    record(`${label}: stats reaches the lattice's ${holders} holders and ${arms} arms`,
      indexed != null, indexed == null ? 'never' : `${indexed - start} ms after the teleport`)
    record(`${label}: the bot is sent every lattice arm`, sent != null && seen.arms === arms,
      `${seen.arms} arms${sent == null ? '' : `, all within ${sent - start} ms`}`)
    if (mode === 'all') {
      const diagonals = seen.shulkers.length / lattice.shulkers
      const perDiagonal = (seen.shulkers.length + seen.frogs.length + seen.vehicles.length) /
        diagonals + 1
      const want = lattice.shulkers + lattice.frogs + (lattice.shulkers + lattice.frogs) + 1
      record(`${label}: each diagonal in collision range sends ${want} virtual entities`,
        Number.isInteger(diagonals) && diagonals > 0 && perDiagonal === want &&
          seen.frogs.length === lattice.frogs * diagonals,
        `${diagonals} diagonals: ${seen.shulkers.length} shulkers, ${seen.frogs.length} frogs, ` +
          `${seen.vehicles.length} vehicles, so ${perDiagonal} each with the arm; ` +
          `${seen.arms + seen.shulkers.length + seen.frogs.length + seen.vehicles.length} in all`)
    }
    record(`${label}: no tick over ${opts['slow-ms']} ms from the teleport on`,
      worstMs <= Number(opts['slow-ms']), `worst tick ${worstMs} ms (tick query P99 = max of 100)`)
    console.log(`  profile ${path.basename(profile)}`)
    const shown = /no thread named/.test(report)
      ? `no tick over ${opts['slow-ms']} ms was sampled`
      : report.split('\n').filter(l => !l.startsWith('SUMMARY')).join('\n')
    console.log(shown.split('\n').map(l => `    ${l}`).join('\n'))

    // Leaving unloads the lattice: profile only the slow ticks, so any one is attributed.
    const awayStart = Date.now()
    await cmd(`spark profiler start --interval 1 --only-ticks-over ${opts['slow-ms']}`)
    await sleep(2000)
    const away = Date.now()
    const awayWorst = sampleWorstTick(cmd, 12000)
    await tp(far.x, far.z)
    const gone = await untilStats(cmd, s => s.holders === base.holders, 60000)
    const awayMs = await awayWorst
    await cmd('spark profiler stop --save-to-file')
    const awayReport = sparkReport(sparkJar, await waitForProfile(server.runDir, awayStart))
    record(`${name} away (${mode}): 2000 blocks off, stats is back to ${base.holders} holders`,
      gone != null, gone == null ? 'never' : `${gone - away} ms`)
    record(`${name} away (${mode}): no tick over ${opts['slow-ms']} ms while the lattice unloads`,
      awayMs <= Number(opts['slow-ms']), `worst tick ${awayMs} ms`)
    if (/no thread named/.test(awayReport)) {
      console.log(`    no tick over ${opts['slow-ms']} ms was sampled`)
    } else {
      console.log(awayReport.split('\n').filter(l => !l.startsWith('SUMMARY'))
        .map(l => `    ${l}`).join('\n'))
    }
    await sleep(6000)
  }
}

async function worldEditLegs ({ cmd, tp, bot }) {
  // WorldEdit takes the bot's own chat commands; `execute as` leaves it the console's actor.
  const replies = []
  bot.on('messagestr', message => replies.push(message))
  const we = async (command, prefix = '//') => {
    const before = replies.length
    bot.chat(`${prefix}${command}`)
    await until(() => replies.length > before, 3000)
    await sleep(100)
    return replies.slice(before).join(' | ')
  }
  execFileSync(path.join(__dirname, 'layouts.sh'), ['timed', '0', '0', 'time query daytime'],
    { encoding: 'utf8', env: { ...process.env, MOD_DIR: opts['mod-dir'] } })
  await cmd(`forceload add ${WE.x0} ${WE.z0} ${WE.x1} ${WE.z1}`)
  await cmd(`fill ${WE.x0} ${Y} ${WE.z0} ${WE.x1} ${Y + 1} ${WE.z1} air`)
  await tp(WE_SOURCE.x + 0.5, WE_SOURCE.z + 0.5)
  await sleep(3000)

  // One wall by //set beside one by setblock: one diagonal, then none.
  const a = { x: WE_SOURCE.x, z: WE_SOURCE.z }
  await cmd(`setblock ${a.x} ${Y} ${a.z} minecraft:cobblestone_wall`)
  console.log(`  ${await we(`pos1 ${a.x + 1},${Y},${a.z + 1}`)}`)
  console.log(`  ${await we(`pos2 ${a.x + 1},${Y},${a.z + 1}`)}`)
  await step('//set one wall diagonal to another', +1, a.x + 1, a.z + 1, 'set cobblestone_wall')
  await step('//set that wall to air', -1, a.x + 1, a.z + 1, 'set air')
  await cmd(`setblock ${a.x} ${Y} ${a.z} minecraft:air`)

  // A checkerboard 16 square by setblock and clone, saved as a schematic and loaded again.
  const s = WE_SOURCE
  const sArms = (s.n - 1) * (s.n - 1)
  await checkerboard(s)
  await tp(s.x + 0.5, s.z + 0.5)
  await sleep(500)
  console.log(`  ${await we(`pos1 ${s.x},${Y},${s.z}`)}`)
  console.log(`  ${await we(`pos2 ${s.x + s.n - 1},${Y + 1},${s.z + s.n - 1}`)}`)
  console.log(`  ${await we('copy')}`)
  console.log(`  ${await we('schem save -f pdw_s15_lattice')}`)
  console.log(`  ${await we('clearclipboard', '/')}`)
  const loaded = await we('schem load pdw_s15_lattice.schem')
  console.log(`  ${loaded}`)
  record('WorldEdit: the saved schematic loads into an emptied clipboard', /loaded/.test(loaded),
    loaded)

  await tp(WE_PASTE.x + 0.5, WE_PASTE.z + 0.5)
  await sleep(1000)
  const p = WE_PASTE
  await step(`//paste of the ${s.n}-square schematic`, +sArms, p.x, p.z, 'paste')
  await step('//undo of the paste', -sArms, p.x, p.z, 'undo')
  await step('//redo of the paste', +sArms, p.x, p.z, 'redo')
  await step('//undo again', -sArms, p.x, p.z, 'undo')

  // Filling the source with walls joins every cell, so every diagonal goes; //undo restores it.
  await tp(s.x + 0.5, s.z + 0.5)
  await sleep(500)
  await we(`pos1 ${s.x},${Y},${s.z}`)
  await we(`pos2 ${s.x + s.n - 1},${Y},${s.z + s.n - 1}`)
  await step(`//set of the ${s.n}-square source to walls`, -sArms, s.x + 1, s.z,
    'set cobblestone_wall')
  await step('//undo of that //set', +sArms, s.x + 1, s.z, 'undo')

  // 2048 walls in one //paste: the drain's budget spreads their arms over a few ticks.
  const b = WE_BIG
  const bArms = (b.n - 1) * (b.n - 1)
  await checkerboard(b)
  await sleep(1000)
  await tp(b.x + 0.5, b.z + 0.5)
  await sleep(500)
  await we(`pos1 ${b.x},${Y},${b.z}`)
  await we(`pos2 ${b.x + b.n - 1},${Y + 1},${b.z + b.n - 1}`)
  await we('copy')
  await tp(WE_BIG_PASTE.x + 0.5, WE_BIG_PASTE.z + 0.5)
  await sleep(2000)
  await step(`//paste of a ${b.n}-square checkerboard`, +bArms, WE_BIG_PASTE.x, WE_BIG_PASTE.z,
    'paste', true)
  await step('//undo of that paste', -bArms, WE_BIG_PASTE.x, WE_BIG_PASTE.z, 'undo', true)
  await cmd(`fill ${WE.x0} ${Y} ${WE.z0} ${WE.x1} ${Y + 1} ${WE.z1} air`)
  await cmd(`forceload remove ${WE.x0} ${WE.z0} ${WE.x1} ${WE.z1}`)

  // One block in the corner and one diagonal to it, cloned double along x and then z.
  async function checkerboard ({ x, z, n }) {
    await cmd(`setblock ${x} ${Y} ${z} minecraft:cobblestone_wall`)
    await cmd(`setblock ${x + 1} ${Y} ${z + 1} minecraft:cobblestone_wall`)
    for (let w = 2; w < n; w *= 2) {
      await cmd(`clone ${x} ${Y} ${z} ${x + w - 1} ${Y} ${z + 1} ${x + w} ${Y} ${z}`)
    }
    for (let w = 2; w < n; w *= 2) {
      await cmd(`clone ${x} ${Y} ${z} ${x + n - 1} ${Y} ${z + w - 1} ${x} ${Y} ${z + w}`)
    }
    await sleep(500)
  }

  // Starts layouts.sh's 40-tick sampler, then sends the bot's command, which lands between two
  // ticks. The count must hold through the first sample that sees the block change, which is
  // taken before that tick's drain, and read the new count from the next sample on.
  // With spread, the new count may come at any later sample; the leg reports which, and the
  // slowest tick around it.
  async function step (leg, delta, x, z, command, spread = false) {
    await cmd(`function pdwtest:watch {x:${x},y:${Y},z:${z}}`)
    const sent = Date.now()
    const reply = await we(command)
    const replied = Date.now() - sent
    await sleep(2500)
    const p99 = /P99: ([\d.]+)ms/.exec(await cmd('tick query'))?.[1]
    const data = await cmd('data get storage pdwtest:t')
    const list = key => (new RegExp(`${key}: \\[([^\\]]*)\\]`).exec(data)?.[1] ?? '')
      .split(',').map(v => Number(v.trim()))
    const arms = list('arms')
    const walls = list('walls')
    const start = arms[0]
    const changed = walls.findIndex(v => v !== walls[0])
    if (spread) {
      const settled = arms.findIndex(v => v === start + delta)
      const ok = changed > 0 && settled > changed &&
        arms.slice(settled).every(v => v === start + delta)
      record(`WorldEdit ${leg}: arms move by ${delta} within the 40 sampled ticks`, ok,
        `"${reply}"; block changed before sample ${changed}, arms complete at sample ${settled}, ` +
        `${settled - changed} drains later; arms ${arms.slice(changed, settled + 1).join(', ')}` +
        ` (from ${start}, ${arms.at(-1)} at the end); ` +
        `worst tick in the last 100 ${p99} ms`)
      return
    }
    const ok = changed > 0 && arms.slice(0, changed + 1).every(v => v === start) &&
      arms.slice(changed + 1).every(v => v === start + delta) && changed + 1 < arms.length
    record(`WorldEdit ${leg}: arms move by ${delta} at that tick's drain`, ok,
      `"${reply}" after ${replied} ms; block changed before sample ${changed}; ` +
        `arms ${arms.slice(Math.max(0, changed - 1), changed + 3).join(', ')} around it, ` +
        `${arms.at(-1)} at sample ${arms.length - 1}`)
  }
}

// The longest tick over the next ms: tick query's P99 is its 100th of 100 samples, the window's
// slowest tick, so sampling every 2 s and keeping the largest misses none.
async function sampleWorstTick (cmd, ms) {
  let worst = 0
  const deadline = Date.now() + ms
  while (Date.now() < deadline) {
    await sleep(2000)
    const p99 = /P99: ([\d.]+)ms/.exec(await cmd('tick query'))
    if (p99 != null) worst = Math.max(worst, Number(p99[1]))
  }
  return worst
}

// Polls stats until it holds still for 3 s.
async function settle (cmd) {
  let last = null
  let since = Date.now()
  for (;;) {
    const s = await stats(cmd)
    if (last == null || s.holders !== last.holders || s.arms !== last.arms) {
      last = s
      since = Date.now()
    } else if (Date.now() - since >= 3000) {
      return s
    }
    await sleep(250)
  }
}

async function stats (cmd) {
  const m = /Total: (\d+) holders, (\d+) arms/.exec(await cmd('diagonals stats'))
  return { holders: Number(m[1]), arms: Number(m[2]) }
}

async function untilStats (cmd, test, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (test(await stats(cmd))) return Date.now()
    await sleep(100)
  }
  return null
}

async function until (test, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (test()) return Date.now()
    await sleep(50)
  }
  return test() ? Date.now() : null
}

// What the bot holds within a square of side r around centre: arms are block displays with a
// block state and no rider, vehicles carry a shulker or frog.
function near (bot, centre, r) {
  const all = [...bot.tracked.values()]
    .filter(e => Math.abs(e.x - centre.x) <= r && Math.abs(e.z - centre.z) <= r)
  const displays = all.filter(e => e.name === 'block_display')
  return {
    shulkers: all.filter(e => e.name === 'shulker'),
    frogs: all.filter(e => e.name === 'frog'),
    vehicles: displays.filter(e => e.passengers.length > 0),
    arms: displays.filter(e => e.passengers.length === 0 && e.meta[23] != null).length
  }
}

function sparkReport (sparkJar, profile) {
  try {
    return execFileSync('java', ['--sun-misc-unsafe-memory-access=allow', '-cp', sparkJar,
      path.join(LOADTEST, 'SparkReport.java'), profile, '--match', MATCH, '--top', '25'],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] })
  } catch (err) {
    return `SparkReport failed:\n${err.stdout || ''}${err.stderr || err.message}\n`
  }
}

// A bot that keeps every entity it is sent as the packets give it, before mineflayer reads them.
function join (server) {
  const bot = mineflayer.createBot({
    host: '127.0.0.1',
    port: server.serverPort,
    username: BOT,
    version: opts.version,
    auth: 'offline',
    viewDistance: server.viewDistance
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
    for (const item of packet.metadata) {
      if (item.key === 23) entity.meta[23] = true
    }
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
