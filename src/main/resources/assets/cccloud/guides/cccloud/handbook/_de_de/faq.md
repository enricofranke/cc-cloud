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
- Auf einem gemieteten Server muss der Port freigegeben sein – oder Reverse Proxy / Tunnel nutzen – oder ganz ohne Port mit [CC: Cloud Connect](security.md).

## Eine cccloud.dev-Adresse zeigt „offline“, „aktualisieren“ oder „kein Dashboard“

- **Offline:** Der Minecraft-Server ist nicht mit dem Relay verbunden. Er kommt von selbst wieder (die Seite lädt alle 30 Sekunden neu). Server-Admins: `/cccloud internet` zeigt den Grund (kein Internet, Name abgelehnt, …).
- **CC: Cloud aktualisieren:** Der Server nutzt einen CC: Cloud-Build, dessen Dashboard auf cccloud.dev nicht veröffentlicht ist (ein Entwicklungs-Build). Veröffentlichte Versionen funktionieren immer; die LAN-Adresse funktioniert weiter.
- **Kein Dashboard:** Kein Server nutzt diesen Namen (Tippfehler, freigegeben oder 90 Tage unbenutzt).
- **Zu viel Verkehr:** Der Server hat ein Connect-Limit erreicht (Tabs oder Tagesvolumen); versuch es später noch einmal.

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

## Items zeigen einen Buchstaben statt eines Icons

Das Icon gibt es noch nicht. Auf einem dedizierten Server zeichnet der Server beim Start die einfachen Icons; Truhen, Stufen, Treppen, 3D-Maschinen – und Vanilla-Items, solange du die Vanilla-Texturen nicht erlaubst – warten auf den ersten Spieler mit CC: Cloud, dessen Spiel sie im Hintergrund rendert. Siehe [Item-Icons auf einem dedizierten Server](security.md).

## Falsche PIN / gesperrt

Nach 5 falschen PINs ist das Gerät 5 Minuten gesperrt. Der Besitzer kann die PIN mit `/cccloud pin set` ändern.

## Keine Quests nach der Installation

- Der Server braucht **FTB Quests** und die normale Jar `cccloud-<version>.jar` – die Modpack-Edition fügt nie Quests hinzu.
- Im Server-Log steht, was passiert ist: Suche nach `FTB Quests:` (zum Beispiel `installQuests = false`, eine ID, die im Questbuch schon vergeben ist, oder ein Kapitel, das früher gelöscht wurde).
- Die Quests kommen nur einmal; ein gelöschtes Kapitel kommt nicht wieder. Für einen Neuanfang `config/cccloud/quests-installed.json` löschen und neu starten. Siehe [Quests](index.md).

## Eine Karte zeigt „Gesperrt – diese Maschine liegt im Claim von …“

Maschinen im Claim eines anderen Teams funktionieren auf dem Dashboard nicht mehr. Claimt ein Team den Chunk einer Maschine, die ein Uplink, eine Sonde, eine Bridge, ein Redstone-Gerät oder eine Karte über ein CC-Kabel liest, und ist weder der Spieler, der es verbunden hat, noch der Besitzer des Mainframes in diesem Team oder mit ihm verbündet, zeigt die Karte den Claim statt der Werte, und jedes Bedienelement wird abgelehnt – auch aus Regeln, Szenen und der API. Den Namen des Teams sehen nur Spieler, die Positionen sehen dürfen.

Sie geht von selbst wieder, sobald der Claim entfernt wird oder eure Teams Verbündete werden. Server-Operatoren bekommen keine Ausnahme. Claims kommen aus FTB Chunks; andere Claim-Mods werden noch nicht geprüft.

