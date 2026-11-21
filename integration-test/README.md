# Интеграционные тесты (настоящий сервер Paper 1.21.11)

Только для **тестового** сервера: стенд создаёт блоки и мобов возле точки (0, y, 0).

1. Собрать плагин (`gradle build` в корне) и стенд (`gradle build` в `integration-test/harness`).
2. Положить в `plugins/` тестового сервера `FokendLegendaryWepons-*.jar` и `legendary-it-harness-1.0.jar`.
   В `server.properties`: `online-mode=false`, `level-type=minecraft\:flat`.
3. `cd integration-test/bot && npm install`
4. Тесты:
   - `node axe-test.js 127.0.0.1 25565 main` — алмазный топор: создание, копание, удар по мобу,
     обтёсывание бревна (в том числе в шлеме Вардена), наковальня/точило на копиях, перезаход.
     После перезапуска сервера: `node axe-test.js 127.0.0.1 25565 after-restart`.
     Топор не удаляется и не заменяется, инвентарь не очищается.
   - `op SmokeTester`, затем `node smoke-test.js 127.0.0.1 25565 [id,id,...]` — выдать и применить
     каждое легендарное оружие; после прогона проверить лог сервера на исключения.
   - `op NoteWriter`, затем `node deathnote-relog-test.js 127.0.0.1 25565` — отложенная запись
     Тетради Смерти срабатывает, даже если жертва перезашла.

Каждая проверка печатает строку `LIT RESULT <имя> PASS|FAIL ...` (или `DN RESULT` / `SMOKE RESULT`).
