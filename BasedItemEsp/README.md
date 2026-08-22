# BasedItemEsp

Production Anti Item-ESP for Paper 1.20.4+ using ProtocolLib.

## Download
Prebuilt jar (no Maven needed):

**https://github.com/idkbro928162/mellowduels/raw/cursor/based-item-esp-fefe/BasedItemEsp/dist/BasedItemEsp.jar**

Or browse: `BasedItemEsp/dist/BasedItemEsp.jar` on the `cursor/based-item-esp-fefe` branch → Download.

Also available as a GitHub Actions artifact on the **Build BasedItemEsp** workflow.

## Requirements
- Paper 1.20.4+
- ProtocolLib 5.1.0+
- Java 17+

## Behavior
- Hides dropped items (and stacker holograms) with no line of sight
- Vertical coverage: full world height (bedrock / min height → build limit)
- Horizontal: `max-distance: -1` = no limit (loaded entity window)
- **No bypass** — applies to everyone, including OP

## Build
```bash
cd BasedItemEsp
mvn -q package
```
Jar: `target/BasedItemEsp.jar`

## Config
See `src/main/resources/config.yml`
