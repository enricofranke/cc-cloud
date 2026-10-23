---
# Aus der Dashboard-Doku erzeugt (tools/gen_handbook.ts) – Änderungen in cloud/web/src/docs/content machen.
navigation:
  title: "Konten, Rollen & Teilen"
  position: 8
  icon: cccloud:cloud_link_card
---

# Konten, Rollen und Teilen

Ein **Gerät** ist jeder Browser – auf PC, Tablet oder Handy. Jede Seite des Dashboards funktioniert überall gleich, mit Maus, Tastatur oder Touch.

## Spieler verknüpfen

1. Tippe in Minecraft `/cccloud link` (oder nutze „Link this browser“ im Mainframe-Bildschirm). Du bekommst einen Einmal-Code (10 Minuten).
2. Dashboard öffnen, Code und einen Namen für diesen Browser eingeben, **Pair device** anklicken.
3. Im Chat auf **[Allow]** klicken.

Der Browser ist jetzt mit **deinem Spieler** verknüpft, nicht mit einem einzelnen Mainframe: Er zeigt jeden Mainframe, auf dem du eine Rolle hast, und du wechselst über den Mainframe-Namen oben in der Kopfzeile. Browser, die vorher mit einem Mainframe-Code gekoppelt wurden, funktionieren für diesen einen Mainframe weiter.

## Rollen

| Rolle | Darf |
|---|---|
| viewer | Werte und Meldungen sehen – keine Bedienung, keine Koordinaten |
| operator | viewer + Buttons, Schalter, Schieberegler, Craften, Regeln ein-/ausschalten |
| admin | operator + Geräte, Rollen, Layout bearbeiten, Freigabe-Links, PIN, Regeln, Terminal |
| owner | der Spieler, der den Mainframe aufgestellt hat: admin + übertragen und löschen |

Rollen kommen aus mehreren Quellen – **die höchste gilt**: der Besitzer, einem Spieler gegebene Rollen (`/cccloud role <spieler> <rolle>`, Mainframe-Bildschirm oder Seite **Access**), Team-Ränge und Server-Rechte. Was du nicht darfst, ist ausgeblendet oder deaktiviert, mit Begründung.

## Teams

Ein Mainframe kann einem Team aus **FTB Teams**, **Open Parties and Claims** oder einem Vanilla-Scoreboard-Team zugeordnet werden. Jeder Team-Rang bekommt eine Rolle auf diesem Mainframe (Standard: owner/officer/leader → admin, member → operator, ally → viewer). Tritt jemand ein oder aus, ändert sich sein Zugriff sofort.

## Die Seite Access

Admins öffnen sie über das Mainframe-Menü in der Kopfzeile (**Access & roles**):

- die Liste der Spieler mit Rolle und Herkunft der Rolle
- direkt vergebene Rollen ändern oder entfernen, Spieler per Name hinzufügen
- ein Team zuordnen und die Rolle pro Team-Rang wählen

## Geräte

**Devices** im Kopfzeilen-Menü listet jeden Browser, der mit deinem Spieler verknüpft ist – Name, Typ (Computer, Tablet, Handy), letzte Adresse und letzte Nutzung. Ein verlorenes oder altes Gerät dort entfernen, oder im Spiel mit `/cccloud devices` und `/cccloud revoke`.

## Freigabe-Links

Admins können eine **schreibgeschützte** Ansicht teilen – für ein Stream-Overlay, einen Freund oder ein Wand-Display:

1. Mainframe-Menü → **Share links** → **New link**.
2. Ganze Seiten oder einzelne Karten wählen, die Laufzeit (1 Stunde, 24 Stunden, 7 Tage oder unbegrenzt), wie viele gleichzeitig zusehen dürfen, und optional **genaue Zahlen ausblenden** (nur Anzeigen).
3. Link kopieren – er wird nur einmal angezeigt.

Der geheime Teil hinter `#` landet nie in den Server-Logs und verschwindet aus der Adresszeile, sobald die Seite offen ist. Wer den Link hat, sieht **„Shared by …“** und kann nichts drücken: Es gibt gar keine Bedienelemente, und der Server lehnt jede Aktion über einen Freigabe-Link ab. Nie enthalten: Koordinaten, Item-Suche, Netzwerkkarte und Verlaufs-Export. Ein Widerruf trennt alle Zuschauer sofort.

## Server-Rechte

Server-Admins steuern CC: Cloud mit **LuckPerms** oder **FTB Ranks** (oder über das OP-Level, wenn kein Rechte-Mod installiert ist):

| Node | Standard | Erlaubt |
|---|---|---|
| `cccloud.use` | alle | Blöcke aufstellen und nutzen, Browser verknüpfen |
| `cccloud.admin.view` | OP | jeden Mainframe ansehen |
| `cccloud.admin.operate` | OP | jeden Mainframe bedienen |
| `cccloud.admin.manage` | OP | Geräte und Rollen jedes Mainframes verwalten |
| `cccloud.limit.mainframes` | 3 | Mainframes pro Spieler |

Schaut ein Admin auf einen fremden Mainframe, sieht der Besitzer das im Protokoll.

## Tastatur

Tab springt durch alles, Enter oder Leertaste löst aus, Escape schließt Dialoge und Menüs, die Pfeiltasten wechseln zwischen Seiten-Tabs und Menüeinträgen, und „Skip to content“ springt an der Kopfzeile vorbei.

