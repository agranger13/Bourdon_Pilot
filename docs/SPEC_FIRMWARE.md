# Spec : modifications du firmware pour AppControlleur

Document de travail pour faire évoluer le firmware **`DroneDelivery/super-octo-broccoli`** (C++, PlatformIO / Arduino, XIAO ESP32-S3). L'objectif est qu'il soit compatible avec l'application Android et plus sûr en vol.

> Rien n'est encore modifié dans le firmware : ce document décrit **quoi** faire et **pourquoi**.
> Référence côté app : [`CAHIER_DES_CHARGES.md`](CAHIER_DES_CHARGES.md).

---

## 1. État actuel (commit `947598d`)

> Relu le 2026-09-15. La copie de travail contient des modifications **non commitées** dans `UDPReceiver.cpp` (trace de chaque paquet, `hasReceived`), `Hostpot.cpp` (trace de démarrage de l'AP) et `DroneLogger.h` (nombre de clients de l'AP). Les numéros de ligne ci-dessous correspondent à cette copie de travail.

| Élément | Existant |
|---|---|
| Cartes | XIAO ESP32-S3 (`-e xiao-esp32s3`) et FireBeetle 2 ESP32-S3 (`-e firebeetle-esps3`), brochage dans `include/BoardPins.h` |
| Décodage CSV | `(raw − 511.5) / 511.5`, zone morte `|raw − 511| < 20` ; throttle `raw / 1023` |
| Consignes | roll/pitch × 30°, yaw × 180 °/s, throttle × 200 (sur 255) ; sous 20/255, moteurs = throttle sans PID |
| Mixage | M1 = T − R − P − Y, M2 = T + R − P + Y, M3 = T + R + P − Y, M4 = T − R + P + Y (M1 avant gauche, sens horaire) |
| Attitude | `roll = atan2(ay, az)`, `pitch = atan2(−ax, √(ay² + az²))`, `dt` en `millis()` |
| Verrou d'urgence | Actif au boot ; relâché au premier paquet avec `arm = 0` |
| Logs | Série 9600 bauds, rapport toutes les 10 s + trace de chaque paquet |
| Wi-Fi | Point d'accès `DRONE_IOT` / `dronepassword`, IP `192.168.4.1` |
| Liaison | UDP, port **4210**, réception seule (pas de télémétrie) |
| Format | Texte CSV `lx,ly,rx,ry,arm,emg` (0–1023, centre 512) ou JSON équivalent |
| Correspondance | lx → roll, ly → pitch, rx → yaw, ry → throttle (0–1023 → 0–1) |
| Boucle | `loop()` avec `delay(10)` : UDP, PID et moteurs dans la même boucle, cadence non maîtrisée |
| IMU | MPU-6050 (Adafruit), filtre complémentaire α = 0,96 |
| Moteurs | PWM LEDC 20 kHz, 8 bits, `MAX_THROTTLE` = 200/255 |
| Failsafe | Timeout de **10 s**, verrou d'urgence relâché seulement après un passage par « désarmé » (bonne idée, à garder) |

---

## 2. Problèmes relevés

Classés par gravité. **Bloquant** = à corriger avant de voler avec l'app.

| # | Gravité | Où | Problème | Correction (voir §) |
|---|---|---|---|---|
| P1 | 🔴 Bloquant | `src/main.cpp:31` | `while (!Serial)` avec `ARDUINO_USB_CDC_ON_BOOT=1` : sur batterie, sans PC branché, le `setup()` attend probablement indéfiniment. **À vérifier sur batterie seule.** | Attente bornée (§6.1) |
| P2 | 🔴 Bloquant | `include/UDPReceiver.h:8` | Failsafe à **10 s** : un drone qui perd le lien continue sur sa dernière consigne pendant 10 s | 300 ms (§5) |
| P3 | 🔴 Bloquant | `src/motor/DroneController.cpp:80` | Armement accepté quelle que soit la position du throttle : si l'app envoie ARM avec les gaz hauts, les moteurs démarrent directement | Conditions d'armement (§5.2) |
| P4 | 🟠 Majeur | `src/wifi/UDPReceiver.cpp:49` | `Serial.print` de **chaque paquet** (50 Hz) : ralentit la boucle et ajoute de la gigue | Logs limités (§6.4) |
| P5 | 🟠 Majeur | `src/motor/DroneController.cpp:113-124` | L'IMU n'est lue que si throttle ≥ 20. Le premier `dt` du filtre est alors énorme, l'attitude est fausse au décollage et la télémétrie est figée au sol | IMU lue à chaque tour (§6.2) |
| P6 | 🟠 Majeur | `src/motor/DroneController.cpp:7` + `src/motor/PIDController.cpp:30-32` | `dt` du PID fixé à 0,01 s alors que la période réelle varie (`delay(10)` + I²C + UDP). Intégrale et dérivée sont donc faussées | Boucle à cadence fixe + `dt` mesuré (§6.2) |
| P7 | 🟠 Majeur | `src/motor/PIDController.cpp:32` | Dérivée calculée sur l'erreur : pic de correction (*derivative kick*) à chaque mouvement brusque du joystick | Dérivée sur la mesure (§6.3) |
| P8 | 🟡 Moyen | `src/wifi/UDPReceiver.cpp:40` | Un seul paquet lu par tour : si la boucle ralentit, les paquets s'accumulent et la latence augmente | Vider la file, garder le plus récent (§4.4) |
| P9 | 🟡 Moyen | `GyroScoper` | Pas de calibration des offsets du gyroscope au démarrage : dérive de l'attitude | Calibration au boot (§6.2) |
| P10 | 🟡 Moyen | `src/motor/DroneController.cpp:11` | `armed(true)` dans le constructeur | Initialiser à `false` |
| P11 | 🟡 Moyen | Protocole | Ni séquence, ni CRC, ni version, ni télémétrie | Nouveau protocole (§4) |
| P12 | 🟢 Mineur | `src/main.cpp` | SSID et mot de passe en dur, mot de passe par défaut | §3 |
| P13 | 🟠 Majeur | `DroneController.cpp:131-161` + `GyroScoper.cpp:170-185` | Conventions de signe non documentées entre joystick, IMU et mixage (analyse ci-dessous) : la boucle de lacet semble inversée | Convention fixée + test au banc (§6.5) |
| P14 | 🟡 Moyen | `src/sensor/GyroScoper.cpp:50` | MPU-6050 absent : `while (true)` infini après le démarrage de l'AP. L'AP est visible mais le drone ne traite plus aucun paquet, sans que l'app puisse savoir pourquoi | Continuer en `DISARMED` avec le bit `IMU OK` à 0 |
| P15 | 🟢 Mineur | `GyroScoper.cpp:144` | `dt` du filtre en `millis()` : résolution 1 ms pour ~10 ms de période, soit ±10 % d'erreur d'intégration | `micros()` (§6.2) |
| P16 | 🟢 Mineur | `DroneController.h:87` + `DroneLogger.h:78` | `getYawRate()` renvoie des rad/s, affichés comme des °/s | Convertir dans le getter |

> À vérifier au banc (sans hélices) : les signes du mixage moteur (`DroneController.cpp`, calcul de motor1Speed…). Incliner le drone à la main vers l'avant doit **accélérer les moteurs avant**.

### Analyse des signes (P13)

Hypothèse : MPU-6050 monté à plat, composants vers le haut, **X vers l'avant**, donc Y vers la gauche et Z vers le haut (repère direct). Sortie PID positive quand `consigne > mesure`.

| Axe | Mesure positive | Sortie PID positive | Effet | Boucle |
|---|---|---|---|---|
| Pitch | nez vers le bas (`−ax`) | M3, M4 (arrière) accélèrent | nez vers le bas : la mesure augmente | ✅ stable ; consigne + = avant |
| Roll | côté droit vers le bas (`ay`) | M2, M3 (droite) accélèrent | côté droit monte : la mesure diminue | ❌ inversée si X est vers l'avant |
| Yaw | rotation anti-horaire vue de dessus (`gz`) | M2, M4 (hélices anti-horaires) accélèrent | couple de réaction horaire : la mesure diminue | ❌ inversée quel que soit le montage à plat |

Si le capteur est tourné de 180° (X vers l'arrière), c'est le roll qui devient stable et le pitch qui s'inverse. Le lacet, lui, reste inversé dans les deux cas, sauf si le sens de rotation des hélices est l'inverse de celui indiqué dans `main.cpp`.

**Convention imposée par l'app** (mode CSV comme binaire) : roll + = droite, pitch + = avant (nez vers le bas), yaw + = rotation horaire vue de dessus, throttle + = monter. C'est au firmware d'adapter ses signes pour la respecter.

**Décision (2026-09-15)** : le MPU-6050 n'étant pas encore fixé, il sera monté **à plat, composants vers le haut, axe X (sérigraphie) vers l'avant**. C'est le repère standard, et le pitch est déjà correct avec lui. Aucune orientation à plat ne corrige à la fois roll et lacet : il faudra donc, dans le firmware, **inverser la mesure de roll et celle de `yawRate`** avant le PID. Correction à confirmer par le test du §6.5 avant tout vol.

**Mise en œuvre (FW-5, corrigée en FW-5b le 2026-09-18)** :
- **Lacet** : `yawRate = −gyro.z` dans `GyroScoper`, puis `compute(yawSetpoint, yawRate)`.
- **Roll** : la mesure `GyroScoper.roll` reste dans la convention de l'app, c'est-à-dire `atan2(+ay, az)` fusionné avec `+gyro.x`, positif quand le côté droit descend. C'est cette valeur qui part dans la télémétrie et le HUD. L'inversion se fait à l'entrée du PID, sur la consigne **et** la mesure : `compute(−rollSetpoint, −roll)`. La consigne et la mesure restent ainsi comparables.
- **Défaut corrigé par FW-5b** : FW-5 avait en plus inversé le signe de `ay` (`atan2(−ay, az)`). Ce signe était faux : quand le côté droit descend, `ay = +g·sin φ > 0`. L'accéléromètre contredisait alors le gyroscope dans le filtre complémentaire et annulait l'inversion du PID, d'où la boucle de roulis divergente au banc.
- Retirer l'inversion du PID n'aurait pas été une bonne correction : l'aile basse aurait été corrigée, mais le joystick vers la droite aurait fait rouler le drone à gauche, et le HUD serait resté inversé.

---

## 3. Wi-Fi

| ID | Exigence |
|---|---|
| FW-WIFI-1 | Garder le mode AP, IP `192.168.4.1`, port UDP **4210** |
| FW-WIFI-2 | Limiter le point d'accès à **1 client** : `WiFi.softAP(ssid, pass, channel, hidden=0, max_connection=1)` |
| FW-WIFI-3 | `WiFi.setSleep(false)` pour limiter la latence |
| FW-WIFI-4 | SSID et mot de passe dans un fichier `include/Secrets.h`, ignoré par Git, avec un `Secrets.example.h` versionné. Mot de passe ≥ 8 caractères, différent du défaut |
| FW-WIFI-5 | Canal fixe configurable (1, 6 ou 11) |
| FW-WIFI-6 | **Verrouillage du pilote** : seule l'adresse IP et le port ayant fait le HELLO (§4.3) peuvent envoyer des commandes. Le verrou se libère après un failsafe |

L'app se connecte en utilisant le SSID et le mot de passe saisis dans ses réglages. Par défaut : `DRONE_IOT`.

---

## 4. Nouveau protocole binaire

Tous les paquets sont en **little-endian**, sans padding (`#pragma pack(push, 1)`).
Le CRC utilisé est **CRC-16/CCITT-FALSE** (polynôme 0x1021, valeur initiale 0xFFFF). Il porte sur tous les octets sauf les 2 derniers.
L'octet 1 contient `version << 4 | type`. Version du protocole : **1**.

### 4.1 CommandPacket (app → drone, type `0x1`, 50 Hz, 16 octets)

```cpp
#pragma pack(push, 1)
struct CommandPacket {
    uint8_t  magic;      // 0xD7
    uint8_t  verType;    // 0x11
    uint16_t seq;        // incrémenté à chaque envoi
    int16_t  roll;       // -1000..+1000  (joystick droit, horizontal)
    int16_t  pitch;      // -1000..+1000  (joystick droit, vertical, +1000 = avant)
    int16_t  yaw;        // -1000..+1000  (joystick gauche, horizontal, +1000 = droite)
    uint16_t throttle;   // 0..1000       (joystick gauche, vertical)
    uint8_t  flags;      // bit0 ARM, bit1 E-STOP, bit2 réservé (mode acro)
    uint8_t  reserved;   // 0
    uint16_t crc;
};
#pragma pack(pop)
```

Conversion vers les consignes actuelles :
- `rollSetpoint = roll / 1000.0 * MAX_ROLL_ANGLE`, et de même pour `pitch` ;
- `yawSetpoint = yaw / 1000.0 * MAX_YAW_RATE` ;
- `throttle = throttle / 1000.0 * MAX_THROTTLE`.

Les valeurs hors plage sont contraintes ; aucun paquet n'est rejeté pour cette raison.

### 4.2 TelemetryPacket (drone → app, type `0x2`, 10 Hz, 20 octets)

Envoyé à l'adresse IP et au port du pilote verrouillé.

```cpp
#pragma pack(push, 1)
struct TelemetryPacket {
    uint8_t  magic;       // 0xD7
    uint8_t  verType;     // 0x12
    uint16_t seq;         // séquence propre au drone
    uint16_t ackSeq;      // dernier seq de commande valide reçu (calcul perte/latence côté app)
    uint16_t vbatMv;      // tension batterie filtrée en mV (0 si non mesurée)
    int16_t  roll;        // attitude mesurée, centièmes de degré
    int16_t  pitch;       // centièmes de degré
    int16_t  yawRate;     // dixièmes de °/s
    uint8_t  state;       // voir §5.1 (0 DISARMED, 1 ARMED, 2 FAILSAFE, 3 ESTOP_LATCH)
    uint8_t  statusFlags; // bit0 IMU OK, bit1 batterie basse, bit2 batterie critique,
                          // bit3 arm refusé (throttle haut), bit4 calibration en cours
    int8_t   rssi;        // RSSI du client vu par l'AP (dBm), 0 si inconnu
    uint8_t  loopHz;      // fréquence réelle de la boucle / 2 (ex. 125 → 250 Hz)
    uint16_t crc;
};
#pragma pack(pop)
```

### 4.3 Handshake (type `0x3`)

- **HELLO** (app → drone, 6 octets) : `magic, verType=0x13, seq(u16), crc(u16)`.
- **HELLO_ACK** (drone → app, 10 octets) : `magic, verType=0x13, protoVersion(u8), fwMajor, fwMinor, fwPatch, boardId(u8: 1 = XIAO, 2 = FireBeetle), reserved(u8), crc(u16)`.
- Le drone n'accepte des CommandPacket qu'après un HELLO, venant de la même IP et du même port.

### 4.4 Règles de réception

1. **Vider la file** à chaque tour : `while (udp.parsePacket() > 0)`. Décoder chaque paquet et ne garder que le **dernier valide**.
2. Rejeter un paquet si la taille est mauvaise, si `magic` est différent de 0xD7, si la version n'est pas supportée ou si le CRC est faux. Incrémenter alors un compteur d'erreurs (affiché dans les logs périodiques).
3. **Séquence** : accepter si `(uint16_t)(seq - lastSeq)` est compris entre 1 et 32767. Après un failsafe ou un nouveau HELLO, accepter n'importe quelle séquence.
4. Mettre à jour `lastValidPacketMs` uniquement sur un paquet **valide**.

### 4.5 Transition depuis le CSV
- **Étape A** : le parser détecte le format au premier octet (`0xD7` = binaire, sinon CSV ou JSON actuels). L'app dispose aussi d'un mode « legacy CSV » pour tester tout de suite avec le firmware existant.
- **Étape B** : une fois le binaire validé en vol, supprimer le CSV et le JSON.

### 4.6 Plus tard : paramètres (type `0x4`)
Lecture et écriture des PID, des angles max et des seuils batterie depuis l'app, avec acquittement et sauvegarde en NVS (`Preferences`). **Uniquement si le drone est désarmé.** Le format sera spécifié au jalon J5.

---

## 5. Sécurité

### 5.1 Machine à états

| État | Moteurs | Entrée | Sortie |
|---|---|---|---|
| `DISARMED` | 0 | Boot, DISARM reçu, fin de verrou | → `ARMED` si les conditions §5.2 sont remplies |
| `ARMED` | PID actif | Armement valide | → `DISARMED` (ARM = 0) · → `FAILSAFE` (timeout) · → `ESTOP_LATCH` (E-STOP) |
| `FAILSAFE` | **0 immédiatement** | Aucun paquet valide depuis **300 ms** | → `DISARMED` quand des paquets valides reviennent **avec ARM = 0** |
| `ESTOP_LATCH` | 0 | Flag E-STOP reçu | → `DISARMED` quand E-STOP = 0 **et** ARM = 0 |

> Pourquoi couper les moteurs plutôt que descendre doucement en failsafe ? Il n'y a ni baromètre ni capteur d'altitude, donc aucune descente contrôlée possible. Sur un micro-drone de quelques dizaines de grammes, couper est le choix le plus sûr.

### 5.2 Conditions d'armement (toutes requises)
1. Front montant du flag ARM : on passe de 0 à 1. Un ARM déjà à 1 au premier paquet est **refusé**.
2. `throttle < 50` (sur 1000).
3. Lien valide, IMU OK, calibration terminée.
4. Batterie au-dessus du seuil critique (si la mesure existe).

En cas de refus : lever le bit `arm refusé` dans la télémétrie, pour que l'app puisse expliquer pourquoi.

### 5.3 Divers
- `stopAll()` avant toute initialisation (déjà le cas, à garder).
- En cas de reset du watchdog ou de brown-out : état `DISARMED` au boot (c'est déjà le comportement par défaut, à garder).

---

## 6. Boucle de vol et stabilisation

### 6.1 Démarrage
- Remplacer `while (!Serial)` par une attente bornée : `unsigned long t0 = millis(); while (!Serial && millis() - t0 < 1500) delay(10);`
- Séquence : moteurs à 0 → Wi-Fi AP → UDP → IMU → **calibration du gyroscope** → `DISARMED`.

### 6.2 Cadence fixe et IMU
- Boucle de contrôle à **250 Hz** (4 ms), cadencée avec `micros()` et sans `delay()` fixe.
- IMU lue **à chaque tour**, que le drone soit armé ou non : l'attitude reste toujours valide, et la télémétrie aussi.
- `dt` **mesuré** à chaque tour et transmis au PID : `compute(setpoint, input, dt)`.
- Mesurer la fréquence réelle de la boucle et l'exposer dans la télémétrie (`loopHz`). Si le MPU-6050 via la bibliothèque Adafruit plafonne la cadence, retenir la fréquence atteignable de façon **stable** (≥ 200 Hz visé).
- **Calibration** : au boot, drone immobile, moyenne de ~500 échantillons de gyroscope pour obtenir les offsets, puis soustraction à chaque lecture. LED clignotante pendant la calibration.
- Découpage conseillé à chaque tour :
  1. lecture UDP (§4.4) ;
  2. machine à états ;
  3. IMU ;
  4. PID + mixage ;
  5. télémétrie toutes les 100 ms ;
  6. logs.

### 6.3 PID
- Signature `compute(float setpoint, float input, float dt)`.
- Dérivée **sur la mesure** : `derivative = -(input - previousInput) / dt`. Option : filtre passe-bas sur le terme D.
- Garder l'anti-windup, et le reset en dessous du seuil de throttle.
- Garder le test natif `test/test_pid` et l'étendre : `dt` variable, absence de derivative kick.

### 6.4 Logs
- Supprimer la trace par paquet. Les logs série périodiques sont à ≤ 1 Hz.
- Macro `DRONE_DEBUG` (flag de build) pour activer les traces détaillées.

### 6.5 Validation des signes (P13), sans hélices
Chaque réponse attendue découle de la convention du §2. On arme avec un throttle juste au-dessus de 20/255 (PID actif), puis :

| Action | Moteurs qui doivent accélérer | État (banc du 2026-09-18, firmware FW-5b) |
|---|---|---|
| Incliner à la main le nez vers le bas | M1, M2 (avant) : le PID s'oppose | ✅ Vérifié (2026-09-17) |
| Incliner à la main le côté droit vers le bas | M2, M3 (droite) | ✅ Vérifié après FW-5b (en FW-5 : ❌ M1 et M4) |
| Tourner à la main dans le sens horaire vu de dessus | Ceux qui créent un couple anti-horaire : M1, M3 (hélices horaires) | ✅ Vérifié : M1 et M3 |
| Pousser le joystick droit vers l'avant, drone à plat | M3, M4 (arrière) | ✅ Vérifié |
| Pousser le joystick droit vers la droite | M1, M4 (gauche) | ✅ Vérifié après FW-5b |
| Pousser le joystick gauche vers la droite (yaw) | M2, M4 (hélices anti-horaires, couple horaire) | ✅ Vérifié |

> 🚨 **Aucun vol avec hélices avant que les six lignes soient vertes.**
> ✅ **Les six lignes sont vertes depuis le banc du 2026-09-18** : le verrou du
> premier vol est levé.
>
> **Banc du 2026-09-18** : en FW-5, le lacet était bon mais le **roulis était
> inversé** (aile droite abaissée → M1 et M4 au lieu de M2 et M3). Le défaut ne
> venait pas de l'inversion consigne + mesure à l'entrée du PID : elle est
> nécessaire avec ce mixage. Il venait du signe de `ay` dans la mesure de roll,
> inversé à tort en FW-5 (détail au §2, « Mise en œuvre »). **FW-5b** remet ce
> signe, sans toucher au mixage, au PID, au pitch ni au lacet. Les deux lignes
> de roulis (main + joystick) sont vertes après FW-5b.
>
> **Dérive d'attitude : ✅ vérifiée le 2026-09-18** — drone à plat et immobile,
> horizon stable sur plusieurs minutes (le relevé du 2026-09-17, 10 s carte posée
> de travers, ne comptait pas). La cadence de boucle est mesurée à **250 Hz
> stables**. Les sécurités de FW-2 (failsafe < 500 ms, ARM refusé gaz hauts,
> E-STOP) ont été **repassées sur le binaire FW-5b** : ✅.

Corriger en inversant la mesure concernée (`roll`, `pitch` ou `yawRate`) avant le PID, pas le mixage. Consigner le montage réel du MPU-6050 dans le README.

---

## 7. Mesure de la tension batterie (nouveau matériel)

### 7.1 Câblage proposé (XIAO ESP32-S3)
Broches libres après les moteurs (D0–D3) et l'I²C (D4–D5) : D6–D10.
- D6/D7 sont l'UART0 : à exclure.
- D8/D9 servent au repli I²C de diagnostic.
- Choix retenu : **D10 = GPIO9 (ADC1_CH8)**. L'ADC1 est obligatoire, car l'ADC2 est inutilisable quand le Wi-Fi est actif.

```
 VBAT (LiPo 1S, 3.0–4.2 V)
   │
  [R1 100 kΩ]
   │
   ├───────► D10 / GPIO9 (ADC)
   │    │
  [R2 100 kΩ]  [C 100 nF]
   │    │
  GND ──┴──
```
- Rapport 1/2 : 4,2 V donne 2,1 V sur l'ADC, dans la plage de l'atténuation 11 dB.
- Consommation permanente ≈ 21 µA : négligeable.
- Le condensateur de 100 nF stabilise la lecture (impédance de source élevée).
- Prélever VBAT **en amont** de l'interrupteur de puissance si possible, sinon juste après.

À intégrer dans la prochaine révision de `pcb_drone`. D'ici là, un montage en fils volants suffit.

### 7.2 Logiciel
- `analogSetPinAttenuation(9, ADC_11db)` puis `analogReadMilliVolts(9) * 2`. Cette fonction utilise la calibration eFuse.
- Lecture à 20 Hz, moyenne glissante sur ~1 s, exposée en `vbatMv`.
- Constante `VBAT_DIVIDER` corrigeable après mesure au multimètre.
- Seuils (sous charge, un 1S coreless chute beaucoup) :

| Niveau | Seuil filtré | Persistance | Action |
|---|---|---|---|
| Bas | < 3,50 V | 2 s | bit `batterie basse` → alerte dans l'app |
| Critique | < 3,30 V | 2 s | bit `batterie critique` → alerte forte + **armement refusé** |
| Coupure | < 3,00 V | 3 s | désarmement forcé (protection de la LiPo). Seuil à valider en vol |

- Constante `HAS_VBAT_SENSE` : si elle vaut 0, `vbatMv = 0` et les règles batterie sont ignorées.

---

## 8. Plan de mise en œuvre

| Étape | Contenu | Test de validation (sans hélices sauf mention) | État |
|---|---|---|---|
| FW-1 | P1 (attente série), P10, P4 (logs) | Le drone démarre sur batterie seule, l'AP est visible | ✅ Fait |
| FW-2 | Machine à états, failsafe 300 ms, conditions d'armement (P2, P3) | Arrêt du Wi-Fi du téléphone → moteurs à 0 en < 500 ms ; ARM refusé avec throttle haut | ✅ Fait |
| FW-3 | Protocole binaire + HELLO + détection legacy (§4) | App en mode binaire : consignes cohérentes dans les logs, compteur CRC à 0 | ✅ Fait |
| FW-4 | Télémétrie 10 Hz | L'app affiche l'attitude en inclinant le drone à la main | ✅ Fait, validé au banc le 2026-09-17 |
| FW-5 | Boucle 250 Hz, IMU permanente, calibration, PID à `dt` mesuré (P5–P9, P13, P15) | `loopHz` stable ; attitude qui ne dérive pas au repos ; test des signes du mixage | ✅ Fait, `loopHz` = 250 Hz stables. 🚨 Banc du 2026-09-18 : pitch et lacet ✅, **roulis inversé → FW-5b** |
| FW-5b | **Correction du signe de roulis** : mesure de roll remise à `atan2(+ay, az)`, cohérente avec `+gyro.x` ; l'inversion consigne + mesure à l'entrée du PID est conservée (§2, §6.5) | Aile droite abaissée → M1/M4 → **M2/M3** ; joystick droit vers la droite → M1, M4 | ✅ Fait, validé au banc le 2026-09-18. **Les 6 tests du §6.5 sont verts**, dérive au repos comprise |
| FW-7 | **Premier vol** avec hélices, en extérieur ou dans un espace dégagé | Stationnaire de 30 s | ⏳ **Prochaine étape** — les 6 tests du §6.5 sont verts, le verrou est levé |
| FW-6 | Mesure batterie (§7) | Écart < 50 mV avec le multimètre | ⏳ À faire **après FW-7** : le montage du pont diviseur n'est pas fait, et la mesure n'est pas nécessaire aux premiers vols d'essai, courts. Elle conditionne en revanche les **vols plus longs** (voir l'encadré ci-dessous) |
| FW-8 | Paramètres via l'app (§4.6) | PID modifiés depuis l'app et conservés après reboot | ⏳ À faire |
| FW-9 | Suppression du format CSV/JSON | L'app fonctionne en binaire uniquement | ⏳ À faire |

> **Décision (2026-09-18) : FW-6 passe après le premier vol.** Le pont diviseur
> n'est pas câblé et le montage demande du temps ; les premiers vols seront des
> **essais courts**, pour lesquels la mesure de tension n'est pas indispensable.
> FW-6 est donc la condition des **vols plus longs**, pas du premier vol.
>
> En attendant, aucune protection batterie n'existe : l'app affiche « NON
> MESURÉE » et n'émet aucune alerte tant que `vbatMv` vaut 0 (elle ne bloque pas
> l'armement pour autant). Le risque de brown-out en vol du registre de risques
> reste donc non couvert, et est compensé à la main :
> - tension au multimètre **avant et après** chaque vol, pas de redécollage sous **3,50 V** ;
> - vols **minutés et courts** (~1 min pour commencer), jamais « jusqu'à ce que ça faiblisse » ;
> - toute perte de puissance ressentie = atterrissage immédiat.

---

## 9. Organisation du code proposée

```
include/
  Protocol.h          structs CommandPacket / TelemetryPacket / Hello*, constantes, CRC
  LinkManager.h       réception UDP, validation, HELLO, verrou pilote, envoi télémétrie
  FlightState.h       machine à états + conditions d'armement
  BatteryMonitor.h    ADC, filtrage, seuils
  Secrets.example.h   SSID / mot de passe
src/
  link/Protocol.cpp       (pur C++, testable en env native)
  link/LinkManager.cpp    (remplace UDPReceiver)
  flight/FlightState.cpp  (pur C++, testable en env native)
  sensor/BatteryMonitor.cpp
test/
  test_protocol/     encodage/décodage, CRC, séquence (vecteurs partagés avec l'app Android)
  test_flight_state/ armement, failsafe, verrou E-STOP
```

> **Vecteurs de test partagés** : un fichier `protocol_vectors.json` (paquets connus en hexadécimal et valeurs attendues) est utilisé à la fois par les tests Unity du firmware et par les tests JUnit de l'app. On garantit ainsi que les deux côtés parlent exactement le même protocole.
