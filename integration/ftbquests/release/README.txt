CC: Cloud @VERSION@ - FTB Quests chapters for All the Mods 11
Deutsch: siehe unten.

WHAT IS THIS
Two quest chapters for FTB Quests, with English and German text:
"CC: Tweaked" (24 quests: computers, turtles, monitors, modems, first programs) and
"CC: Cloud" (39 quests: mainframe, uplinks, pairing a browser, bridges, relays,
upgrades, tablet, display). The quests are not inside the mod jar: FTB Quests keeps
every quest in the server's quest book (config/ftbquests/quests). Install them once
on the server; players get them from the server automatically. The chapters appear
at the end of the Tech group, after Ender IO and XyCraft.

INSTALL (Linux, macOS, or Git Bash / WSL on Windows)
  1. Unpack this zip anywhere.
  2. ./install.sh <server-folder>
     <server-folder> is the folder that contains config/ftbquests/quests
     (singleplayer: the instance folder). --dry-run only shows what it would do.
  3. Restart the server, or run /ftbquests reload as an operator.

The script first copies config/ftbquests/quests to quests.backup-<date>. It never
replaces a chapter that is not ours and never changes existing quest text (it only
adds missing text keys). Updating from an earlier quest zip: run it again with
--replace-text to also update our quest text. Undo: delete config/ftbquests/quests
and rename the backup folder back.

ONLY A FILE MANAGER (hosted servers without a shell)
  1. Download config/ftbquests/quests as a backup.
  2. Upload quests/chapters/cc_tweaked.json5 and cc_cloud.json5 into
     config/ftbquests/quests/chapters/. If a file with one of these names is already
     there and is not from CC: Cloud, stop.
  3. Upload quests/lang/en_us/chapters/cc_tweaked.json5 and cc_cloud.json5 into
     config/ftbquests/quests/lang/en_us/chapters/, the same for de_de (create
     missing folders).
  4. Open config/ftbquests/quests/lang/en_us/chapter.json5 and paste the entries of
     quests/lang/en_us/chapter.additions.json5 in front of its last "}" (the entry
     above them needs a comma at the end). The same for de_de; if
     lang/de_de/chapter.json5 does not exist, upload chapter.additions.json5 there
     and rename it to chapter.json5.
  5. Restart the server.

Not ATM11? The chapters belong to ATM11's Tech chapter group (id 2B51AC12041E3F89),
which install.sh adds when it is missing, and the milestone rewards draw from ATM11's
reward tables. Pack makers may ship and adapt the chapters (MPL-2.0, see LICENSE).


============================================================================
DEUTSCH

WAS IST DAS
Zwei Quest-Kapitel für FTB Quests, mit englischem und deutschem Text:
"CC: Tweaked" (24 Quests: Computer, Turtles, Monitore, Modems, erste Programme) und
"CC: Cloud" (39 Quests: Mainframe, Uplinks, Browser koppeln, Bridges, Relays,
Upgrades, Tablet, Display). Die Quests stecken nicht in der Mod-Jar: FTB Quests hält
alle Quests im Questbuch des Servers (config/ftbquests/quests). Einmal auf dem
Server installieren; die Spieler bekommen sie automatisch vom Server. Die Kapitel
stehen am Ende der Gruppe Tech, nach Ender IO und XyCraft.

INSTALLATION (Linux, macOS oder Git Bash / WSL unter Windows)
  1. Dieses Zip irgendwo entpacken.
  2. ./install.sh <Server-Ordner>
     <Server-Ordner> ist der Ordner, in dem config/ftbquests/quests liegt
     (Einzelspieler: der Instanz-Ordner). --dry-run zeigt nur, was passieren würde.
  3. Server neu starten oder als Operator /ftbquests reload ausführen.

Das Skript kopiert config/ftbquests/quests zuerst nach quests.backup-<Datum>. Es
ersetzt nie ein fremdes Kapitel und ändert nie vorhandene Quest-Texte (es ergänzt nur
fehlende Text-Schlüssel). Update von einem älteren Quest-Zip: noch einmal mit
--replace-text ausführen, dann werden auch unsere Quest-Texte aktualisiert.
Rückgängig: config/ftbquests/quests löschen und den Backup-Ordner zurückbenennen.

NUR EIN DATEIMANAGER (gehostete Server ohne Shell)
  1. config/ftbquests/quests als Backup herunterladen.
  2. quests/chapters/cc_tweaked.json5 und cc_cloud.json5 nach
     config/ftbquests/quests/chapters/ hochladen. Liegt dort schon eine Datei mit
     einem dieser Namen, die nicht von CC: Cloud ist: abbrechen.
  3. quests/lang/en_us/chapters/cc_tweaked.json5 und cc_cloud.json5 nach
     config/ftbquests/quests/lang/en_us/chapters/ hochladen, genauso für de_de
     (fehlende Ordner anlegen).
  4. config/ftbquests/quests/lang/en_us/chapter.json5 öffnen und die Einträge aus
     quests/lang/en_us/chapter.additions.json5 vor der letzten "}" einfügen (der
     Eintrag davor braucht am Ende ein Komma). Genauso für de_de; gibt es
     lang/de_de/chapter.json5 nicht, chapter.additions.json5 dorthin hochladen und in
     chapter.json5 umbenennen.
  5. Server neu starten.

Kein ATM11? Die Kapitel gehören zur Tech-Kapitelgruppe von ATM11 (ID
2B51AC12041E3F89), die install.sh anlegt, wenn sie fehlt, und die Meilenstein-
Belohnungen kommen aus den Belohnungstabellen von ATM11. Modpack-Ersteller dürfen die
Kapitel mitliefern und anpassen (MPL-2.0, siehe LICENSE).
