// Death Note regression: a delayed entry ("Name ticks") must still fire if the victim relogs before it is due.
// Writer must be an operator (uses /legendary give). Usage: node deathnote-relog-test.js <host> <port>
const mineflayer = require('mineflayer');

const [host = '127.0.0.1', port = '25599'] = process.argv.slice(2);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function connect(username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host, port: Number(port), username, version: '1.21.11', auth: 'offline' });
    bot.once('spawn', () => resolve(bot));
    bot.once('error', reject);
  });
}

function noteSlot(bot) {
  const it = bot.inventory.items().find((i) =>
    /fokendlegendarywepons:weapon_id":\{"type":"string","value":"death_note"/.test(JSON.stringify(i.components ?? {})));
  return it ? it.slot : -1;
}

async function main() {
  const writer = await connect('NoteWriter');
  let victim = await connect('Victim');
  await sleep(1500);
  writer.chat('/gamemode survival');
  writer.chat('/tp NoteWriter 40 -60 40');
  writer.chat('/tp Victim 43 -60 40');
  writer.chat('/gamemode survival Victim');
  await sleep(1000);
  writer.chat('/legendary give NoteWriter death_note');
  await sleep(1200);
  const slot = noteSlot(writer);
  if (slot < 0) throw new Error('no death note received');
  await writer.equip(writer.inventory.slots[slot], 'hand');
  await sleep(300);

  // 100 ticks = 5 s. The victim relogs ~1.5 s after the entry is written.
  await writer.writeBook(writer.quickBarSlot + writer.inventory.hotbarStart, ['Victim 100']);
  const writtenAt = Date.now();
  await sleep(1500);
  victim.quit();
  await sleep(1500);
  victim = await connect('Victim');
  let died = false;
  victim.on('death', () => { died = true; });
  victim.on('health', () => { if (victim.health <= 0) died = true; });
  const wait = 5000 - (Date.now() - writtenAt) + 2500;
  await sleep(Math.max(wait, 1000));
  console.log(`DN RESULT delayed-entry-survives-victim-relog ${died ? 'PASS victim died on schedule' : 'FAIL victim is still alive'}`);
  writer.quit();
  victim.quit();
  await sleep(300);
  process.exit(died ? 0 : 1);
}

main().catch((e) => {
  console.error('DN ERROR', e);
  process.exit(2);
});
