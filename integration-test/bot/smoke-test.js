// Smoke test: an operator bot receives every legendary item and triggers its abilities with real
// client packets. The runner checks the server log for exceptions afterwards.
// A second bot ("Observer") checks how chat from an invisible player is rendered (Death Note cipher).
// Usage: node smoke-test.js <host> <port> [only-id,...]
const mineflayer = require('mineflayer');

const [host = '127.0.0.1', port = '25599', only = ''] = process.argv.slice(2);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (...a) => console.log('[smoke]', ...a);

function connect(username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host, port: Number(port), username, version: '1.21.11', auth: 'offline' });
    bot.once('spawn', () => resolve(bot));
    bot.once('error', reject);
    bot.once('kicked', (r) => reject(new Error(username + ' kicked: ' + JSON.stringify(r))));
  });
}

async function cmd(bot, text, wait = 600) {
  bot.chat(text);
  await sleep(wait);
}

function findItem(bot, predicate) {
  return bot.inventory.items().find(predicate);
}

function legendaryId(item) {
  // custom_data component holds the PDC; search the raw NBT text for the weapon id.
  const raw = JSON.stringify(item?.components ?? item?.nbt ?? {});
  const m = raw.match(/fokendlegendarywepons:weapon_id":\{"type":"string","value":"([a-z_]+)"/);
  return m ? m[1] : null;
}

async function give(bot, id, storedId = id) {
  await cmd(bot, `/legendary give ${bot.username} ${id}`, 900);
  return findItem(bot, (it) => legendaryId(it) === storedId) || null;
}

async function hold(bot, item) {
  if (!item) throw new Error('item was not given');
  await bot.equip(item, 'hand');
  await sleep(300);
  return true;
}

async function rmb(bot, sneak = false) {
  if (sneak) bot.setControlState('sneak', true);
  await sleep(150);
  bot.activateItem();
  await sleep(150);
  bot.deactivateItem();
  if (sneak) {
    await sleep(150);
    bot.setControlState('sneak', false);
  }
  await sleep(400);
}

async function hitNearest(bot, name) {
  const target = bot.nearestEntity((e) => e.name === name);
  if (!target) return false;
  await bot.lookAt(target.position.offset(0, target.height * 0.6, 0), true);
  bot.attack(target);
  await sleep(700);
  return true;
}

async function summonZombie(bot) {
  await cmd(bot, '/summon zombie ~3 ~ ~ {NoAI:1b,PersistenceRequired:1b}', 800);
  const z = bot.nearestEntity((e) => e.name === 'zombie');
  if (z) await bot.lookAt(z.position.offset(0, 1.2, 0), true);
}

const scenarios = {
  ra_blade: async (bot) => {
    await hold(bot, await give(bot, 'ra_blade', 'ra_god_blade'));
    await rmb(bot);
    await hitNearest(bot, 'armor_stand');
    await rmb(bot); // recall
  },
  chaos_boomerang: async (bot) => {
    await summonZombie(bot);
    await hold(bot, await give(bot, 'chaos_boomerang'));
    await rmb(bot);
    await sleep(6000);
  },
  twins_sword: async (bot) => {
    await summonZombie(bot);
    await hold(bot, await give(bot, 'twins_sword'));
    await rmb(bot);
    await hitNearest(bot, 'zombie');
    await hitNearest(bot, 'zombie');
  },
  magnet: async (bot) => {
    await summonZombie(bot);
    await hold(bot, await give(bot, 'magnet'));
    await rmb(bot);
    await sleep(5200);
    await rmb(bot, true);
  },
  ice_spear: async (bot) => {
    await summonZombie(bot);
    const spear = await give(bot, 'ice_spear');
    await bot.moveSlotItem(spear.slot, bot.inventory.hotbarStart + 0);
    bot.setQuickBarSlot(0);
    await sleep(300);
    await rmb(bot, true);
    await sleep(500);
    const again = findItem(bot, (it) => legendaryId(it) === 'ice_spear');
    await bot.moveSlotItem(again.slot, bot.inventory.hotbarStart + 1);
    bot.setQuickBarSlot(1);
    await sleep(300);
    await hitNearest(bot, 'zombie');
    await sleep(3200);
  },
  time_clock: async (bot) => {
    await summonZombie(bot);
    await hold(bot, await give(bot, 'time_clock'));
    await hitNearest(bot, 'zombie');
    await sleep(10800);
    const slot = bot.quickBarSlot;
    bot.setQuickBarSlot((slot + 1) % 9);
    await sleep(300);
    bot.setQuickBarSlot(slot);
    await sleep(300);
    await rmb(bot);
  },
  warden_helmet: async (bot) => {
    await summonZombie(bot);
    const helm = await give(bot, 'warden_helmet');
    await bot.equip(helm, 'head');
    await sleep(800);
    await rmb(bot);
    await sleep(500);
    await bot.unequip('head');
    await sleep(800);
  },
  default_mask: async (bot) => {
    await summonZombie(bot);
    const mask = await give(bot, 'default_mask');
    await hold(bot, mask);
    await rmb(bot); // thompson from hand
    await sleep(1500);
    const again = findItem(bot, (it) => legendaryId(it) === 'default_mask');
    await bot.equip(again, 'head');
    await sleep(600);
    await bot.unequip('hand');
    await rmb(bot, true); // hammer
    await sleep(1200);
    await bot.unequip('head');
  },
  happy_mask: async (bot) => {
    const mask = await give(bot, 'happy_mask');
    await bot.equip(mask, 'head');
    await sleep(2500);
    await bot.unequip('head');
  },
  mask_tompson: async (bot) => {
    await summonZombie(bot);
    await hold(bot, await give(bot, 'mask_tompson'));
    for (let i = 0; i < 3; i++) await rmb(bot);
  },
  soul_bow: async (bot) => {
    await summonZombie(bot);
    await cmd(bot, `/give ${bot.username} arrow 16`);
    await hold(bot, await give(bot, 'soul_bow'));
    bot.activateItem();
    await sleep(1200);
    bot.deactivateItem();
    await sleep(800);
    bot.setControlState('sneak', true);
    bot.swingArm();
    await sleep(300);
    bot.setControlState('sneak', false);
  },
  fate_scales: async (bot) => {
    await summonZombie(bot);
    await hold(bot, await give(bot, 'fate_scales'));
    await rmb(bot);
    await rmb(bot, true);
    await hitNearest(bot, 'zombie');
    await sleep(1500);
  },
  fate_lightning: async (bot) => {
    await summonZombie(bot);
    await hold(bot, await give(bot, 'fate_lightning'));
    await hitNearest(bot, 'zombie');
    await sleep(1000);
  },
  death_note: async (bot) => {
    await cmd(bot, '/summon pig ~4 ~ ~ {NoAI:1b}', 800);
    const note = await give(bot, 'death_note');
    await hold(bot, note);
    const pages = ['pig', '', ''];
    await bot.writeBook(bot.quickBarSlot + bot.inventory.hotbarStart, pages);
    await sleep(1200);
    const pigs = Object.values(bot.entities).filter((e) => e.name === 'pig').length;
    log('death_note: pigs left nearby =', pigs);
  },
};

async function chatCipherCheck(actor) {
  const observer = await connect('Observer');
  await sleep(1500);
  let seen = null;
  // The rendered (per-viewer) message arrives as unsignedChatContent of the raw player_chat packet.
  observer._client.on('player_chat', (packet) => {
    const text = JSON.stringify(packet.unsignedChatContent ?? packet);
    if (JSON.stringify(packet).includes('cipher-probe')) seen = text;
  });
  await cmd(actor, `/effect give ${actor.username} invisibility 30 0 true`, 1200);
  actor.chat('cipher-probe');
  await sleep(1500);
  const obfuscated = !!seen && /"obfuscated":\{"type":"byte","value":1\}/.test(seen);
  console.log(`SMOKE RESULT chat-cipher-for-invisible ${obfuscated ? 'PASS' : 'FAIL'} ${seen}`);
  await cmd(actor, `/effect clear ${actor.username}`, 600);
  observer.quit();
}

async function main() {
  const bot = await connect('SmokeTester');
  await sleep(2000);
  await cmd(bot, '/gamemode survival', 400);
  await cmd(bot, '/tp @s 20 -60 20', 800);
  const ids = only ? only.split(',') : Object.keys(scenarios);
  for (const id of ids) {
    log('scenario', id);
    try {
      await scenarios[id](bot);
      console.log(`SMOKE RESULT ${id} RAN`);
    } catch (e) {
      console.log(`SMOKE RESULT ${id} ERROR ${e.message}`);
    }
    await cmd(bot, '/legendary fix', 400);
    await cmd(bot, '/kill @e[type=!player,type=!item]', 600);
    await cmd(bot, '/effect clear @s', 300);
  }
  await chatCipherCheck(bot);
  bot.quit();
  await sleep(500);
  process.exit(0);
}

main().catch((e) => {
  console.error('SMOKE ERROR', e);
  process.exit(2);
});
