# BasedDuels

BasedDuels is a Paper 1.21.4 dueling plugin with direct challenges, per-kit
matchmaking queues, reusable arena copies, spectators, disconnect grace
periods, and SQLite/MySQL statistics.

## Build and install

1. Build with Java 21: `mvn clean verify`
2. Copy `target/BasedDuels.jar` into the Paper server's `plugins` directory.
3. Start the server once to generate `config.yml` and `kits.yml`.

The configured arena world is created automatically as a flat world.

## Create an arena

1. Stand at one corner and run `/dueladmin arena pos1`.
2. Stand at the opposite corner and run `/dueladmin arena pos2`.
3. Run `/dueladmin arena capture <name>`.
4. Run `/dueladmin arena generate <name> [count]`.

The capture includes air blocks so each arena can be reset after a match.
Generated copies and their spawn locations are stored in `arenas.yml`.

## Player commands

- `/duel challenge <player> <kit>`
- `/duel accept` or `/duel deny`
- `/duel queue <kit>`
- `/duel leave`
- `/duel spectate <player>`
- `/duel stats [player]`
- `/duel top [kit]`
- `/duel gui`

Kits are configured in `kits.yml`. Run `/dueladmin reload` after changing
configuration or kits.
