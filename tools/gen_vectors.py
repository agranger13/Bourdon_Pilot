"""Génère shared/protocol_vectors.json à partir de l'implémentation Python de référence.

Usage : python tools/gen_vectors.py
Les tests JUnit de l'app (et plus tard les tests Unity du firmware) lisent ce fichier.
"""

import json
from pathlib import Path

import drone_protocol as p

OUT = Path(__file__).resolve().parent.parent / "shared" / "protocol_vectors.json"

COMMANDS = [
    # seq, roll, pitch, yaw, throttle, arm, estop
    (0, 0.0, 0.0, 0.0, 0.0, False, False),
    (1, 1.0, -1.0, 0.5, 1.0, True, False),
    (65535, -0.25, 0.75, -1.0, 0.049, False, True),
    (1234, 2.0, -3.0, 0.0005, -0.5, True, True),  # hors plage : contraint
]

TELEMETRY = [
    # seq, ackSeq, vbatMv, roll_cdeg, pitch_cdeg, yawRate_ddps, state, flags, rssi, loopHz/2
    (10, 9, 3950, 1234, -567, -150, p.STATE_ARMED, 0b00001, -55, 125),
    (65535, 0, 0, -18000, 9000, 0, p.STATE_FAILSAFE, 0b11110, 0, 0),
]

HELLO_ACKS = [(1, 0, 3, 1, 1)]


def hexs(b: bytes) -> str:
    return b.hex()


def main():
    vectors = {
        "crc16_ccitt_false": [
            {"ascii": "123456789", "crc": 0x29B1},
            {"ascii": "", "crc": 0xFFFF},
        ],
        "command": [
            {"seq": s, "roll": r, "pitch": pi, "yaw": y, "throttle": t, "arm": a, "estop": e,
             "hex": hexs(p.encode_command(s, r, pi, y, t, a, e))}
            for s, r, pi, y, t, a, e in COMMANDS
        ],
        "legacy_csv": [
            {"roll": r, "pitch": pi, "yaw": y, "throttle": t, "arm": a, "estop": e,
             "text": p.encode_legacy_csv(r, pi, y, t, a, e)}
            for _, r, pi, y, t, a, e in COMMANDS
        ],
        "hello": [{"seq": s, "hex": hexs(p.encode_hello(s))} for s in (0, 42)],
        "telemetry": [
            {"seq": s, "ackSeq": ack, "vbatMv": v, "rollCdeg": r, "pitchCdeg": pi, "yawRateDdps": y,
             "state": st, "statusFlags": f, "rssi": rs, "loopHzHalf": lh,
             "hex": hexs(p.encode_telemetry(s, ack, v, r, pi, y, st, f, rs, lh))}
            for s, ack, v, r, pi, y, st, f, rs, lh in TELEMETRY
        ],
        "hello_ack": [
            {"protoVersion": pv, "fwMajor": a, "fwMinor": b, "fwPatch": c, "boardId": bid,
             "hex": hexs(p.encode_hello_ack(pv, a, b, c, bid))}
            for pv, a, b, c, bid in HELLO_ACKS
        ],
    }
    assert p.crc16_ccitt_false(b"123456789") == 0x29B1
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(vectors, indent=2) + "\n", encoding="utf-8")
    print(f"Écrit : {OUT}")


if __name__ == "__main__":
    main()
