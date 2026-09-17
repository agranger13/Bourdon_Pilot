# AppControlleur : cahier des charges

Application Android pour piloter le micro-drone maison avec des joysticks virtuels.

> **Contexte (mis à jour le 2026-09-15)**
> - **Drone** : carte `pcb_drone`, XIAO ESP32-S3, IMU MPU-6050, 4 moteurs coreless brushed (hélices 49 mm) pilotés par MOSFET, LiPo 1S.
> - **Firmware de référence** : `DroneDelivery/super-octo-broccoli` (C++, PlatformIO/Arduino). Le drone crée le point d'accès Wi-Fi **`DRONE_IOT`** (`192.168.4.1`) et écoute en **UDP sur le port 4210**.
> - **Protocole actuel** : texte CSV `lx,ly,rx,ry,arm,emg` (valeurs 0–1023, centre 512).
> - **Modifications du firmware** : détaillées à part dans [`SPEC_FIRMWARE.md`](SPEC_FIRMWARE.md).

### Décisions prises
| Sujet | Décision |
|---|---|
| Mode de pilotage | **Mode 2** (le plus répandu) : gaz et lacet à gauche, tangage et roulis à droite |
| Firmware | Rester en **C++** (super-octo-broccoli) |
| Mesure batterie | Pas encore câblée : **à ajouter** (pont diviseur sur D10/GPIO9, voir SPEC_FIRMWARE §7). L'app gère l'absence de mesure |
| Connexion Wi-Fi | **L'app se connecte elle-même** au Wi-Fi du drone |

---

## 1. Objectifs

### 1.1 Objectif principal
Faire décoller, stabiliser, déplacer et poser le drone depuis un téléphone Android, **en sécurité**. Les joysticks doivent réagir de façon fluide.

### 1.2 Objectifs découpés par jalons

| Jalon | Objectif | Critère de réussite | Dépend du firmware ? |
|---|---|---|---|
| **J0 : Banc réseau** | L'app se connecte au Wi-Fi du drone et envoie des paquets **au format CSV actuel** | Les logs série du drone affichent les valeurs | Non : firmware actuel |
| **J1 : MVP joysticks** | 2 joysticks, ARM/DISARM, arrêt d'urgence, toujours en CSV | Les moteurs réagissent, drone **sans hélices** | Non (FW-1 conseillé) |
| **J2 : Sécurité** | Protocole binaire, failsafe 300 ms, armement sécurisé | Wi-Fi coupé : moteurs arrêtés en < 500 ms ; ARM refusé avec les gaz hauts | FW-2, FW-3 |
| **J3 : Télémétrie** | Attitude, qualité du lien, état confirmé par le drone, batterie | L'horizon artificiel suit le drone incliné à la main | FW-4 à FW-6 |
| **J4 : Premier vol** | Vol stationnaire à faible hauteur | 30 s de stationnaire | FW-5, FW-7 |
| **J5 : Réglages** | PID, trims, expo et sensibilité depuis l'app | Réglages conservés après reboot du drone | FW-8 |
| **J6 : Confort** | Manette Bluetooth, retour haptique | Pilotage avec une manette physique | Non |

> Règle d'or : **pas d'hélices avant la validation de J2.**

---

## 2. Périmètre

**Inclus**
- Application Android native sur téléphone, en mode paysage.
- Connexion automatique au point d'accès Wi-Fi du drone.
- Pilotage manuel en mode stabilisé (angle).
- Télémétrie et réglages.

**Hors périmètre (pour l'instant)**
- iOS, retour vidéo FPV, GPS, vol autonome.
- Liaison longue portée (ELRS, LoRa).
- Contrôle via Internet.

---

## 3. Exigences fonctionnelles

### 3.1 Connexion
| ID | Exigence | Priorité |
|---|---|---|
| F-CON-1 | **L'app se connecte seule au Wi-Fi du drone** via `WifiNetworkSpecifier` (SSID `DRONE_IOT` par défaut, mot de passe configurable) | Must |
| F-CON-2 | Tout le trafic UDP passe par ce réseau, même sans Internet (`bindSocket`, voir §6.3) | Must |
| F-CON-3 | État du lien affiché : recherche du drone, connecté au Wi-Fi, drone répond, lien dégradé, perdu | Must |
| F-CON-4 | Handshake HELLO : version du firmware, compatibilité du protocole (mode binaire) | Should |
| F-CON-5 | Reconnexion automatique si le Wi-Fi tombe, **sans réarmement automatique** | Must |
| F-CON-6 | Sélecteur de protocole : **Legacy CSV** (firmware actuel) ou **Binaire v1** (firmware modifié) | Must |

### 3.2 Pilotage (Mode 2)
| ID | Exigence | Priorité |
|---|---|---|
| F-PIL-1 | **Joystick gauche** : throttle (vertical, **sans retour au centre**) et yaw (horizontal, retour au centre) | Must |
| F-PIL-2 | **Joystick droit** : pitch (vertical) et roll (horizontal), retour au centre sur les deux axes | Must |
| F-PIL-3 | Multi-touch : les deux joysticks utilisables simultanément | Must |
| F-PIL-4 | Envoi à fréquence fixe (**50 Hz**), même si les doigts ne bougent pas | Must |
| F-PIL-5 | Joysticks « flottants » : le centre se place là où le pouce touche la zone | Could |
| F-PIL-6 | Zone morte, courbe expo et sensibilité réglables | Should |
| F-PIL-7 | Trims roll, pitch et yaw | Should |
| F-PIL-8 | Support d'une manette Bluetooth (`InputDevice` / `MotionEvent`) | Could |

### 3.3 Sécurité (critique)
| ID | Exigence | Priorité |
|---|---|---|
| F-SEC-1 | **Armement** : uniquement si throttle = 0, avec un geste volontaire (appui long 1 s ou glissement) | Must |
| F-SEC-2 | **Arrêt d'urgence** : gros bouton rouge toujours visible, qui coupe immédiatement (E-STOP envoyé plusieurs fois de suite) | Must |
| F-SEC-3 | **Failsafe drone** : 300 ms sans paquet valide, moteurs coupés (côté firmware, voir SPEC_FIRMWARE §5) | Must |
| F-SEC-4 | **Failsafe app** : app en arrière-plan, écran éteint ou fermeture → DISARM envoyé plusieurs fois puis arrêt de l'émission | Must |
| F-SEC-5 | Numéro de séquence et CRC dans chaque paquet (mode binaire) | Must |
| F-SEC-6 | Au lancement et après chaque reconnexion, l'app démarre **désarmée, gaz à zéro** | Must |
| F-SEC-7 | Afficher la raison d'un refus d'armement renvoyée par le drone (gaz hauts, batterie critique, IMU…) | Should |
| F-SEC-8 | Écran maintenu allumé pendant le pilotage | Must |

### 3.4 Télémétrie (mode binaire)
| ID | Exigence | Priorité |
|---|---|---|
| F-TEL-1 | État armé/désarmé/failsafe **confirmé par le drone** | Must |
| F-TEL-2 | Tension batterie avec niveaux bas et critique (alerte visuelle et vibration). Masquée si le drone ne la mesure pas (`vbatMv = 0`) | Should |
| F-TEL-3 | Horizon artificiel (roll et pitch mesurés) | Could |
| F-TEL-4 | Qualité du lien : perte de paquets (via `ackSeq`), RSSI, fréquence de boucle du drone | Should |

### 3.5 Réglages
| ID | Exigence | Priorité |
|---|---|---|
| F-REG-1 | Écran Paramètres : SSID, mot de passe, IP et port (défaut `192.168.4.1:4210`), protocole, expo, zone morte | Must |
| F-REG-2 | Réglage des PID envoyé au drone, **uniquement s'il est désarmé** | Could |
| F-REG-3 | Paramètres de l'app sauvegardés (DataStore) | Should |

---

## 4. Exigences non fonctionnelles

| ID | Exigence | Cible |
|---|---|---|
| NF-1 | Latence entre le geste et la réception par le drone | < 50 ms (p95) |
| NF-2 | Fréquence d'émission | 50 Hz stable (gigue < 5 ms) |
| NF-3 | Portée utile | ≥ 20 m |
| NF-4 | Android minimum | **Android 10 (API 29)**, requis par `WifiNetworkSpecifier` ; cible : dernière API |
| NF-5 | Orientation | Paysage verrouillé, plein écran immersif |
| NF-6 | Fiabilité | Aucun crash pendant 30 min d'utilisation continue |
| NF-7 | Batterie du téléphone | Émission arrêtée hors de l'écran de pilotage |
| NF-8 | Testabilité | Protocole et logique joystick testés sans drone : tests unitaires, simulateur, vecteurs partagés avec le firmware |

---

## 5. Architecture

```
┌──────────── Téléphone Android ─────────────┐   Wi-Fi AP DRONE_IOT  ┌──── Drone XIAO ESP32-S3 (C++) ────┐
│ UI (Compose)                               │                       │                                   │
│  ├─ Joystick ×2      ─┐                    │ UDP :4210 Command     │ LinkManager                       │
│  ├─ ARM / E-STOP      ├─► ControlViewModel ┼──────── 50 Hz ───────►│  ├─ CRC, séquence, verrou pilote  │
│  └─ HUD télémétrie ◄──┘         │          │                       │  └─► FlightState (failsafe 300ms) │
│                                 ▼          │ UDP Telemetry         │        └─► DroneController (PID)  │
│                  LinkManager (coroutines) ◄┼──────── 10 Hz ────────┤              └─► MotorController  │
│                  ├─ WifiConnector          │                       │ GyroScoper, BatteryMonitor        │
│                  ├─ DatagramSocket         │                       │                                   │
│                  └─ ProtocolCodec          │                       │                                   │
│                     (LegacyCsv | BinaryV1) │                       │                                   │
└────────────────────────────────────────────┘                       └───────────────────────────────────┘
```

Couches de l'application (MVVM) :
- **ui/** : écrans Compose, `Joystick`, HUD, paramètres.
- **control/** : `ControlViewModel` (axes, armement, expo, trims), sous forme de `StateFlow`.
- **link/** :
  - `WifiConnector` : `WifiNetworkSpecifier` et suivi du `Network` ;
  - `LinkManager` : boucle 50 Hz, réception, surveillance du lien.
- **protocol/** : interface `ProtocolCodec` avec deux implémentations, `LegacyCsvCodec` et `BinaryV1Codec`. En **Kotlin pur**, testable.
- **data/** : paramètres (DataStore).

---

## 6. Protocole de communication

### 6.1 Legacy CSV (firmware actuel, jalons J0–J1)
Chaîne ASCII `lx,ly,rx,ry,arm,emg`, sans retour à la ligne :

| Champ | Axe | Conversion depuis l'app (valeur normalisée v) |
|---|---|---|
| `lx` | roll | `floor(511.5 + v × 511.5 + 0.5)`, v ∈ [−1, 1] (centre 512) |
| `ly` | pitch | idem, +1 = avant (signe à valider au banc, voir SPEC_FIRMWARE P13) |
| `rx` | yaw | idem, +1 = droite |
| `ry` | throttle | `floor(v × 1023 + 0.5)`, v ∈ [0, 1] |
| `arm` / `emg` | flags | `0` ou `1` |

Comportement du firmware actuel, relevé dans `UDPReceiver.cpp` et `DroneController.cpp` (commit `947598d`) :
- **Décodage** : `(raw − 511.5) / 511.5` pour les axes centrés, `raw / 1023` pour le throttle. L'encodage ci-dessus en est l'inverse exact.
- **Zone morte firmware** : `|raw − 511| < 20` donne 0, soit ≈ ±3,9 % sans remise à l'échelle (saut de 0 à 0,038). En Legacy CSV, les petites corrections sous ce seuil sont perdues.
- **Consignes** : roll et pitch × 30°, yaw × 180 °/s, throttle × 200/255. **Sous 20/255** (gaz < 10 %), les 4 moteurs reçoivent directement le throttle, sans PID.
- **Verrou d'urgence au démarrage** : au boot, et après toute perte de lien (10 s sans paquet) ou tout `emg = 1`, les moteurs restent verrouillés tant qu'un paquet avec `arm = 0` n'a pas été reçu. L'app doit donc commencer par émettre désarmée (F-SEC-6), sinon ARM n'a aucun effet.
- Format détecté par la présence d'une virgule (CSV) ou d'une accolade (JSON `{"lx":…}`). Le paquet est tronqué à 255 octets. Chaque paquet est tracé sur le port série (9600 bauds).

⚠️ Dans ce mode, le firmware actuel n'a qu'un failsafe de 10 s et ne vérifie pas les gaz à l'armement. **Mode réservé aux tests sans hélices.** L'app affiche un bandeau d'avertissement.

### 6.2 Binaire v1 (firmware modifié, à partir de J2)
Spécification complète dans **SPEC_FIRMWARE §4** : CommandPacket de 16 octets, TelemetryPacket de 20 octets, HELLO/HELLO_ACK, CRC-16/CCITT-FALSE. Côté Kotlin, l'encodage se fait avec `ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)`.

### 6.3 Connexion Wi-Fi automatique : points Android importants
- `WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(pass)`, puis `ConnectivityManager.requestNetwork(request, callback)`.
- Android affiche une **fenêtre de confirmation système** à la première connexion. Elle est normale et on ne peut pas la contourner.
- Le réseau obtenu est **propre à l'app** et sans Internet. Il faut lier le socket avec `network.bindSocket(datagramSocket)`, sinon les paquets partent en 4G/5G.
- La connexion se libère quand l'app appelle `unregisterNetworkCallback` : le faire en quittant l'écran de pilotage.
- Permissions à déclarer : `CHANGE_NETWORK_STATE`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `INTERNET`, et selon la version d'Android, `NEARBY_WIFI_DEVICES` (13+) ou la localisation. **À valider sur ton téléphone dès J0.**
- Solution de repli : bouton « Connexion manuelle » qui ouvre les réglages Wi-Fi d'Android.

---

## 7. Choix technologiques

### 7.1 Application Android

| Sujet | Choix recommandé | Alternatives écartées |
|---|---|---|
| Langage | **Kotlin** | Java, Dart/Flutter |
| Framework | **Natif Android + Jetpack Compose** | Flutter, React Native |
| Joysticks | Composant Compose sur mesure (`Canvas` + `pointerInput`, multi-touch) | Bibliothèques tierces peu maintenues |
| Architecture | MVVM, `ViewModel` + `StateFlow` | — |
| Réseau | **Kotlin Coroutines** + `DatagramSocket` (Dispatchers.IO) | Ktor-network |
| Wi-Fi | `WifiNetworkSpecifier` + `ConnectivityManager` | — |
| Persistance | Jetpack **DataStore** | SharedPreferences |
| Injection de dépendances | Manuelle au début, **Hilt** si l'app grossit | Koin |
| Build | Gradle Kotlin DSL + version catalog | — |
| IDE | **Android Studio** | — |
| Tests | JUnit + Turbine ; **vecteurs de protocole partagés** avec le firmware (`protocol_vectors.json`) | — |
| Qualité | ktlint ou detekt, GitHub Actions | — |

**Pourquoi le natif Kotlin ?** Les points sensibles sont tous des API Android : `WifiNetworkSpecifier` et `bindSocket`, cycle de vie pour le failsafe, manettes Bluetooth, haptique. Flutter ne serait pertinent que si iOS entrait dans le périmètre.

### 7.2 Côté drone
Voir **[`SPEC_FIRMWARE.md`](SPEC_FIRMWARE.md)**. Les points bloquants à corriger avant de voler avec l'app :
- attente `while (!Serial)` au démarrage ;
- failsafe de 10 s ;
- armement possible avec les gaz hauts.

### 7.3 Outils
- **Simulateur de drone sur PC** (Python, `tools/drone_sim.py`) : il parle les deux protocoles et renvoie une fausse télémétrie.
- **Wireshark** pour inspecter les paquets.
- **Git** dès le premier commit.

---

## 8. Plan de réalisation

> **Avancement (2026-09-17)** : étapes 1 à 8 terminées ; étape 9 codée côté app, FW-4 fait (validation au banc à faire). Prochaine étape : 10.

| Étape | Contenu | Livrable | État |
|---|---|---|---|
| 1 | Projet Android Studio (Compose, minSdk 29, paysage) + Git | Squelette qui compile | ✅ Fait |
| 2 | `protocol/` : `LegacyCsvCodec` et `BinaryV1Codec`, CRC, tests + `protocol_vectors.json` | Tests verts | ✅ Fait |
| 3 | Simulateur Python (CSV et binaire) | `tools/drone_sim.py` | ✅ Fait |
| 4 | `WifiConnector` : connexion auto à `DRONE_IOT` + `bindSocket` | Connexion validée sur le téléphone | ✅ Fait |
| 5 | Composant `Joystick` (multi-touch, retour au centre configurable) | Écran de démo | ✅ Fait |
| 6 | `LinkManager` 50 Hz + écran de pilotage en **Legacy CSV** | **J0 et J1** avec le firmware actuel, sans hélices | ✅ Fait |
| 7 | *Firmware FW-1 à FW-3* (voir SPEC_FIRMWARE §8) | Drone en binaire v1 sécurisé | ✅ Fait |
| 8 | Maquettes **Claude Design** (connexion, pilotage désarmé/armé, alertes, paramètres, design system), puis intégration dans le thème Compose (`Theme.kt` : couleurs, typo, composants ARM / E-STOP / barre de statut) | Écrans conformes aux maquettes, logique inchangée | ✅ Fait |
| 9 | Mode binaire dans l'app + HELLO + affichage de l'état confirmé | **J2** | 🟡 Codé et testé au simulateur ; FW-4 fait, **validation sur le drone à faire** |
| 10 | *Firmware FW-4 à FW-6* + HUD télémétrie et batterie | **J3** | ⏳ À faire |
| 11 | Premiers vols, puis réglages expo/trims/PID (*FW-8*) | **J4 et J5** | ⏳ À faire |
| 12 | Manette Bluetooth, haptique | **J6** | ⏳ À faire |

---

## 9. Risques

| Risque | Impact | Parade |
|---|---|---|
| UDP routé vers la 4G | Aucune commande reçue | `WifiNetworkSpecifier` + `bindSocket` (§6.3) |
| Comportement du Wi-Fi différent selon le constructeur du téléphone | Connexion auto instable | Tester dès l'étape 4 ; bouton de connexion manuelle en repli |
| Firmware actuel peu sûr (failsafe 10 s, armement gaz hauts) | Départ moteur, drone incontrôlé | Legacy CSV limité aux tests **sans hélices** ; corrections FW-1 à FW-3 avant le vol |
| Latence ou pics Wi-Fi | Pilotage saccadé | 50 Hz fixe, séquence, failsafe 300 ms, mesure de la perte |
| Chute de tension LiPo 1S (brown-out) | Reboot en vol | Mesure batterie (SPEC_FIRMWARE §7), alertes, armement refusé si critique |
| App et firmware désynchronisés sur le protocole | Commandes erronées | HELLO avec version, vecteurs de test partagés |
