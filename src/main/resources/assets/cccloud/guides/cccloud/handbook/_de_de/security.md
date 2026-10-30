---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Sicherheit & Hosting"
  position: 14
  icon: cccloud:quantum_core
---

# Sicherheit und Hosting

Über das Dashboard lässt sich deine Welt steuern – deshalb ist es wie ein echter Webdienst geschützt.

## Standard: nur LAN

Ab Werk können nur Geräte im lokalen Netzwerk des Servers (und der Host selbst) das Dashboard öffnen. Alle anderen bekommen „LAN only“.

```toml
[web]
internetAccess = "off"   # default: only LAN addresses may connect (in game: /cccloud internet)
```

Die Konfigurationsdatei ist `<welt>/serverconfig/cccloud-server.toml`; Änderungen greifen ohne Neustart (siehe [unten](security.md)).

## Freigaben und Geräte

- Ein Kopplungscode ist 10 Minuten gültig und funktioniert einmal.
- Nach dem Code muss der Spieler, der ihn geöffnet hat, im Spiel auf **[Allow]** klicken (innerhalb von 2 Minuten).
- Jedes Gerät gehört zu diesem Spieler. Verliert er den Zugriff (nicht mehr vertraut, kein OP mehr, gebannt), funktioniert das Gerät sofort nicht mehr.
- Geräte, die 30 Tage nicht benutzt wurden, verfallen.
- Geräte auflisten und entfernen: im Mainframe-Bildschirm oder per Befehl.

## Besitzer, vertraute Spieler, OPs

Wer den Mainframe platziert hat, besitzt ihn. Besitzer, vertraute Spieler und OPs (Level 2+) sehen den Kopplungscode, geben Geräte frei, verbinden Uplinks und ändern Einstellungen. Alle anderen sehen nur den Status.

## Befehle

Schau den Mainframe an und gib ein:

```
/cccloud status
/cccloud devices
/cccloud revoke <device name>
/cccloud revoke all
/cccloud trust <player>
/cccloud untrust <player>
/cccloud pin set <4-8 digits>
/cccloud pin clear
```

## PIN

Kritische Widgets (`pin=true`) brauchen eine 4- bis 8-stellige PIN. 5 Fehlversuche sperren das Gerät für 5 Minuten und benachrichtigen den Besitzer.

## Zugriff aus dem Internet in 2 Minuten

Ein Befehl im Spiel (Serverbetreiber, OP-Level 4, oder die Server-Konsole):

```
/cccloud internet http
/cccloud internet http mybase.duckdns.org
```

Der Chat antwortet mit dem **Link** zum Öffnen, dem **Port**, den du im Router weiterleitest, und **wie du es testest** (Handy mit ausgeschaltetem WLAN). Es gilt sofort, ohne Neustart. `/cccloud internet` zeigt den aktuellen Stand, `/cccloud internet off` schließt es wieder. Dasselbe in `serverconfig/cccloud-server.toml`: `internetAccess = "http"` (optional `internetDomain = "meinebasis.duckdns.org"` für einen festen Namen).

| Modus | Einfach | Sicher | Braucht |
|---|---|---|---|
| `off` (Standard) | nichts zu tun | ja – nur dein lokales Netz | – |
| `http` – Einfach-Modus | ein Befehl, ein Port | **nein**, siehe unten | eine PIN an jedem Mainframe, TCP-Port 8787 weitergeleitet |
| HTTPS (Caddy, Cloudflare Tunnel, eigenes Zertifikat) | etwas Einrichtung | ja | eine Domain, siehe [Erweitert](#advanced) |

### Einfaches HTTP: was passieren kann

> **Achtung:** Im Modus `http` ist nichts verschlüsselt. Jeder im selben WLAN (Café, Schule, Hotel) oder auf dem Weg (Provider, VPN) kann deine Anmeldung und deine PIN mitlesen – und deine Basis dann wie du steuern. Nutze es zum Ausprobieren oder wenn du dieses Risiko in Kauf nimmst.

Was CC: Cloud tut, um den Schaden klein zu halten:

- **PIN für jede Steuer-Aktion aus dem Internet.** `/cccloud internet http` wird abgelehnt, bis jedes Mainframe eine PIN hat (`/cccloud pin set <Ziffern>`). Das Dashboard fragt einmal nach der PIN; danach funktionieren die Steuerelemente 10 Minuten lang in diesem Browser. Ein Mainframe ohne PIN kann aus dem Internet nur angesehen werden.
- **Kurze Anmeldungen:** Ein Browser, der 24 Stunden nicht benutzt wurde, wird abgemeldet, wenn er aus dem Internet kommt (`publicTokenIdleHours`); im LAN gelten die üblichen 30 Tage.
- **Neue Browser brauchen weiter [Erlauben] im Spiel;** Anfragen aus dem Internet sind im Chat markiert.
- **Strengere Grenzen für Internet-Adressen:** 5 falsche Codes, Anmeldungen oder PINs in 15 Minuten sperren die Adresse; halb so viele Aktionen und API-Anfragen.
- **Freigabe-Links bleiben nur lesend,** und API-Schlüssel können über einfaches HTTP aus dem Internet nichts steuern.
- **Warnungen überall:** im Server-Log, im Chat für OPs beim Betreten und als Banner im Dashboard, das sich nicht schließen lässt.

**Auf HTTPS umsteigen:** Richte einen der [erweiterten](#advanced) Wege ein (mit eigener Domain ist Caddy am einfachsten), dann `/cccloud internet off` – die erweiterten Einstellungen funktionieren für sich weiter.

## Erweitert: eigenes HTTPS

Diese Varianten nutzen die erweiterten `[web]`-Schlüssel und `internetAccess = "off"`.

### Caddy als Reverse Proxy (empfohlen)

```toml
[web]
webBind = "127.0.0.1"
webAllowInternet = true
webTrustedProxies = ["127.0.0.1"]
publicUrl = "https://dash.example.com"
```

```
dash.example.com {
    reverse_proxy 127.0.0.1:8787
}
```

Caddy holt sich automatisch ein Zertifikat und leitet WebSockets weiter.

### Cloudflare Tunnel (kein offener Port)

Gleiche `[web]`-Einstellungen wie bei Caddy, dann:

```bash
cloudflared tunnel login
cloudflared tunnel create cccloud
cloudflared tunnel route dns cccloud dash.example.com
cloudflared tunnel run cccloud
```

```yaml
tunnel: cccloud
credentials-file: /root/.cloudflared/<tunnel-id>.json
ingress:
  - hostname: dash.example.com
    service: http://127.0.0.1:8787
  - service: http_status:404
```

### TLS direkt im Mod

```toml
[web]
webAllowInternet = true
tlsCertPath = "/etc/letsencrypt/live/mc.example.com/fullchain.pem"
tlsKeyPath = "/srv/mc/privkey-pkcs8.pem"
publicUrl = "https://mc.example.com:8787"
```

Den Let's-Encrypt-Schlüssel vorher umwandeln: `openssl pkcs8 -topk8 -nocrypt -in privkey.pem -out privkey-pkcs8.pem`. Lassen sich die Dateien nicht laden, startet das Dashboard nicht – es fällt nie heimlich auf HTTP zurück.

### Port von Hand freigeben

Ältere Einrichtungen: `webAllowInternet = true` mit `publicUrl = "http://mc.example.com:8787"` öffnet den Port ohne die Schutzmaßnahmen des Modus `http`. Besser `/cccloud internet http`.

## Große Server

CC: Cloud ist für große Basen gebaut: hunderte Maschinen an einem Mainframe, viele Mainframes und viele Browser. Das meiste braucht keine Einstellung; das kann ein Serverbetreiber tun.

- **Seiten:** Das Dashboard zeichnet nur die Karten in der Nähe des Bildschirms, aber eine Seite mit 500 Karten bleibt ein langer Scroll. Teile große Basen in Seiten auf (Layout-Editor oder `opts.page` in Lua); bis etwa 100 Karten pro Seite ist angenehm. Layouts mit bis zu 2048 Karten lassen sich speichern.
- **Kompression:** Lass `webCompression = true` (Standard). Browser bekommen Snapshot und Updates komprimiert (5–10× kleiner); das kostet etwas CPU auf den eigenen Threads des Webservers, nie auf dem Server-Thread. Reverse-Proxys (Caddy, nginx, Cloudflare) reichen das durch.
- **Browser:** Jeder Browser eines Mainframes bekommt alle seine Updates. Ein Mainframe mit 500 Geräten, bei dem sich ständig alles ändert, schickt etwa 130 KB/s pro Browser (komprimiert etwa 60 KB/s). Das Bandbreiten-Upgrade (oder `baseBrowsers`) begrenzt die Browser pro Mainframe; Freigabe-Links zählen extra.
- **Langzeit-Statistik:** Jeder Mainframe hält höchstens 2 MB Verlauf; `historyMaxTotalMB` (Standard 128) begrenzt alle Mainframes zusammen. Jede Reihe hat von Anfang an eine feste Größe (1 h ≈ 240 B, 24 h ≈ 5,6 KB, 7 Tage ≈ 14 KB, 30 Tage ≈ 20 KB).
- **Kartenkacheln auf der Festplatte:** Die 2D-Karte speichert ihre Kacheln in `<world>/cccloud/maptiles`. Kacheln, die 30 Tage weder gezeichnet noch angesehen wurden, werden gelöscht (`mapTileMaxAgeDays`, 0 behält sie), und das Löschen eines Mainframes (purge) entfernt seine Höhlenschnitte.
- **Bildschirme im Spiel:** Der Mainframe-Bildschirm listet die ersten 600 Geräte (Probleme zuerst) und bekommt das Netzwerk nur, wenn es sich geändert hat; die Netzwerk-Seite des Dashboards zeigt alle Geräte. Große Netzwerkkarten lassen Verbindungen weg, die ein Gerät nicht benutzt.
- **Langsame Browser:** Ein Browser, der mehr als 4 MB oder 10 Sekunden zurückliegt, wird getrennt und verbindet sich mit einem frischen Snapshot neu; auf dem Server stapelt sich nichts.
- **Erweiterte Cloud-Sensoren:** jeder schaut zweimal pro Sekunde mit einer begrenzten Entity-Abfrage in seine Zone, verteilt über die Ticks; `advancedSensorScansPerTick` (Standard 32) begrenzt die Scans pro Tick auf dem ganzen Server, und ein unscharfer Sensor ohne Redstone-Ausgang scannt gar nicht.
- **Messen:** `/neoforge tps` oder spark zeigen die Tick-Zeit des Servers. Der eigene Anteil von CC: Cloud liegt bei einem Mainframe mit 500 Geräten und 10 Browsern bei etwa 0,5–1 ms pro Tick (Entwickler: `./gradlew runGameTestServer -PscaleTest` misst das).

## Einstellungen ohne Neustart ändern

Jede Einstellung in `cccloud-server.toml` und `cccloud-integrations-server.toml` greift, während der Server läuft. Datei bearbeiten und speichern: NeoForge bemerkt die Änderung (innerhalb weniger Sekunden), CC: Cloud übernimmt sie. Oder als OP (Berechtigung `cccloud.command.reload`):

```
/cccloud config reload     # beide Dateien neu lesen, anwenden, Änderungen auflisten
/cccloud config show       # alle Werte (Geheimes nur als "set")
/cccloud config show webPort
```

`config reload` zeigt, welche Werte sich geändert haben, was live übernommen wurde und was einen Neustart bräuchte (die letzte Kommentarzeile jeder Einstellung in der Datei sagt es; derzeit greift jede Einstellung live). Es lädt außerdem erneuerte TLS-Zertifikatsdateien unter denselben Pfaden und versucht ein Dashboard neu, das nicht starten konnte. Derselbe Bericht steht im Server-Log.

Was mit dem Dashboard passiert:

- **`[web]` (Port, Adresse, TLS, Proxys, Internetzugriff, erlaubte Hosts):** Der Webserver startet allein neu, ohne das Spiel. Ein neuer Port oder eine neue Adresse wird geöffnet, **bevor** der alte schließt; klappt das nicht (Port belegt, keine Berechtigung), ändert sich nichts, der alte läuft weiter, und Log und `config reload` sagen warum. Unlesbare TLS-Dateien behalten ebenfalls die alten Einstellungen – ein Reload fällt nie auf unverschlüsseltes HTTP zurück. Offene Dashboards verbinden sich selbst neu; nach einer Port-, Adress- oder TLS-Änderung die neue Adresse öffnen (Mainframe-Bildschirm und `/cccloud status` zeigen sie).
- **Internetzugriff:** `webAllowInternet` einschalten wirkt genau wie ein Serverstart damit (dieselben Warnungen im Log, z. B. bei HTTP ohne TLS); ausschalten trennt sofort jede Verbindung, und nur Browser im LAN kommen wieder herein.
- **Funktionsschalter** (API, Push, Regeln, Szenen, Terminal, Voxel, Produktionsstatistik, Script Store): gelten ab der nächsten Aktion; offene Dashboards passen ihre Menüs an.
- **Limits** (API-Ratenlimits, Änderungen pro Sekunde der Cloud-Schalter, Grundwerte der Upgrades, Speicher der Statistik, Puffer): gelten sofort. Ein gesenktes Limit rechnet an, was schon verbraucht wurde; ein erhöhtes schenkt nichts zurück. Browser, die über einem gesenkten Browser-Limit schon offen sind, bleiben verbunden (wie nach dem Entfernen eines Bandbreiten-Upgrades).
- **Reichweiten und Energie:** Jedes Netzwerk wird innerhalb einer Sekunde neu berechnet.

Eine Datei mit Syntaxfehler ersetzt NeoForge durch die Standardwerte (mit einer Sicherungskopie daneben) – die Standardwerte sind nur LAN, es öffnet sich also nichts aus Versehen.

## Was der Server protokolliert

Jede Kopplung, Freigabe, Sperre und Dashboard-Aktion steht als `[cccloud] …` im Server-Log.

