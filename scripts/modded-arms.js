#!/usr/bin/env node
'use strict'
// Checks S14's modded arms from one mineflayer bot's side: at each modded layout S-Y it reads the
// item displays the bot is sent around the corner point, their item models and view range, and
// that no block display draws a stand-in there; then that the patch overlay drawing the owner
// itself has that view range, and the shulkers each diagonal's columns hold.
// Needs the dev server up with survival's mod set, `scripts/layouts.sh modded build` done, and
// ViaFabric + ViaBackwards in run/mods. It borrows mineflayer and the helpers of the workspace's
// scripts/loadtest (`npm install` there once). Run with the sandbox off: RCON and the bot use
// loopback.
//
//   node scripts/modded-arms.js [--mod-dir <checkout or worktree>]
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
const { readDevServer, sleep, requireViaFabric } = load('./lib/devserver')
const { fixResourcePackReplies } = load('./lib/flyer')

const BOT = 'mdA'
const Y = -53
// Display metadata as 26.1 numbers it: view range, then a block display's state or an item
// display's item.
const VIEW_RANGE = 17
const DISPLAYED = 23
// Shulkers per diagonal, as each family's ColumnLayout stacks them.
const SHULKERS = { wall: 6, fence: 9, pane: 12 }
// scripts/layouts.sh's S-Y: owner x, z (each runs south-east), family, and its MODELS and RANGE.
const LAYOUTS = {
  S: { x: 100, z: 116, family: 'wall', range: 3, models: wallSides('terrestria:-/block/volcanic_rock_wall_side') },
  T: { x: 104, z: 116, family: 'wall', range: 100, models: wallSides('enderscape:-/block/mirestone_wall_side') },
  U: { x: 108, z: 116, family: 'fence', range: 3, models: wallSides('terrestria:-/block/redwood_fence_side') },
  V: { x: 112, z: 116, family: 'fence', range: 3, models: wallSides('woods_and_mires:-/block/pine_fence_side') },
  W: { x: 116, z: 116, family: 'fence', range: 100, models: wallSides('enderscape:-/block/veiled_fence_side') },
  X: {
    x: 120,
    z: 116,
    family: 'pane',
    range: 100,
    models: ['enderscape:-/block/shadoline_bars_side', 'enderscape:-/block/shadoline_bars_side_alt']
  },
  Y: { x: 124, z: 116, family: 'wall', range: 3, models: wallSides('terrestria:-/block/volcanic_rock_wall_side_tall') }
}
// An arm element stands 1/16 from the corner point toward its owner; the next layout's are 4 away.
const RADIUS = 0.5

const results = []
function record (leg, ok, detail) {
  results.push({ leg, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${leg}${detail ? ` — ${detail}` : ''}`)
}

// The north and south uvlock copies of one side model, as the patches pre-rotate them.
function wallSides (model) {
  return [`${model}_uvlock_0_0`, `${model}_uvlock_0_180`]
}

async function main () {
  const server = readDevServer(opts['mod-dir'])
  requireViaFabric(server.runDir)
  const rcon = await Rcon.connect({ port: server.rconPort, password: server.rconPassword })
  const cmd = async c => (await rcon.send(c)).trim()
  const bot = await join(server)
  try {
    await cmd(`gamemode creative ${BOT}`)
    // Three blocks south of each pair, inside ColumnRange's 8, so its columns come too.
    for (const [name, layout] of Object.entries(LAYOUTS)) {
      await cmd(`execute in minecraft:overworld run tp ${BOT} ${layout.x + 1} ${Y} ${layout.z + 4} 0 0`)
      await sleep(3000)
      checkLayout(bot, name, layout)
    }
  } finally {
    bot.quit()
    rcon.close()
  }
  const failed = results.filter(r => !r.ok).length
  console.log(`\n${results.length - failed}/${results.length} legs passed`)
  process.exit(failed === 0 ? 0 : 1)
}

function checkLayout (bot, name, layout) {
  const corner = { x: layout.x + 1, z: layout.z + 1 }
  const near = [...bot.tracked.values()]
    .filter(e => Math.hypot(e.x - corner.x, e.z - corner.z) <= RADIUS)
  const items = near.filter(e => e.name === 'item_display')
  const blockArms = near.filter(e => e.name === 'block_display' && e.passengers.length === 0)
  const models = items.map(e => itemModel(e.meta[DISPLAYED])).sort()
  const ranges = [...new Set(items.map(e => e.meta[VIEW_RANGE]))]
  const want = [...layout.models].sort()
  record(`${name}: the arm is ${want.length} item displays showing ${want.join(', ')}`,
    JSON.stringify(models) === JSON.stringify(want), `got ${JSON.stringify(models)}`)
  record(`${name}: every element has view range ${layout.range}`,
    ranges.length === 1 && ranges[0] === layout.range, `got ${JSON.stringify(ranges)}`)
  record(`${name}: no block display draws a stand-in arm`, blockArms.length === 0,
    `${blockArms.length} found`)
  // The overlay's own elements sit on the owner's centre, shifted at most 0.0005.
  const centre = { x: layout.x + 0.5, z: layout.z + 0.5 }
  const overlay = [...bot.tracked.values()].filter(e => e.name === 'item_display' &&
    Math.hypot(e.x - centre.x, e.z - centre.z) <= 0.01)
  const overlayRanges = [...new Set(overlay.map(e => e.meta[VIEW_RANGE]))]
  record(`${name}: the owner's own overlay draws at the same view range`,
    overlay.length > 0 && overlayRanges.length === 1 && overlayRanges[0] === layout.range,
    `${overlay.length} overlay elements at ${JSON.stringify(overlayRanges)}`)
  const shulkers = [...bot.tracked.values()].filter(e => e.name === 'shulker' &&
    Math.abs(e.x - corner.x) <= 0.5 && Math.abs(e.z - corner.z) <= 0.5)
  record(`${name}: the ${layout.family} columns hold ${SHULKERS[layout.family]} shulkers`,
    shulkers.length === SHULKERS[layout.family], `${shulkers.length} found`)
}

// The item_model component of an item display's slot, or a dump of the slot when it has none.
function itemModel (slot) {
  const components = slot?.components ?? []
  const component = components.find(c => String(c.type).replace(/^minecraft:/, '') === 'item_model')
  return component != null ? String(component.data) : `no item_model in ${JSON.stringify(slot)}`
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

main().catch(err => {
  console.error(err)
  process.exit(2)
})
