"""Simulateur de drone sur PC (CAHIER_DES_CHARGES §7.3).

Écoute en UDP comme le firmware et accepte les deux protocoles :
- Legacy CSV `lx,ly,rx,ry,arm,emg` (affiche les valeurs, pas de réponse) ;
- Binaire v1 (HELLO/HELLO_ACK, machine à états §5, télémétrie 10 Hz simulée).

Usage : python tools/drone_sim.py [--port 4210] [--vbat 3900]
Dans l'app, régler l'IP du drone sur l'adresse du PC.
"""

import argparse
import math
import socket
import time

import drone_protocol as p

FAILSAFE_S = 0.300
TELEMETRY_PERIOD_S = 0.100
LOG_PERIOD_S = 1.0
ARM_THROTTLE_MAX = 50
FW_VERSION = (0, 1, 0)
BOARD_ID = 1  # XIAO

STATE_NAMES = ["DISARMED", "ARMED", "FAILSAFE", "ESTOP_LATCH"]


class SimDrone:
    def __init__(self, vbat_mv: int):
        self.vbat_mv = vbat_mv
        self.state = p.STATE_DISARMED
        self.pilot = None            # (ip, port) verrouillé par HELLO
        self.last_seq = None
        self.last_valid = 0.0
        self.prev_arm = None         # None : aucun paquet encore reçu
        self.arm_refused = False
        self.cmd = {"roll": 0, "pitch": 0, "yaw": 0, "throttle": 0}
        self.roll = self.pitch = 0.0
        self.tel_seq = 0
        self.errors = 0
        self.received = 0

    # --- réception --------------------------------------------------------

    def on_datagram(self, data: bytes, addr, sock):
        if data and data[0] == p.MAGIC:
            self._on_binary(data, addr, sock)
        else:
            self._on_csv(data)

    def _on_csv(self, data: bytes):
        msg = p.decode_legacy_csv(data.decode("ascii", errors="replace"))
        if msg is None:
            self.errors += 1
            return
        self.received += 1
        self._csv_last = msg

    def _on_binary(self, data: bytes, addr, sock):
        hello = p.decode_hello(data)
        if hello is not None:
            self.pilot = addr
            self.last_seq = None
            sock.sendto(p.encode_hello_ack(p.VERSION, *FW_VERSION, BOARD_ID), addr)
            print(f"[HELLO] pilote verrouillé : {addr[0]}:{addr[1]}")
            return

        cmd = p.decode_command(data)
        if cmd is None:
            self.errors += 1
            return
        if addr != self.pilot:
            return  # FW-WIFI-6 : seul le pilote verrouillé commande
        if self.last_seq is not None and not (1 <= ((cmd["seq"] - self.last_seq) & 0xFFFF) <= 32767):
            return  # séquence ancienne ou dupliquée
        self.last_seq = cmd["seq"]
        self.last_valid = time.monotonic()
        self.received += 1
        self.cmd = cmd
        self._update_state(cmd)

    # --- machine à états (SPEC_FIRMWARE §5) -------------------------------

    def _update_state(self, cmd):
        arm, estop = cmd["arm"], cmd["estop"]
        rising = arm and self.prev_arm is False
        self.prev_arm = arm

        if estop:
            self.state = p.STATE_ESTOP_LATCH
        elif self.state == p.STATE_ESTOP_LATCH:
            if not arm:
                self.state = p.STATE_DISARMED
        elif self.state == p.STATE_FAILSAFE:
            if not arm:
                self.state = p.STATE_DISARMED
        elif self.state == p.STATE_ARMED:
            if not arm:
                self.state = p.STATE_DISARMED
        elif self.state == p.STATE_DISARMED and rising:
            battery_critical = 0 < self.vbat_mv < 3300
            if cmd["throttle"] < ARM_THROTTLE_MAX and not battery_critical:
                self.state = p.STATE_ARMED
                self.arm_refused = False
            else:
                self.arm_refused = True
        if not arm:
            self.arm_refused = False

    def tick(self, now: float):
        if self.state == p.STATE_ARMED and now - self.last_valid > FAILSAFE_S:
            self.state = p.STATE_FAILSAFE
            self.last_seq = None
            print("[FAILSAFE] plus de paquet valide depuis 300 ms : moteurs coupés")
        # Attitude simulée : suit la consigne (angle max 30°) avec un premier ordre
        k = 0.15
        self.roll += (self.cmd["roll"] / 1000 * 30 - self.roll) * k
        self.pitch += (self.cmd["pitch"] / 1000 * 30 - self.pitch) * k

    def telemetry(self) -> bytes:
        flags = 1  # IMU OK
        if 0 < self.vbat_mv < 3500:
            flags |= 1 << 1
        if 0 < self.vbat_mv < 3300:
            flags |= 1 << 2
        if self.arm_refused:
            flags |= 1 << 3
        self.tel_seq = (self.tel_seq + 1) & 0xFFFF
        wobble = math.sin(time.monotonic() * 3) * 0.5
        return p.encode_telemetry(
            self.tel_seq, self.last_seq or 0, self.vbat_mv,
            int((self.roll + wobble) * 100), int(self.pitch * 100), int(self.cmd["yaw"] / 1000 * 1800),
            self.state, flags, -50, 125,
        )


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=4210)
    ap.add_argument("--vbat", type=int, default=3900, help="tension simulée en mV (0 = non mesurée)")
    args = ap.parse_args()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(("0.0.0.0", args.port))
    sock.settimeout(0.01)
    print(f"Simulateur en écoute sur UDP :{args.port} (Ctrl+C pour quitter)")

    drone = SimDrone(args.vbat)
    last_tel = last_log = time.monotonic()
    while True:
        try:
            data, addr = sock.recvfrom(256)
            drone.on_datagram(data, addr, sock)
        except socket.timeout:
            pass
        except ConnectionResetError:
            pass  # Windows : ICMP port unreachable après un envoi

        now = time.monotonic()
        drone.tick(now)
        if drone.pilot and now - last_tel >= TELEMETRY_PERIOD_S:
            sock.sendto(drone.telemetry(), drone.pilot)
            last_tel = now
        if now - last_log >= LOG_PERIOD_S:
            csv = getattr(drone, "_csv_last", None)
            if csv:
                print(f"[CSV] {csv}  paquets/s={drone.received} erreurs={drone.errors}")
            else:
                c = drone.cmd
                print(f"[BIN] {STATE_NAMES[drone.state]} r={c['roll']} p={c['pitch']} y={c['yaw']} "
                      f"t={c['throttle']} paquets/s={drone.received} erreurs={drone.errors}")
            drone.received = 0
            last_log = now


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
