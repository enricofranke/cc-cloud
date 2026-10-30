---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Fehlerbehebung"
  position: 15
  icon: cccloud:cloud_analyzer
---

# Fehlerbehebung und FAQ

## „No such program“

Du hast Lua-Code in die **Shell** getippt. Die Shell startet nur Programme. Schreib den Code mit `edit startup.lua` in eine Datei und starte `startup`, oder tippe vorher `lua`. Siehe [CC-Grundlagen](cc-basics.md).

## „No widgets yet“ / leere Karte

- Der Uplink muss **an einem Computer** (oder einer Bridge) sitzen und mit der Link Card **verbunden** sein.
- Ein Computer ohne eigenes Programm zeigt die automatische Computer-Karte. Hat dein Programm alle Widgets entfernt (`cloud.clear()`), kommt die Karte nach Programmende zurück.
- Dein Programm muss wirklich laufen: Leg es in `startup.lua`, damit es nach Neustart oder Chunk-Laden startet.

## Die Bridge erscheint nicht

- Der Uplink muss **direkt auf der Bridge** sitzen, nicht an einem Kabel daneben.
- Die Bridge muss Teil eines Netzwerks mit Strom sein (ME: braucht einen Kanal – Controller und Kanalanzahl der Kabel prüfen; RS: der Controller braucht Energie).
- Verbinde mit der Link Card den **Uplink**, nicht die Bridge.

## Das Dashboard ist nicht erreichbar

- Nimm die URL aus dem Mainframe-Bildschirm. `localhost` funktioniert nur auf dem PC, auf dem Minecraft läuft.
- Das Gerät mit dem Browser und der Minecraft-Server müssen im selben Netzwerk sein (z. B. im selben WLAN, nicht mobile Daten) – oder siehe [Zugriff aus dem Internet](security.md).
- Erlaube Java in der PC-Firewall für private Netzwerke.
- Port 8787 muss frei sein; sonst steht es im Server-Log und ein Admin kann `webPort` ändern.
- Auf einem gemieteten Server muss der Port freigegeben sein – oder Reverse Proxy / Tunnel nutzen.

## „Mainframe has no power“

Der FE-Puffer ist leer. Schließe einen Generator oder ein FE-Kabel an den Mainframe an; er verbindet sich von selbst, sobald der Puffer wieder voll genug ist. Siehe [Strom](index.md).

## „Access revoked“

Das Gerät wurde entfernt (im Mainframe-Bildschirm oder mit `/cccloud revoke`), ist nach 30 Tagen verfallen, oder der Spieler, der es freigegeben hat, hat keinen Zugriff mehr. Mit einem neuen Code erneut koppeln.

## „LAN only“

Der Server erlaubt nur lokale Adressen. Der Serverbetreiber kann ihn mit `/cccloud internet` öffnen – siehe [Zugriff aus dem Internet](security.md).

## Bedienelemente sind ausgegraut

Das Dashboard ist ausgeschaltet, der Mainframe offline oder ohne Strom, der Uplink offline, oder dein Gerät ist schreibgeschützt. Das Banner oben sagt, was zutrifft.

## Meine Geräte gehen offline, wenn ich weggehe

Minecraft lässt nur die Chunks in der Nähe von Spielern laufen. Gehst du weg, wird der Chunk eines Computers, Uplinks oder einer Bridge entladen – er steht still, und CC: Cloud erreicht ihn nicht. Das Dashboard behält seine Karte **ausgegraut mit den letzten Werten** und zeigt „last seen 12 min ago – chunk not loaded“; die Bedienelemente sind gesperrt, bis der Chunk wieder geladen ist. CC: Cloud lädt nie selbst Chunks.

Damit eine Basis weiterläuft, während du weg bist, halte ihre Chunks geladen: mit FTB Chunks **force-loaden** (claimen, dann in der Karte force-load), mit einem Chunk-Loader aus einer anderen Mod, oder über die Spawn-Chunks des Servers. Dann wird die Karte von selbst wieder lebendig.

## Falsche PIN / gesperrt

Nach 5 falschen PINs ist das Gerät 5 Minuten gesperrt. Der Besitzer kann die PIN mit `/cccloud pin set` ändern.

