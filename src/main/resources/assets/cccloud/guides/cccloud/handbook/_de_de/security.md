---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Sicherheit & Hosting"
  position: 9
  icon: cccloud:quantum_core
---

# Sicherheit und Hosting

Über das Dashboard lässt sich deine Welt steuern – deshalb ist es wie ein echter Webdienst geschützt.

## Standard: nur LAN

Ab Werk können nur Geräte im lokalen Netzwerk des Servers (und der Host selbst) das Dashboard öffnen. Alle anderen bekommen „LAN only“.

```toml
[web]
webAllowInternet = false   # default: only LAN addresses may connect
```

Die Konfigurationsdatei ist `<welt>/serverconfig/cccloud-server.toml`; Änderungen an `[web]` brauchen einen Server-Neustart.

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

## Zugriff aus dem Internet

> **Achtung:** Unverschlüsseltes HTTP über das Internet überträgt Zugangstoken und PIN im Klartext. Nutze HTTPS (Caddy, Cloudflare Tunnel oder TLS im Mod).

Setze `webAllowInternet = true` und wähle eine der folgenden Varianten. Läuft das Dashboard per HTTP über eine öffentliche Adresse, zeigt es eine Warnung.

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

### Port direkt freigeben

Nur für schnelle Tests: `webAllowInternet = true`, `publicUrl = "http://mc.example.com:8787"` setzen und TCP 8787 im Panel deines Hosters freigeben.

## Was der Server protokolliert

Jede Kopplung, Freigabe, Sperre und Dashboard-Aktion steht als `[cccloud] …` im Server-Log.

