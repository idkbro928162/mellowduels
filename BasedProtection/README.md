# BasedProtection

Packet-only anti-ESP / anti-freecam / anti-xray for Paper 1.21+.

- **Version:** 1.1.1  
- **Author:** Magnus  
- **Depends:** [packetevents](https://github.com/retrooper/packetevents)  
- **Folia:** supported  

## Commands

`/antiesp <reload|status|toggle>`  
Aliases: `basedprotection`, `bp`, `aesp`, `fakewall`, `fw`

## Build

```bash
mvn clean package
```

Output jar: `target/BasedProtection.jar`

## Install

1. Install **packetevents** on the server  
2. Drop `BasedProtection.jar` into `plugins/`  
3. Restart and edit `plugins/BasedProtection/config.yml`

## Notes

Source was recovered from the production jar (decompiled). Logic and package name (`dev.mellow.antiesp`) match the live plugin.
