"""Protocole AppControlleur côté Python (SPEC_FIRMWARE §4).

Implémentation de référence utilisée par le simulateur et par le générateur de
vecteurs partagés. Doit rester alignée sur les codecs Kotlin et le firmware.
"""

import math
import struct

MAGIC = 0xD7
VERSION = 1
TYPE_COMMAND = 0x1
TYPE_TELEMETRY = 0x2
TYPE_HELLO = 0x3

FLAG_ARM = 1 << 0
FLAG_ESTOP = 1 << 1

COMMAND_FMT = "<BBHhhhHBB"      # + crc
TELEMETRY_FMT = "<BBHHHhhhBBbB"  # + crc
HELLO_FMT = "<BBH"               # + crc
HELLO_ACK_FMT = "<BBBBBBBB"      # + crc

STATE_DISARMED, STATE_ARMED, STATE_FAILSAFE, STATE_ESTOP_LATCH = range(4)


def crc16_ccitt_false(data: bytes) -> int:
    crc = 0xFFFF
    for b in data:
        crc ^= b << 8
        for _ in range(8):
            crc = ((crc << 1) ^ 0x1021) if crc & 0x8000 else (crc << 1)
            crc &= 0xFFFF
    return crc


def round_half_up(x: float) -> int:
    return math.floor(x + 0.5)


def _with_crc(body: bytes) -> bytes:
    return body + struct.pack("<H", crc16_ccitt_false(body))


def _ver_type(t: int) -> int:
    return (VERSION << 4) | t


def _check(data: bytes, size: int, t: int) -> bool:
    return (
        len(data) == size
        and data[0] == MAGIC
        and data[1] == _ver_type(t)
        and struct.unpack_from("<H", data, size - 2)[0] == crc16_ccitt_false(data[:-2])
    )


# --- app -> drone ---------------------------------------------------------

def encode_command(seq, roll, pitch, yaw, throttle, arm, estop) -> bytes:
    """roll/pitch/yaw dans [-1, 1], throttle dans [0, 1]."""
    def s(v, lo, hi):
        return round_half_up(min(max(v, lo), hi) * 1000)
    flags = (FLAG_ARM if arm else 0) | (FLAG_ESTOP if estop else 0)
    body = struct.pack(COMMAND_FMT, MAGIC, _ver_type(TYPE_COMMAND), seq & 0xFFFF,
                       s(roll, -1, 1), s(pitch, -1, 1), s(yaw, -1, 1), s(throttle, 0, 1), flags, 0)
    return _with_crc(body)


def encode_hello(seq) -> bytes:
    return _with_crc(struct.pack(HELLO_FMT, MAGIC, _ver_type(TYPE_HELLO), seq & 0xFFFF))


def encode_legacy_csv(roll, pitch, yaw, throttle, arm, estop) -> str:
    def c(v):
        # Inverse du décodage firmware (raw - 511.5) / 511.5
        return round_half_up(511.5 + min(max(v, -1), 1) * 511.5)
    ry = round_half_up(min(max(throttle, 0), 1) * 1023)
    return f"{c(roll)},{c(pitch)},{c(yaw)},{ry},{int(arm)},{int(estop)}"


def decode_command(data: bytes):
    if not _check(data, 16, TYPE_COMMAND):
        return None
    _, _, seq, roll, pitch, yaw, thr, flags, _ = struct.unpack_from(COMMAND_FMT, data)
    return {"seq": seq, "roll": roll, "pitch": pitch, "yaw": yaw, "throttle": thr,
            "arm": bool(flags & FLAG_ARM), "estop": bool(flags & FLAG_ESTOP)}


def decode_hello(data: bytes):
    if not _check(data, 6, TYPE_HELLO):
        return None
    return {"seq": struct.unpack_from(HELLO_FMT, data)[2]}


def decode_legacy_csv(text: str):
    parts = text.strip().split(",")
    if len(parts) != 6:
        return None
    try:
        lx, ly, rx, ry, arm, emg = (int(p) for p in parts)
    except ValueError:
        return None
    return {"lx": lx, "ly": ly, "rx": rx, "ry": ry, "arm": arm == 1, "estop": emg == 1}


# --- drone -> app ---------------------------------------------------------

def encode_telemetry(seq, ack_seq, vbat_mv, roll_cdeg, pitch_cdeg, yaw_rate_ddps,
                     state, status_flags, rssi, loop_hz_half) -> bytes:
    body = struct.pack(TELEMETRY_FMT, MAGIC, _ver_type(TYPE_TELEMETRY), seq & 0xFFFF, ack_seq & 0xFFFF,
                       vbat_mv, roll_cdeg, pitch_cdeg, yaw_rate_ddps, state, status_flags, rssi, loop_hz_half)
    return _with_crc(body)


def encode_hello_ack(proto_version, fw_major, fw_minor, fw_patch, board_id) -> bytes:
    body = struct.pack(HELLO_ACK_FMT, MAGIC, _ver_type(TYPE_HELLO), proto_version,
                       fw_major, fw_minor, fw_patch, board_id, 0)
    return _with_crc(body)
