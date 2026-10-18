#!/usr/bin/env node
'use strict'
// Checks S18's post-less fence arms from two mineflayer bots' side, at layouts H and I: a bot
// that accepts the server pack is sent one item display per diagonal showing the fence's
// generated arm and no block-display arm; a bot that declines it is sent the block display only.
// Both arms stand at the same place with the same turn and scale.
// Needs the dev server up with the pack auto-hosted and not required (a declined required pack
// disconnects), `scripts/layouts.sh build H I` done, and ViaFabric + ViaBackwards in run/mods. It
// borrows mineflayer and the helpers of the workspace's scripts/loadtest (`npm install` there
// once). Run with the sandbox off: RCON and the bots use loopback.
//
//   node scripts/fence-arms.js [--mod-dir <checkout or worktree>]
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

const Y = -53
// Display metadata as 26.1 numbers it.
const TRANSLATION = 11
const SCALE = 12
const LEFT_ROTATION = 13
const VIEW_RANGE = 17
const DISPLAYED = 23
const ARM_MODEL = 'polymerdiagonalwalls:fence_arm/minecraft/oak_fence'
// scripts/layouts.sh's H and I: the corner point of each diagonal, all south-east of an oak fence.
const LAYOUTS = {
  H: [{ x: 145, z: 101 }],
  I: [0, 1, 2, 3, 4].map(k => ({ x: 149 + k, z: 101 + k }))
}
// An arm stands 1/16 from its corner point toward the owner; the next corner is 1.41 away.
const RADIUS = 0.5
// ServerboundResourcePackPacket actions.
const DECLINED = 1

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
  const seen = {}
  try {
    for (const [name, accepts] of [['faPack', true], ['faNoPack', false]]) {
      const bot = await join(server, name, accepts)
      try {
        await cmd(`gamemode spectator ${name}`)
        await cmd(`execute in minecraft:overworld run tp ${name} 149 ${Y + 3} 104 0 30`)
        await sleep(4000)
        seen[name] = arms(bot)
      } finally {
        bot.quit()
      }
      await sleep(1000)
    }
  } finally {
    rcon.close()
  }
  for (const [layout, corners] of Object.entries(LAYOUTS)) {
    corners.forEach((corner, i) => check(`${layout}${i + 1}`, corner, seen))
  }
  const failed = results.filter(r => !r.ok).length
  console.log(`\n${results.length - failed}/${results.length} legs passed`)
  process.exit(failed === 0 ? 0 : 1)
}

// One diagonal as each bot saw it.
function check (leg, corner, seen) {
  const at = list => list.filter(e => Math.hypot(e.x - corner.x, e.z - corner.z) <= RADIUS)
  const packItems = at(seen.faPack.items)
  const packBlocks = at(seen.faPack.blocks)
  const plainItems = at(seen.faNoPack.items)
  const plainBlocks = at(seen.faNoPack.blocks)
  const models = packItems.map(e => itemModel(e.meta[DISPLAYED]))
  record(`${leg}: with the pack, one item display showing ${ARM_MODEL}`,
    models.length === 1 && models[0] === ARM_MODEL, `got ${JSON.stringify(models)}`)
  record(`${leg}: with the pack, no block-display arm`, packBlocks.length === 0,
    `${packBlocks.length} found`)
  record(`${leg}: without the pack, one block display of oak_fence`,
    plainBlocks.length === 1 && plainItems.length === 0,
    `${plainBlocks.length} block, ${plainItems.length} item displays`)
  if (packItems.length !== 1 || plainBlocks.length !== 1) return
  const item = packItems[0]
  const block = plainBlocks[0]
  record(`${leg}: both arms stand at the same place, view range 2`,
    item.x === block.x && item.y === block.y && item.z === block.z &&
      item.meta[VIEW_RANGE] === 2 && block.meta[VIEW_RANGE] === 2,
    `item ${pos(item)} ${item.meta[VIEW_RANGE]}, block ${pos(block)} ${block.meta[VIEW_RANGE]}`)
  record(`${leg}: both arms share their turn and scale`,
    same(item.meta[LEFT_ROTATION], block.meta[LEFT_ROTATION]) &&
      same(item.meta[SCALE], block.meta[SCALE]),
    `item ${JSON.stringify([item.meta[LEFT_ROTATION], item.meta[SCALE], item.meta[TRANSLATION]])}` +
      `, block ${JSON.stringify([block.meta[LEFT_ROTATION], block.meta[SCALE], block.meta[TRANSLATION]])}`)
}

// Every display the bot holds, split into item displays and arm block displays (columns ride
// block displays with passengers).
function arms (bot) {
  const tracked = [...bot.tracked.values()]
  return {
    items: tracked.filter(e => e.name === 'item_display'),
    blocks: tracked.filter(e => e.name === 'block_display' && e.passengers.length === 0)
  }
}

function pos (e) {
  return `${e.x.toFixed(4)},${e.y.toFixed(4)},${e.z.toFixed(4)}`
}

// Whether two vector or quaternion metadata values agree to float precision.
function same (a, b) {
  if (a == null || b == null) return false
  return Object.keys(a).every(k => Math.abs(a[k] - b[k]) < 1e-6)
}

// The item_model component of an item display's slot, or a dump of the slot when it has none.
function itemModel (slot) {
  const components = slot?.components ?? []
  const component = components.find(c => String(c.type).replace(/^minecraft:/, '') === 'item_model')
  return component != null ? String(component.data) : `no item_model in ${JSON.stringify(slot)}`
}

// A bot that keeps every entity it is sent as the packets give it. One that does not accept
// answers the pack push with a decline instead of mineflayer's accept and loaded pair.
function join (server, username, accepts) {
  const bot = mineflayer.createBot({
    host: '127.0.0.1',
    port: server.serverPort,
    username,
    version: opts.version,
    auth: 'offline',
    viewDistance: 4
  })
  fixResourcePackReplies(bot._client)
  if (!accepts) {
    const write = bot._client.write.bind(bot._client)
    let declined = false
    bot._client.write = (name, params) => {
      if (name !== 'resource_pack_receive') return write(name, params)
      if (declined) return
      declined = true
      return write(name, { ...params, result: DECLINED })
    }
  }
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
    bot.once('kicked', reason => reject(new Error(`${username} kicked: ${JSON.stringify(reason)}`)))
    bot.once('error', reject)
  })
}

main().catch(err => {
  console.error(err)
  process.exit(2)
})
