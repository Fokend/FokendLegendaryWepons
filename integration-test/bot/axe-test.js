// Real-client integration test: a mineflayer bot performs vanilla actions with a diamond axe
// while FokendLegendaryWepons is loaded. Server-side checks are done by the LegendaryITHarness plugin.
// Usage: node axe-test.js <host> <port> <phase: main|after-restart>
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');

const [host = '127.0.0.1', port = '25599', phase = 'main'] = process.argv.slice(2);
const NAME = 'AxeTester';
const results = [];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function connect() {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host, port: Number(port), username: NAME, version: '1.21.11', auth: 'offline' });
    bot.on('messagestr', (msg) => {
      if (msg.startsWith('LIT RESULT')) {
        results.push(msg);
        console.log(msg);
      }
    });
    // mineflayer cannot compute dig time for 1.21.11 enchantment components; use a safe, longer fixed time
    // (the server accepts a slower-than-possible dig).
    bot.once('spawn', () => {
      bot.digTime = () => 1500;
      resolve(bot);
    });
    bot.once('error', reject);
    bot.once('kicked', (r) => reject(new Error('kicked: ' + JSON.stringify(r))));
  });
}

async function lit(bot, step, arg = '', wait = 1200) {
  bot.chat(`/lit ${step} ${NAME} ${arg}`.trim());
  await sleep(wait);
}

function lastResult(name) {
  return [...results].reverse().find((r) => r.startsWith(`LIT RESULT ${name} `));
}

function parseCoord(text, key) {
  const m = text.match(new RegExp(`${key}=(-?\\d+),(-?\\d+),(-?\\d+)`));
  return m ? new Vec3(Number(m[1]), Number(m[2]), Number(m[3])) : null;
}

async function main() {
  let bot = await connect();
  await sleep(1500);

  if (phase === 'after-restart') {
    await lit(bot, 'axe-verify', 'after-server-restart');
    await lit(bot, 'clean');
    await lit(bot, 'summary');
    bot.quit();
    return finish();
  }

  await lit(bot, 'prepare');
  await lit(bot, 'axe-create', '', 3000);
  const created = lastResult('axe-create');
  const slot = Number((created || '').match(/slot=(\d+)/)?.[1] ?? -1);
  if (slot < 0) throw new Error('axe was not created: ' + created);
  bot.setQuickBarSlot(slot);
  await sleep(500);

  await lit(bot, 'area');
  const area = lastResult('area') || '';
  const logA = parseCoord(area, 'logA');
  const logB = parseCoord(area, 'logB');
  const logC = parseCoord(area, 'logC');
  if (!logA) throw new Error('area failed: ' + area);

  // 1) Break a log with the axe (real dig packets).
  await bot.lookAt(logA.offset(0.5, 0.5, 0.5));
  await bot.dig(bot.blockAt(logA), true);
  await sleep(800);
  await lit(bot, 'area-verify', 'dig');

  // 2) Attack a cow with the axe until it dies.
  for (let i = 0; i < 6; i++) {
    const cow = bot.nearestEntity((e) => e.name === 'cow');
    if (!cow) break;
    await bot.lookAt(cow.position.offset(0, 0.8, 0));
    bot.attack(cow);
    await sleep(1100);
  }
  await lit(bot, 'area-verify', 'cow');

  // 3) Right-click in the air with the axe.
  bot.activateItem();
  await sleep(300);
  bot.deactivateItem();
  await sleep(300);

  // 4) Strip a log with the axe (right-click on block).
  await bot.lookAt(logB.offset(0.5, 0.5, 0.5));
  await bot.activateBlock(bot.blockAt(logB));
  await sleep(800);
  await lit(bot, 'area-verify', 'strip');
  await lit(bot, 'axe-verify', 'after-use');

  // 5) Same strip action while wearing the Warden helmet (it hijacks right-clicks).
  await lit(bot, 'warden', 'on', 1500);
  await bot.lookAt(logC.offset(0.5, 0.5, 0.5));
  await bot.activateBlock(bot.blockAt(logC));
  await sleep(800);
  await lit(bot, 'area-verify', 'strip-warden');
  await lit(bot, 'axe-verify', 'after-warden-helmet');
  await lit(bot, 'warden', 'off', 2000);

  // 6) Anvil / grindstone with copies of the axe (the real axe never leaves its slot).
  await lit(bot, 'anvil', '', 1500);
  await lit(bot, 'grindstone', '', 1500);
  await lit(bot, 'axe-verify', 'after-anvil-grindstone');
  await lit(bot, 'clean');

  // 7) Relog: the axe must survive a save/load of player data.
  bot.quit();
  await sleep(2500);
  bot = await connect();
  await sleep(1500);
  await lit(bot, 'axe-verify', 'after-relog');
  await lit(bot, 'summary');
  bot.quit();
  return finish();
}

function finish() {
  const failed = results.filter((r) => / FAIL /.test(r));
  console.log(`BOT DONE results=${results.length} failed=${failed.length}`);
  process.exit(failed.length ? 1 : 0);
}

main().catch((e) => {
  console.error('BOT ERROR', e);
  process.exit(2);
});
