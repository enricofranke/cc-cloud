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

**Empfohlen: CC: Cloud Connect.** Ein Befehl im Spiel (Serverbetreiber, OP-Level 4, oder die Server-Konsole):

```
/cccloud internet connect mybase
```

Dein Dashboard ist dann unter **https://mybase.cccloud.dev** erreichbar – HTTPS, von überall, ohne Portfreigabe, ohne eigene Domain und ohne Proxy. Der Server baut eine Verbindung nach außen zum CC: Cloud-Relay auf und hält sie offen; an deinem Router ändert sich nichts. Such dir einen freien Namen aus (3–32 Zeichen: a–z, 0–9 und -); `/cccloud internet connect` ohne Namen schlägt einen vor. Es gilt sofort, ohne Neustart. In `serverconfig/cccloud-server.toml`: `internetAccess = "connect"` und `connectName = "mybase"` unter `[connect]`.

| Modus | Einfach | Sicher | Braucht |
|---|---|---|---|
| `connect` – **empfohlen** | ein Befehl | ja – HTTPS, Freigabe im Spiel | ausgehendes HTTPS (Port 443) |
| `off` (Standard) | nichts zu tun | ja – nur dein lokales Netz | – |
| `http` – Einfach-Modus | ein Befehl, ein Port | **nein**, siehe unten | TCP-Port 8787 weitergeleitet |
| HTTPS (Caddy, Cloudflare Tunnel, eigenes Zertifikat) | etwas Einrichtung | ja | eine Domain, siehe [Erweitert](#advanced) |

`/cccloud internet` zeigt den aktuellen Modus, die Adresse und ob sie online ist; `/cccloud internet off` schließt den Zugriff aus dem Internet wieder.

### CC: Cloud Connect

- **Es funktioniert einfach:** Jeder neue Browser braucht weiter **[Erlauben]** im Spiel, danach verhalten sich die Steuerelemente wie im LAN; PIN-geschützte fragen weiter nach ihrer PIN. Browser über Connect zählen als Internet-Adressen: Falsche Kopplungscodes und Anmeldungen sperren eine Adresse eine Weile.
- **Strenger, wenn du willst:** `internetRequirePin = true` in der Server-Config lässt jede Steuer-Aktion aus dem Internet nach der Mainframe-PIN fragen (einmal, dann 10 Minuten pro Browser); Mainframes ohne PIN sind von außen dann nur ansehbar (`/cccloud pin set <Ziffern>`).
- **Wo du es siehst:** Der Mainframe-Bildschirm zeigt die Adresse zuerst und ob Connect online ist; Kopplungslinks im Chat nutzen sie, damit sie auch auf dem Handy mit mobilen Daten funktionieren.
- **Der Name gehört dir:** Die Mod erzeugt einen Schlüssel in `<Serverordner>/cccloud/connect/` und weist ihn beim Relay nach; niemand sonst kann den Namen übernehmen. Server umziehen? Kopiere den Ordner mit. Ein Name, der 90 Tage nicht benutzt wird, wird frei; `/cccloud internet connect release` gibt ihn sofort frei und schaltet Connect aus.
- **Grenzen:** 20 gleichzeitig offene Dashboard-Tabs und 2 GB Datenverkehr pro Tag und Server. Die öffentliche API funktioniert über Connect (nicht mit `internetRequirePin`: ein Schlüssel kann keine PIN mitschicken); Live-Event-Streams (`/events`) laufen nicht darüber.
- **Wer was sehen kann:** HTTPS endet bei Cloudflare, wo der Relay läuft. Der Relay reicht deinen Dashboard-Verkehr weiter und könnte ihn technisch mitlesen; er wird vom Autor von CC: Cloud betrieben, speichert nichts und führt keine Anfrage-Logs. Die Dashboard-Seiten kommen immer vom Relay (die offiziellen Dateien deiner Mod-Version), nie von einem Minecraft-Server – so kann niemand eine gefälschte Anmeldeseite unter einer cccloud.dev-Adresse ablegen. Missbrauch meldest du über den CC: Cloud-Discord oder GitHub; Namen, die für Phishing genutzt werden, werden abgeschaltet. Wenn du dem Relay nicht vertrauen willst, nutze `/cccloud internet off` und einen der [erweiterten](#advanced) Wege.

### Einfaches HTTP – Einfach-Modus

```
/cccloud internet http
/cccloud internet http mybase.duckdns.org
```

Der Chat antwortet mit dem **Link** zum Öffnen, dem **Port**, den du im Router weiterleitest, und **wie du es testest** (Handy mit ausgeschaltetem WLAN). Dasselbe in der Config: `internetAccess = "http"` (optional `internetDomain = "meinebasis.duckdns.org"` für einen festen Namen). Es ist nicht verschlüsselt – nimm lieber Connect.

### Einfaches HTTP: was passieren kann

> **Achtung:** Einfaches HTTP ist nicht verschlüsselt: Jeder auf dem Weg (dasselbe WLAN, ein Hotspot, dein Provider) kann Anmeldung und PIN mitlesen und das Dashboard dann wie du benutzen.

Es funktioniert einfach: Steuerelemente verhalten sich wie im LAN, PIN-geschützte fragen weiter nach ihrer PIN. Was bleibt: Neue Browser brauchen **[Erlauben]** im Spiel, ein Browser, der 24 Stunden nicht benutzt wurde, wird abgemeldet, wenn er aus dem Internet kommt (`publicTokenIdleHours`), falsche Kopplungscodes und Anmeldungen sperren eine Adresse eine Weile, Freigabe-Links bleiben nur lesend und API-Schlüssel können über einfaches HTTP nichts steuern.

Strenger: `internetRequirePin = true` in der Server-Config lässt jede Steuer-Aktion aus dem Internet nach der Mainframe-PIN fragen (einmal, dann 10 Minuten pro Browser); Mainframes ohne PIN sind von außen dann nur ansehbar. Sicherer: HTTPS – [CC: Cloud Connect](#connect) oder einer der [erweiterten](#advanced) Wege.

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

## Item-Icons auf einem dedizierten Server

Das Dashboard zeigt echte Item-Icons. Die besten kommen aus dem Spiel selbst: Jeder Spieler mit CC: Cloud rendert Icons im Hintergrund (bei einem großen Pack ein paar Minuten, ohne FPS zu kosten), und der Server bewahrt sie in `<world>/cccloud/icons` auf.

Damit ein frischer Server schon Icons hat, bevor jemand beitritt, zeichnet er die einfachen direkt nach dem Start selbst, auf einem einzigen Hintergrund-Thread mit niedriger Priorität:

- **Zeichnet der Server:** flache Items (Barren, Werkzeuge, die meisten Mod-Items) und einfache Blöcke (Stein, Erze, Stämme, Speicherblöcke, Maschinen mit Würfelmodell).
- **Bleibt für die Spieler:** Truhen, Schilde, Köpfe und andere Spezialmodelle, Stufen, Treppen, Zäune und andere Formen, 3D-Maschinenmodelle. Sie zeigen eine Buchstaben-Kachel, bis ein Spieler mit der Mod beitritt.

Die Renderings der Spieler ersetzen die des Servers später; sie sehen genau wie im Inventar aus. In All the Mods 11 zeichnet der Server etwa 27.000 von 38.500 Items selbst (29.000 mit Vanilla-Texturen), mit etwa 10 Sekunden CPU-Zeit auf einem Kern, nie auf dem Haupt-Thread des Servers; die Ticks merken davon nichts.

**Vanilla-Items** brauchen die Texturen von Minecraft, und ein dedizierter Server hat keine (das Server-Jar enthält keine Texturen). Beide Wege dahin entscheidest du:

```toml
[icons]
renderIconsOnServer = true      # default: draw flat items and plain blocks at server start
downloadVanillaAssets = false   # true: get Minecraft's client jar from Mojang once, for vanilla icons
vanillaAssetsJar = ""           # or: the path to a client jar you own
```

- `downloadVanillaAssets = true` lädt einmal das offizielle Minecraft-Client-Jar genau deiner Serverversion von Mojangs Servern (etwa 40 MB, gegen Mojangs SHA-1 geprüft, abgelegt in `<server>/cccloud/cache`). Es wird nur lokal für Texturen gelesen; nichts davon wird weitergegeben – Browser bekommen nur die 32×32-Icons. Mit dem Einschalten stimmst du Mojangs Bedingungen zu (der [Minecraft-EULA](https://www.minecraft.net/eula)).
- `vanillaAssetsJar` zeigt auf ein Client-Jar, das du schon hast, z. B. `.minecraft/versions/26.1.2/26.1.2.jar`.

Ohne beides bekommen Vanilla-Items ihre Icons, sobald der erste Spieler mit der Mod beitritt; das Server-Log sagt das einmal beim Start. Die Einstellungen stehen im Abschnitt `[icons]` von `<world>/serverconfig/cccloud-server.toml` und gelten im laufenden Betrieb: Nach einer Änderung sucht der Server erneut nach fehlenden Icons (Vanilla-Items werden also gleich gezeichnet, nachdem du den Download erlaubt hast). Im Einzelspieler und im LAN rendert das Spiel des Hosts alle Icons, dort bleibt der Server-Renderer aus.

`/cccloud icons status` zeigt, wie viele Icons von Spielern kommen, wie viele vom Server und wie viele fehlen, und wie weit der Server-Renderer ist. `/cccloud icons rebuild` zeichnet alles neu, `/cccloud icons clear` löscht alle Icons.

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
- **Item-Icons** (`[icons]`): Der Server öffnet die Vanilla-Texturen neu (lädt sie einmal, wenn du den Download gerade erlaubt hast) und zeichnet die Icons, die noch fehlen; das Ausschalten des Server-Renderers stoppt ihn, die gezeichneten Icons bleiben.

Eine Datei mit Syntaxfehler ersetzt NeoForge durch die Standardwerte (mit einer Sicherungskopie daneben) – die Standardwerte sind nur LAN, es öffnet sich also nichts aus Versehen.

## Was der Server protokolliert

Jede Kopplung, Freigabe, Sperre und Dashboard-Aktion steht als `[cccloud] …` im Server-Log.

