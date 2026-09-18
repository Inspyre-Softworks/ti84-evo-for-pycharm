#!/usr/bin/env python3
"""TI-84 Evo OS 7.1 SmartPad USB diagnostic utility.

This script is intentionally separate from the production plugin. It captures
descriptors, compares enumeration states, and decodes raw HID reports from
hidapi or a USBPcap/Wireshark capture. It never sends an output report unless
the explicit ``led-output --confirm`` command is used.
"""

from __future__ import annotations

import argparse
import copy
import datetime as dt
import json
import os
import platform
import shutil
import struct
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Any, Iterable


TI_VENDOR_ID = 0x0451
EVO_PRODUCT_ID = 0xE018
HID_INTERFACE = 3
HID_ENDPOINT = 0x84
BOOT_REPORT_BYTES = 8

ITEM_TYPE_NAMES = {0: 'main', 1: 'global', 2: 'local', 3: 'reserved'}
MAIN_TAGS = {8: 'Input', 9: 'Output', 10: 'Collection', 11: 'Feature', 12: 'End Collection'}
GLOBAL_TAGS = {
    0: 'Usage Page', 1: 'Logical Minimum', 2: 'Logical Maximum',
    3: 'Physical Minimum', 4: 'Physical Maximum', 5: 'Unit Exponent',
    6: 'Unit', 7: 'Report Size', 8: 'Report ID', 9: 'Report Count',
    10: 'Push', 11: 'Pop',
}
LOCAL_TAGS = {
    0: 'Usage', 1: 'Usage Minimum', 2: 'Usage Maximum',
    3: 'Designator Index', 4: 'Designator Minimum', 5: 'Designator Maximum',
    7: 'String Index', 8: 'String Minimum', 9: 'String Maximum', 10: 'Delimiter',
}
REPORT_TYPES = {8: 'input', 9: 'output', 11: 'feature'}


def hex_bytes(data: bytes) -> str:
    return ' '.join(f'{byte:02X}' for byte in data)


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def signed_little(data: bytes) -> int:
    if not data:
        return 0
    return int.from_bytes(data, 'little', signed=True)


def parse_hid_report_descriptor(raw: bytes) -> dict[str, Any]:
    """Parse all HID items and derive report fields without dropping unknowns."""
    items: list[dict[str, Any]] = []
    index = 0
    while index < len(raw):
        offset = index
        prefix = raw[index]
        index += 1
        if prefix == 0xFE:
            if index + 2 > len(raw):
                raise ValueError(f'truncated HID long item header at offset {offset}')
            size, tag = raw[index:index + 2]
            index += 2
            if index + size > len(raw):
                raise ValueError(f'truncated HID long item payload at offset {offset}')
            data = raw[index:index + size]
            index += size
            items.append({
                'offset': offset, 'encoded_hex': hex_bytes(raw[offset:index]),
                'type': 'long', 'tag': tag, 'name': f'Long item 0x{tag:02X}',
                'data_hex': hex_bytes(data), 'unsigned_value': int.from_bytes(data, 'little'),
                'signed_value': signed_little(data),
            })
            continue
        size = (0, 1, 2, 4)[prefix & 0x03]
        if index + size > len(raw):
            raise ValueError(f'truncated HID short item at offset {offset}')
        data = raw[index:index + size]
        index += size
        item_type = (prefix >> 2) & 0x03
        tag = (prefix >> 4) & 0x0F
        type_name = ITEM_TYPE_NAMES[item_type]
        names = MAIN_TAGS if item_type == 0 else GLOBAL_TAGS if item_type == 1 else LOCAL_TAGS
        items.append({
            'offset': offset, 'encoded_hex': hex_bytes(raw[offset:index]),
            'type': type_name, 'tag': tag,
            'name': names.get(tag, f'Unknown {type_name} tag 0x{tag:X}'),
            'data_hex': hex_bytes(data), 'unsigned_value': int.from_bytes(data, 'little'),
            'signed_value': signed_little(data),
        })

    global_state: dict[str, Any] = {
        'usage_page': None, 'logical_minimum': 0, 'logical_maximum': 0,
        'report_size': None, 'report_count': None, 'report_id': 0,
    }
    global_stack: list[dict[str, Any]] = []
    local_state: dict[str, Any] = {'usages': [], 'usage_minimum': None, 'usage_maximum': None}
    offsets: dict[tuple[str, int], int] = {}
    fields: list[dict[str, Any]] = []
    collections: list[dict[str, Any]] = []

    def expanded_usage(value: int, data_size: int) -> dict[str, int | None]:
        if data_size > 2:
            return {'page': (value >> 16) & 0xFFFF, 'usage': value & 0xFFFF}
        return {'page': global_state['usage_page'], 'usage': value}

    for item in items:
        kind, tag, value = item['type'], item['tag'], item['unsigned_value']
        if kind == 'global':
            if tag == 0:
                global_state['usage_page'] = value
            elif tag == 1:
                global_state['logical_minimum'] = item['signed_value']
            elif tag == 2:
                global_state['logical_maximum'] = (
                    item['signed_value'] if global_state['logical_minimum'] < 0 else value
                )
            elif tag == 7:
                global_state['report_size'] = value
            elif tag == 8:
                if not 1 <= value <= 255:
                    raise ValueError(f'invalid Report ID {value} at offset {item["offset"]}')
                global_state['report_id'] = value
            elif tag == 9:
                global_state['report_count'] = value
            elif tag == 10:
                global_stack.append(copy.deepcopy(global_state))
            elif tag == 11:
                if not global_stack:
                    raise ValueError(f'HID Pop without Push at offset {item["offset"]}')
                global_state = global_stack.pop()
        elif kind == 'local':
            usage = expanded_usage(value, len(bytes.fromhex(item['data_hex'])))
            if tag == 0:
                local_state['usages'].append(usage)
            elif tag == 1:
                local_state['usage_minimum'] = usage
            elif tag == 2:
                local_state['usage_maximum'] = usage
        elif kind == 'main':
            if tag in REPORT_TYPES:
                if global_state['report_size'] is None or global_state['report_count'] is None:
                    raise ValueError(f'missing Report Size/Count at offset {item["offset"]}')
                report_type = REPORT_TYPES[tag]
                report_id = global_state['report_id']
                key = (report_type, report_id)
                bit_offset = offsets.get(key, 0)
                field = {
                    'type': report_type,
                    'report_id': report_id,
                    'bit_offset': bit_offset,
                    'report_size': global_state['report_size'],
                    'report_count': global_state['report_count'],
                    'usage_page': global_state['usage_page'],
                    'usages': copy.deepcopy(local_state['usages']),
                    'usage_minimum': copy.deepcopy(local_state['usage_minimum']),
                    'usage_maximum': copy.deepcopy(local_state['usage_maximum']),
                    'logical_minimum': global_state['logical_minimum'],
                    'logical_maximum': global_state['logical_maximum'],
                    'flags': value,
                    'constant': bool(value & 0x01),
                    'variable': bool(value & 0x02),
                    'relative': bool(value & 0x04),
                    'source_offset': item['offset'],
                }
                fields.append(field)
                offsets[key] = bit_offset + field['report_size'] * field['report_count']
            elif tag == 10:
                usage = local_state['usages'][0] if local_state['usages'] else local_state['usage_minimum']
                collections.append({
                    'type': value,
                    'usage_page': usage['page'] if usage else global_state['usage_page'],
                    'usage': usage['usage'] if usage else None,
                    'source_offset': item['offset'],
                })
            local_state = {'usages': [], 'usage_minimum': None, 'usage_maximum': None}
    if global_stack:
        raise ValueError(f'descriptor ended with {len(global_stack)} unclosed Push item(s)')

    reports = []
    for report_type, report_id in sorted(offsets):
        bits = offsets[(report_type, report_id)]
        reports.append({
            'type': report_type,
            'report_id': report_id,
            'payload_bits': bits,
            'wire_bytes': (bits + 7) // 8 + (1 if report_id else 0),
        })
    pages = {
        field['usage_page'] for field in fields
        if field['usage_page'] is not None and 0xFF00 <= field['usage_page'] <= 0xFFFF
    }
    return {
        'raw_hex': hex_bytes(raw), 'length': len(raw), 'items': items,
        'collections': collections, 'fields': fields, 'reports': reports,
        'report_ids': sorted({field['report_id'] for field in fields}),
        'vendor_defined_usage_pages': sorted(pages),
    }


def hid_descriptor_text(decoded: dict[str, Any]) -> str:
    lines = [f'HID report descriptor ({decoded["length"]} bytes)', f'Raw: {decoded["raw_hex"]}']
    for item in decoded['items']:
        lines.append(
            f'{item["offset"]:04X}  {item["encoded_hex"]:<18} '
            f'{item["name"]} = 0x{item["unsigned_value"]:X}'
        )
    lines.append('Reports:')
    for report in decoded['reports']:
        lines.append(
            f'  {report["type"].upper()} id={report["report_id"]} '
            f'{report["payload_bits"]} bits ({report["wire_bytes"]} wire bytes)'
        )
        for field in decoded['fields']:
            if field['type'] != report['type'] or field['report_id'] != report['report_id']:
                continue
            end = field['bit_offset'] + field['report_size'] * field['report_count'] - 1
            lines.append(
                f'    bits {field["bit_offset"]}..{end}: '
                f'{field["report_count"]} x {field["report_size"]}, '
                f'page={format_optional_hex(field["usage_page"])}, '
                f'usages={format_usages(field)}, flags=0x{field["flags"]:X}'
            )
    pages = decoded['vendor_defined_usage_pages']
    lines.append('Vendor-defined usage pages: ' + (', '.join(f'0x{x:04X}' for x in pages) or 'none'))
    return '\n'.join(lines)


def format_optional_hex(value: int | None) -> str:
    return 'unset' if value is None else f'0x{value:X}'


def format_usages(field: dict[str, Any]) -> str:
    def one(value: dict[str, Any] | None) -> str:
        if value is None:
            return '?'
        return f'{format_optional_hex(value["page"])}:0x{value["usage"]:X}'

    if field['usages']:
        return ','.join(one(value) for value in field['usages'])
    if field['usage_minimum'] or field['usage_maximum']:
        return f'{one(field["usage_minimum"])}..{one(field["usage_maximum"])}'
    return 'unspecified'


def usb_modules() -> tuple[Any, Any, Any]:
    try:
        import usb.core
        import usb.util
        from libusb_package import get_libusb1_backend
    except ImportError as error:
        raise SystemExit(
            'USB enumeration requires pyusb and libusb-package. '
            'Install scripts/requirements-smartpad.txt.'
        ) from error
    return usb.core, usb.util, get_libusb1_backend


def hid_module() -> Any:
    try:
        import hid
    except ImportError as error:
        raise SystemExit(
            'HID access requires hidapi. Install scripts/requirements-smartpad.txt.'
        ) from error
    return hid


def find_evo() -> tuple[Any, Any]:
    usb_core, _, get_backend = usb_modules()
    backend = get_backend()
    device = usb_core.find(idVendor=TI_VENDOR_ID, idProduct=EVO_PRODUCT_ID, backend=backend)
    return device, backend


def control_descriptor(device: Any, descriptor_type: int, length: int, index: int = 0) -> bytes:
    return bytes(device.ctrl_transfer(0x80, 6, descriptor_type << 8 | index, 0, length, timeout=2000))


def parse_device_descriptor(raw: bytes) -> dict[str, Any]:
    if len(raw) != 18:
        return {'raw_hex': hex_bytes(raw), 'error': f'expected 18 bytes, received {len(raw)}'}
    values = struct.unpack('<BBHBBBBHHHBBBB', raw)
    names = (
        'bLength', 'bDescriptorType', 'bcdUSB', 'bDeviceClass', 'bDeviceSubClass',
        'bDeviceProtocol', 'bMaxPacketSize0', 'idVendor', 'idProduct', 'bcdDevice',
        'iManufacturer', 'iProduct', 'iSerialNumber', 'bNumConfigurations',
    )
    result = dict(zip(names, values))
    result['raw_hex'] = hex_bytes(raw)
    return result


def parse_configuration_descriptor(raw: bytes) -> dict[str, Any]:
    descriptors = []
    index = 0
    while index < len(raw):
        if index + 2 > len(raw):
            descriptors.append({'offset': index, 'raw_hex': hex_bytes(raw[index:]), 'error': 'truncated header'})
            break
        length, descriptor_type = raw[index:index + 2]
        if length < 2 or index + length > len(raw):
            descriptors.append({'offset': index, 'raw_hex': hex_bytes(raw[index:]), 'error': 'invalid length'})
            break
        data = raw[index:index + length]
        entry: dict[str, Any] = {
            'offset': index, 'length': length, 'descriptor_type': descriptor_type,
            'raw_hex': hex_bytes(data),
        }
        if descriptor_type == 2 and length >= 9:
            total, interfaces, value, string_index, attributes, max_power = struct.unpack_from('<HBBBBB', data, 2)
            entry.update({
                'name': 'configuration', 'wTotalLength': total, 'bNumInterfaces': interfaces,
                'bConfigurationValue': value, 'iConfiguration': string_index,
                'bmAttributes': attributes, 'bMaxPower': max_power,
            })
        elif descriptor_type == 4 and length >= 9:
            number, alternate, endpoint_count, class_code, subclass, protocol, string_index = data[2:9]
            entry.update({
                'name': 'interface', 'bInterfaceNumber': number, 'bAlternateSetting': alternate,
                'bNumEndpoints': endpoint_count, 'bInterfaceClass': class_code,
                'bInterfaceSubClass': subclass, 'bInterfaceProtocol': protocol,
                'iInterface': string_index,
            })
        elif descriptor_type == 5 and length >= 7:
            address, attributes, packet_size, interval = struct.unpack_from('<BBHB', data, 2)
            entry.update({
                'name': 'endpoint', 'bEndpointAddress': address, 'direction': 'IN' if address & 0x80 else 'OUT',
                'bmAttributes': attributes, 'transfer_type': ('control', 'isochronous', 'bulk', 'interrupt')[attributes & 3],
                'wMaxPacketSize': packet_size, 'bInterval': interval,
            })
        elif descriptor_type == 0x0B and length >= 8:
            first, count, class_code, subclass, protocol, string_index = data[2:8]
            entry.update({
                'name': 'interface_association', 'bFirstInterface': first, 'bInterfaceCount': count,
                'bFunctionClass': class_code, 'bFunctionSubClass': subclass,
                'bFunctionProtocol': protocol, 'iFunction': string_index,
            })
        elif descriptor_type == 0x21 and length >= 9:
            hid_version, country, count, child_type, child_length = struct.unpack_from('<HBBB H', data, 2)
            entry.update({
                'name': 'hid', 'bcdHID': hid_version, 'bCountryCode': country,
                'bNumDescriptors': count, 'bDescriptorType2': child_type,
                'wDescriptorLength': child_length,
            })
        else:
            entry['name'] = f'unknown_0x{descriptor_type:02X}'
        descriptors.append(entry)
        index += length
    return {'raw_hex': hex_bytes(raw), 'length': len(raw), 'descriptors': descriptors}


def capture_hid_identity() -> list[dict[str, Any]]:
    hid = hid_module()
    identities = []
    for identity in hid.enumerate(TI_VENDOR_ID, EVO_PRODUCT_ID):
        normalized = {
            key: (value.hex() if isinstance(value, bytes) else value)
            for key, value in identity.items()
        }
        if identity.get('interface_number') != HID_INTERFACE:
            identities.append(normalized)
            continue
        handle = hid.device()
        try:
            handle.open_path(identity['path'])
            report_descriptor = bytes(handle.get_report_descriptor())
            normalized['report_descriptor_source'] = (
                'Windows HID preparsed-data reconstruction (best effort; not a raw control-transfer capture)'
                if platform.system() == 'Windows'
                else 'hidapi device report descriptor'
            )
            normalized['report_descriptor'] = parse_hid_report_descriptor(report_descriptor)
            normalized['report_descriptor_text'] = hid_descriptor_text(normalized['report_descriptor'])
        except OSError as error:
            normalized['open_error'] = str(error)
        finally:
            try:
                handle.close()
            except OSError:
                pass
        identities.append(normalized)
    return identities


def take_snapshot(label: str) -> dict[str, Any]:
    device, _ = find_evo()
    snapshot: dict[str, Any] = {
        'schema': 1,
        'captured_utc': utc_now(),
        'label': label,
        'host': {'platform': platform.platform(), 'python': sys.version},
        'target': {'vid': TI_VENDOR_ID, 'pid': EVO_PRODUCT_ID},
        'connected': device is not None,
    }
    if device is None:
        return snapshot

    _, usb_util, _ = usb_modules()
    raw_device = control_descriptor(device, 1, 18)
    parsed_device = parse_device_descriptor(raw_device)
    total_length = 9
    config_header = control_descriptor(device, 2, total_length)
    if len(config_header) >= 4:
        total_length = int.from_bytes(config_header[2:4], 'little')
    raw_config = control_descriptor(device, 2, total_length)
    strings = {}
    for name, index_name in (
        ('manufacturer', 'iManufacturer'), ('product', 'iProduct'), ('serial', 'iSerialNumber')
    ):
        index = parsed_device.get(index_name, 0)
        try:
            strings[name] = usb_util.get_string(device, index) if index else None
        except Exception as error:  # Access errors are evidence and belong in the snapshot.
            strings[name] = {'error': repr(error)}
    try:
        active_configuration = device.get_active_configuration().bConfigurationValue
    except Exception as error:
        active_configuration = {'error': repr(error)}
    snapshot.update({
        'device_descriptor': parsed_device,
        'configuration_descriptor': parse_configuration_descriptor(raw_config),
        'active_configuration': active_configuration,
        'strings': strings,
        'hid_devices': capture_hid_identity(),
    })
    return snapshot


def snapshot_text(snapshot: dict[str, Any]) -> str:
    lines = [
        f'Label: {snapshot["label"]}',
        f'Captured UTC: {snapshot["captured_utc"]}',
        f'VID:PID: {snapshot["target"]["vid"]:04X}:{snapshot["target"]["pid"]:04X}',
        f'Connected: {snapshot["connected"]}',
    ]
    if not snapshot['connected']:
        return '\n'.join(lines)
    device = snapshot['device_descriptor']
    lines += [
        f'Device descriptor: {device["raw_hex"]}',
        f'bcdUSB=0x{device["bcdUSB"]:04X} class/subclass/protocol='
        f'0x{device["bDeviceClass"]:02X}/0x{device["bDeviceSubClass"]:02X}/0x{device["bDeviceProtocol"]:02X}',
        f'Strings: {snapshot["strings"]}',
        f'Active configuration: {snapshot["active_configuration"]}',
        f'Configuration descriptor: {snapshot["configuration_descriptor"]["raw_hex"]}',
        'Descriptor tree:',
    ]
    for entry in snapshot['configuration_descriptor']['descriptors']:
        lines.append(f'  @{entry["offset"]:03d} {entry["name"]}: {entry["raw_hex"]}')
        if entry['name'] == 'interface':
            lines.append(
                f'      IF{entry["bInterfaceNumber"]} alt={entry["bAlternateSetting"]} '
                f'class/subclass/protocol=0x{entry["bInterfaceClass"]:02X}/'
                f'0x{entry["bInterfaceSubClass"]:02X}/0x{entry["bInterfaceProtocol"]:02X}'
            )
        elif entry['name'] == 'endpoint':
            lines.append(
                f'      EP{entry["bEndpointAddress"]:02X} {entry["direction"]} '
                f'{entry["transfer_type"]} max={entry["wMaxPacketSize"]} interval={entry["bInterval"]}'
            )
    for hid_identity in snapshot['hid_devices']:
        lines.append(
            f'HID interface={hid_identity.get("interface_number")} '
            f'usage=0x{hid_identity.get("usage_page", 0):X}:0x{hid_identity.get("usage", 0):X}'
        )
        if 'report_descriptor_text' in hid_identity:
            lines.append(f'Report descriptor source: {hid_identity["report_descriptor_source"]}')
            lines.append(hid_identity['report_descriptor_text'])
            advertised = next(
                (
                    entry.get('wDescriptorLength')
                    for entry in snapshot['configuration_descriptor']['descriptors']
                    if entry.get('name') == 'hid'
                ),
                None,
            )
            observed = hid_identity['report_descriptor']['length']
            if advertised is not None and advertised != observed:
                lines.append(
                    f'WARNING: HID descriptor advertises {advertised} report-descriptor bytes, '
                    f'but this {hid_identity["report_descriptor_source"]} produced {observed}. '
                    'Capture physical re-enumeration with USBPcap before calling either length exact.'
                )
        if 'open_error' in hid_identity:
            lines.append(f'HID open error: {hid_identity["open_error"]}')
    return '\n'.join(lines)


def write_snapshot(snapshot: dict[str, Any], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(snapshot, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    output.with_suffix('.txt').write_text(snapshot_text(snapshot) + '\n', encoding='utf-8')
    for identity in snapshot.get('hid_devices', []):
        descriptor = identity.get('report_descriptor')
        if descriptor:
            output.with_name(output.stem + '-report-descriptor.bin').write_bytes(
                bytes.fromhex(descriptor['raw_hex'])
            )
            output.with_name(output.stem + '-report-descriptor.txt').write_text(
                identity['report_descriptor_text'] + '\n', encoding='utf-8'
            )


def flatten(value: Any, prefix: str = '') -> dict[str, Any]:
    result: dict[str, Any] = {}
    if isinstance(value, dict):
        for key in sorted(value):
            child = f'{prefix}.{key}' if prefix else key
            result.update(flatten(value[key], child))
    elif isinstance(value, list):
        for index, item in enumerate(value):
            result.update(flatten(item, f'{prefix}[{index}]'))
        if not value:
            result[prefix] = []
    else:
        result[prefix] = value
    return result


def compare_snapshots(before: dict[str, Any], after: dict[str, Any]) -> list[str]:
    ignored = {'captured_utc', 'label', 'host.python'}
    left, right = flatten(before), flatten(after)
    differences = []
    for key in sorted(set(left) | set(right)):
        if key in ignored:
            continue
        if left.get(key) != right.get(key):
            differences.append(f'{key}: {left.get(key)!r} -> {right.get(key)!r}')
    return differences


def wait_for_evo(connected: bool, timeout: float = 60.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        device, _ = find_evo()
        if (device is not None) == connected:
            return
        time.sleep(0.25)
    state = 'connect' if connected else 'disconnect'
    raise TimeoutError(f'timed out waiting for calculator to {state}')


MATRIX_STATES = (
    ('home-connected', 'Connect the calculator and leave it on the normal home screen.'),
    ('smartpad-launched-connected', 'With USB still connected, launch SmartPad.'),
    ('smartpad-before-connect', 'Disconnect USB, leave SmartPad running, then reconnect USB.'),
    ('connected-then-smartpad', 'Exit/disconnect as needed, connect first, then launch SmartPad.'),
    ('smartpad-exited', 'Exit SmartPad if possible while keeping USB connected; otherwise disconnect/reconnect to home.'),
)


def run_matrix(output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    snapshots = []
    print('Interactive SmartPad enumeration matrix. No output reports will be sent.')
    for label, instruction in MATRIX_STATES:
        print(f'\n[{label}] {instruction}')
        input('Press Enter only after the requested state is ready... ')
        wait_for_evo(True)
        snapshot = take_snapshot(label)
        write_snapshot(snapshot, output / f'{label}.json')
        snapshots.append(snapshot)
        print(f'Captured {label}.')
    report = []
    baseline = snapshots[0]
    for snapshot in snapshots[1:]:
        report.append(f'## {baseline["label"]} -> {snapshot["label"]}')
        differences = compare_snapshots(baseline, snapshot)
        report.extend(f'- {line}' for line in differences or ['No USB identity differences.'])
        report.append('')
    (output / 'comparison.md').write_text('\n'.join(report), encoding='utf-8')
    print(f'Wrote snapshots and comparison to {output}')


SMARTPAD_CALCULATOR_KEYS = {
    (0x00, 0x6F): 'Y=', (0x05, 0x6C): 'WINDOW', (0x05, 0x6B): 'ZOOM',
    (0x03, 0x6F): 'TRACE', (0x06, 0x6C): 'GRAPH', (0x05, 0x6E): '2nd',
    (0x05, 0x6F): 'MODE', (0x00, 0x4C): 'DEL', (0x00, 0x52): 'UP',
    (0x03, 0x6D): 'ALPHA', (0x04, 0x6B): 'X,T,theta,n', (0x01, 0x6C): 'STAT',
    (0x00, 0x50): 'LEFT', (0x00, 0x4F): 'RIGHT', (0x00, 0x51): 'DOWN',
    (0x03, 0x6C): 'MATH', (0x01, 0x6F): 'FRAC', (0x01, 0x6B): 'PRGM',
    (0x02, 0x6E): 'VARS', (0x02, 0x6C): 'CLEAR', (0x02, 0x6B): 'x^-1',
    (0x01, 0x6E): 'SIN', (0x02, 0x6F): 'COS', (0x02, 0x6D): 'TAN',
    (0x00, 0x54): 'DIVIDE', (0x04, 0x6F): 'x^2', (0x01, 0x6D): ',',
    (0x06, 0x6E): '(', (0x06, 0x6D): ')', (0x00, 0x55): 'MULTIPLY',
    (0x02, 0x69): 'LOG', (0x04, 0x6E): '7', (0x05, 0x6A): '8',
    (0x03, 0x69): '9', (0x00, 0x56): 'MINUS', (0x04, 0x6D): 'LN',
    (0x05, 0x69): '4', (0x06, 0x6B): '5', (0x00, 0x6D): '6',
    (0x00, 0x57): 'PLUS', (0x04, 0x6C): 'STO->', (0x02, 0x6A): '1',
    (0x00, 0x6B): '2', (0x00, 0x6C): '3', (0x03, 0x6B): 'TOGGLE',
    (0x00, 0x29): 'ON', (0x03, 0x6A): '0', (0x00, 0x6E): '.',
    (0x06, 0x6F): '(-)', (0x00, 0x28): 'ENTER',
}


def calculator_key_name(modifier_byte: int, usage: int) -> str | None:
    return SMARTPAD_CALCULATOR_KEYS.get((modifier_byte, usage))


def host_key_name(usage: int) -> str:
    if 0x04 <= usage <= 0x1D:
        return chr(ord('A') + usage - 0x04)
    if 0x1E <= usage <= 0x26:
        return str(usage - 0x1D)
    names = {
        0x00: 'None', 0x01: 'ErrorRollOver', 0x02: 'POSTFail', 0x03: 'ErrorUndefined',
        0x27: '0', 0x28: 'Enter', 0x29: 'Escape', 0x2A: 'Backspace', 0x2B: 'Tab',
        0x2C: 'Space', 0x2D: 'Minus', 0x2E: 'Equal', 0x2F: 'LeftBracket',
        0x30: 'RightBracket', 0x31: 'Backslash', 0x33: 'Semicolon', 0x34: 'Quote',
        0x35: 'Grave', 0x36: 'Comma', 0x37: 'Period', 0x38: 'Slash', 0x39: 'CapsLock',
        0x46: 'PrintScreen', 0x47: 'ScrollLock', 0x48: 'Pause', 0x49: 'Insert',
        0x4A: 'Home', 0x4B: 'PageUp', 0x4C: 'Delete', 0x4D: 'End', 0x4E: 'PageDown',
        0x4F: 'RightArrow', 0x50: 'LeftArrow', 0x51: 'DownArrow', 0x52: 'UpArrow',
        0x53: 'NumLock', 0x54: 'KeypadSlash', 0x55: 'KeypadAsterisk',
        0x56: 'KeypadMinus', 0x57: 'KeypadPlus', 0x58: 'KeypadEnter',
        0x59: 'Keypad1', 0x5A: 'Keypad2', 0x5B: 'Keypad3', 0x5C: 'Keypad4',
        0x5D: 'Keypad5', 0x5E: 'Keypad6', 0x5F: 'Keypad7', 0x60: 'Keypad8',
        0x61: 'Keypad9', 0x62: 'Keypad0', 0x63: 'KeypadDecimal',
        0x64: 'NonUSBackslash', 0x65: 'Application', 0x66: 'Power',
        0x67: 'KeypadEqual',
    }
    if 0x3A <= usage <= 0x45:
        return f'F{usage - 0x39}'
    if 0x68 <= usage <= 0x73:
        return f'F{usage - 0x5B}'
    if 0xE0 <= usage <= 0xE7:
        return (
            'LeftControl', 'LeftShift', 'LeftAlt', 'LeftGUI',
            'RightControl', 'RightShift', 'RightAlt', 'RightGUI',
        )[usage - 0xE0]
    return names.get(usage, f'Unknown(0x{usage:02X})')


def decode_boot_report(raw: bytes) -> dict[str, Any]:
    if len(raw) != BOOT_REPORT_BYTES:
        return {'valid': False, 'raw_hex': hex_bytes(raw), 'error': f'expected 8 bytes, received {len(raw)}'}
    modifier_byte = raw[0]
    modifiers = [0xE0 + bit for bit in range(8) if modifier_byte & (1 << bit)]
    slots = list(raw[2:8])
    usages = modifiers + [usage for usage in slots if usage]
    calculator_keys = [
        name for usage in slots if usage
        if (name := calculator_key_name(modifier_byte, usage)) is not None
    ]
    return {
        'valid': True,
        'raw_hex': hex_bytes(raw),
        'modifier_byte': modifier_byte,
        'reserved_byte': raw[1],
        'modifiers': modifiers,
        'key_slots': slots,
        'usages': usages,
        'decoded_usages': [host_key_name(usage) for usage in usages],
        'calculator_keys': calculator_keys,
        'rollover': any(usage in (1, 2, 3) for usage in slots),
    }


ReportToken = tuple[int, int, int]


def report_token_name(token: ReportToken) -> str:
    kind, modifier_byte, usage = token
    if kind == 0:
        return host_key_name(usage)
    return calculator_key_name(modifier_byte, usage) or host_key_name(usage)


def report_log_line(
    timestamp: str,
    raw: bytes,
    previous: set[ReportToken],
    interface: int,
    endpoint: int,
) -> tuple[str, set[ReportToken]]:
    decoded = decode_boot_report(raw)
    if not decoded['valid']:
        return f'{timestamp} IF{interface:02d} IN EP{endpoint:02X} raw={hex_bytes(raw)} MALFORMED {decoded["error"]}', previous
    current = {(0, 0, usage) for usage in decoded['modifiers']}
    current.update(
        (1, decoded['modifier_byte'], usage)
        for usage in decoded['key_slots'] if usage
    )
    transitions = [f'{report_token_name(x)}:UP' for x in sorted(previous - current)]
    transitions += [f'{report_token_name(x)}:DOWN' for x in sorted(current - previous)]
    state = ','.join(transitions) if transitions else ('HELD/UNCHANGED' if current else 'IDLE')
    return (
        f'{timestamp} IF{interface:02d} IN EP{endpoint:02X} report_id=0 '
        f'raw={hex_bytes(raw)} usages={decoded["decoded_usages"]} '
        f'calculator_keys={decoded["calculator_keys"]} state={state}',
        current,
    )


def monitor_hid(seconds: float, output: Path | None) -> None:
    hid = hid_module()
    identity = next(
        (item for item in hid.enumerate(TI_VENDOR_ID, EVO_PRODUCT_ID) if item.get('interface_number') == HID_INTERFACE),
        None,
    )
    if identity is None:
        raise SystemExit('TI-84 Evo HID interface 3 was not found.')
    handle = hid.device()
    lines = []
    previous: set[ReportToken] = set()
    try:
        handle.open_path(identity['path'])
        handle.set_nonblocking(0)
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            try:
                raw = bytes(handle.read(64, min(250, max(1, int((deadline - time.monotonic()) * 1000)))))
            except OSError as error:
                if platform.system() == 'Windows':
                    raise SystemExit(
                        'Windows kbdhid owns this boot-keyboard collection and denied raw reads. '
                        'Use the usbpcap-monitor command instead; it records raw endpoint 0x84 traffic.'
                    ) from error
                raise
            if not raw:
                continue
            stamp = dt.datetime.now().astimezone().isoformat(timespec='milliseconds')
            line, previous = report_log_line(stamp, raw, previous, HID_INTERFACE, HID_ENDPOINT)
            print(line)
            lines.append(line)
    finally:
        try:
            handle.close()
        except OSError:
            pass
    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text('\n'.join(lines) + ('\n' if lines else ''), encoding='utf-8')


def find_tshark(explicit: str | None = None) -> str:
    candidates = [explicit, shutil.which('tshark')]
    if platform.system() == 'Windows':
        candidates.append(r'C:\Program Files\Wireshark\tshark.exe')
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return str(candidate)
    raise SystemExit('tshark was not found. Install Wireshark with USBPcap or pass --tshark.')


def _validated_tshark(tshark: str) -> str:
    has_path_hint = any(separator in tshark for separator in ('/', '\\')) or Path(tshark).is_absolute()
    if has_path_hint:
        resolved = Path(tshark).expanduser().resolve()
    else:
        resolved = Path(shutil.which(tshark) or '')
    if not resolved.is_file():
        raise SystemExit(f'tshark executable was not found: {tshark}')
    if not os.access(resolved, os.X_OK):
        raise SystemExit(f'tshark is not executable: {resolved}')
    return str(resolved)


def run_tshark(
    tshark: str,
    *arguments: str,
    capture_output: bool = False,
    text: bool = False,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [_validated_tshark(tshark), *arguments],
        check=True,
        capture_output=capture_output,
        text=text,
    )


def usbpcap_interfaces(tshark: str) -> list[str]:
    result = run_tshark(tshark, '-D', capture_output=True, text=True)
    interfaces = []
    for line in result.stdout.splitlines():
        if 'USBPcap' not in line:
            continue
        name = line.split('.', 1)[1].strip().split(' ', 1)[0]
        interfaces.append(name)
    return interfaces


def capture_with_tshark(tshark: str, interfaces: Iterable[str], seconds: float, output: Path) -> None:
    command = []
    for interface in interfaces:
        command += ['-i', interface]
    command += ['-a', f'duration:{seconds}', '-w', str(output), '-q']
    run_tshark(tshark, *command)


def locate_usbpcap_device(tshark: str) -> tuple[str, int]:
    interfaces = usbpcap_interfaces(tshark)
    if not interfaces:
        raise SystemExit('Wireshark reported no USBPcap interfaces.')
    with tempfile.TemporaryDirectory(prefix='smartpad-usbpcap-') as temp:
        capture = Path(temp) / 'locate.pcapng'
        capture_with_tshark(tshark, interfaces, 1.0, capture)
        command = [
            '-r', str(capture), '-Y',
            f'usb.idVendor == 0x{TI_VENDOR_ID:04x} && usb.idProduct == 0x{EVO_PRODUCT_ID:04x}',
            '-T', 'fields', '-e', 'frame.interface_id', '-e', 'usb.device_address',
        ]
        result = run_tshark(tshark, *command, capture_output=True, text=True)
        rows = [row.split('\t') for row in result.stdout.splitlines() if row.strip()]
        if not rows:
            raise SystemExit('TI-84 Evo was not present in injected USBPcap descriptors.')
        interface_id, address = rows[0]
        return interfaces[int(interface_id)], int(address)


def sanitize_capture(tshark: str, source: Path, output: Path, address: int) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    run_tshark(tshark, '-r', str(source), '-Y', f'usb.device_address == {address}', '-w', str(output))


def decode_pcap(tshark: str, capture: Path, address: int | None, endpoint: int, output: Path | None) -> list[str]:
    display_filter = f'usb.endpoint_address == 0x{endpoint:02x} && usb.data_len > 0'
    if address is not None:
        display_filter = f'usb.device_address == {address} && {display_filter}'
    command = [
        '-r', str(capture), '-Y', display_filter, '-T', 'fields',
        '-e', 'frame.time_epoch', '-e', 'usb.device_address', '-e', 'usb.data_len',
        '-e', 'usbhid.data', '-e', 'data.data', '-e', 'usb.capdata',
    ]
    result = run_tshark(tshark, *command, capture_output=True, text=True)
    previous: set[ReportToken] = set()
    lines = []
    for row in result.stdout.splitlines():
        if not row.strip():
            continue
        columns = row.split('\t')
        if len(columns) != 6:
            print(f'WARNING: skipped unexpected tshark row: {row}', file=sys.stderr)
            continue
        stamp, _, length_text, hid_hex, generic_hex, capture_hex = columns
        length = int(length_text)
        # Prefer HID-decoded bytes, then the generic data dissector. capdata is
        # accepted only when its length exactly matches the URB transfer length
        # because Wireshark also uses that field for capture-system padding.
        candidates = (hid_hex, generic_hex, capture_hex)
        raw = next(
            (
                candidate
                for text_value in candidates
                if text_value
                for candidate in (bytes.fromhex(text_value.replace(':', '')),)
                if len(candidate) == length
            ),
            None,
        )
        if raw is None:
            print(
                f'WARNING: frame at {stamp} advertises {length} data bytes but tshark exposed no exact payload',
                file=sys.stderr,
            )
            continue
        timestamp = dt.datetime.fromtimestamp(float(stamp), dt.timezone.utc).astimezone().isoformat(timespec='milliseconds')
        line, previous = report_log_line(timestamp, raw, previous, HID_INTERFACE, endpoint)
        print(line)
        lines.append(line)
    if output:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text('\n'.join(lines) + ('\n' if lines else ''), encoding='utf-8')
    return lines


def usbpcap_monitor(seconds: float, pcap: Path, log: Path, tshark_explicit: str | None) -> None:
    tshark = find_tshark(tshark_explicit)
    interface, address = locate_usbpcap_device(tshark)
    print(f'Capturing {interface}, device address {address}, for {seconds:g} seconds.')
    print('Press calculator keys while SmartPad is running. No reports will be sent to the calculator.')
    with tempfile.TemporaryDirectory(prefix='smartpad-usbpcap-') as temp:
        raw_capture = Path(temp) / 'root.pcapng'
        capture_with_tshark(tshark, [interface], seconds, raw_capture)
        sanitize_capture(tshark, raw_capture, pcap, address)
    lines = decode_pcap(tshark, pcap, None, HID_ENDPOINT, log)
    print(f'Preserved calculator-only capture: {pcap}')
    print(f'Decoded {len(lines)} raw HID report(s): {log}')


def read_only_probe() -> None:
    identities = capture_hid_identity()
    identity = next((item for item in identities if item.get('interface_number') == HID_INTERFACE), None)
    if identity is None or 'report_descriptor' not in identity:
        raise SystemExit('Could not read the Evo HID report descriptor.')
    decoded = identity['report_descriptor']
    features = [report for report in decoded['reports'] if report['type'] == 'feature']
    outputs = [report for report in decoded['reports'] if report['type'] == 'output']
    print(hid_descriptor_text(decoded))
    print(f'Feature reports: {features or "none; no GET_REPORT probes attempted"}')
    print(f'Output reports: {outputs or "none"}')
    if outputs:
        print('Output layout is descriptor-defined; no output was written in read-only mode.')


def write_led_output(mask: int, confirm: bool) -> None:
    if not confirm:
        raise SystemExit('Refusing to write. Re-run with --confirm after reviewing the descriptor-defined LED byte.')
    if not 0 <= mask <= 0x1F:
        raise SystemExit('LED mask must be in 0x00..0x1F; upper three bits are constant padding.')
    hid = hid_module()
    identity = next(
        (item for item in hid.enumerate(TI_VENDOR_ID, EVO_PRODUCT_ID) if item.get('interface_number') == HID_INTERFACE),
        None,
    )
    if identity is None:
        raise SystemExit('TI-84 Evo HID interface 3 was not found.')
    handle = hid.device()
    try:
        handle.open_path(identity['path'])
        written = handle.write(bytes((0, mask)))  # hidapi requires a report-ID prefix; this descriptor has ID 0.
        print(f'Wrote standard keyboard LED output mask 0x{mask:02X}; hidapi returned {written}.')
    finally:
        handle.close()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)

    snapshot = sub.add_parser('snapshot', help='capture descriptors and USB/HID identity')
    snapshot.add_argument('--label', required=True)
    snapshot.add_argument('--output', type=Path, required=True)

    compare = sub.add_parser('compare', help='compare two snapshot JSON files')
    compare.add_argument('before', type=Path)
    compare.add_argument('after', type=Path)

    matrix = sub.add_parser('matrix', help='interactively capture the requested five-state matrix')
    matrix.add_argument('--output', type=Path, required=True)

    monitor = sub.add_parser('monitor', help='read raw HID reports through hidapi (best on non-Windows hosts)')
    monitor.add_argument('--seconds', type=float, default=30.0)
    monitor.add_argument('--output', type=Path)

    pcap_monitor = sub.add_parser('usbpcap-monitor', help='capture raw Windows endpoint reports with USBPcap')
    pcap_monitor.add_argument('--seconds', type=float, default=30.0)
    pcap_monitor.add_argument('--pcap', type=Path, required=True)
    pcap_monitor.add_argument('--log', type=Path, required=True)
    pcap_monitor.add_argument('--tshark')

    pcap_decode = sub.add_parser('pcap-decode', help='decode HID reports from an existing USBPcap capture')
    pcap_decode.add_argument('capture', type=Path)
    pcap_decode.add_argument('--device', type=int)
    pcap_decode.add_argument('--endpoint', type=lambda value: int(value, 0), default=HID_ENDPOINT)
    pcap_decode.add_argument('--output', type=Path)
    pcap_decode.add_argument('--tshark')

    sub.add_parser('probe', help='list descriptor-defined output/feature reports without writing')

    led = sub.add_parser('led-output', help='explicitly write only the standard descriptor-defined LED byte')
    led.add_argument('--mask', type=lambda value: int(value, 0), required=True)
    led.add_argument('--confirm', action='store_true')
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.command == 'snapshot':
        captured = take_snapshot(args.label)
        write_snapshot(captured, args.output)
        print(snapshot_text(captured))
    elif args.command == 'compare':
        before = json.loads(args.before.read_text(encoding='utf-8'))
        after = json.loads(args.after.read_text(encoding='utf-8'))
        differences = compare_snapshots(before, after)
        print('\n'.join(differences) if differences else 'No USB identity differences.')
    elif args.command == 'matrix':
        run_matrix(args.output)
    elif args.command == 'monitor':
        monitor_hid(args.seconds, args.output)
    elif args.command == 'usbpcap-monitor':
        usbpcap_monitor(args.seconds, args.pcap, args.log, args.tshark)
    elif args.command == 'pcap-decode':
        decode_pcap(find_tshark(args.tshark), args.capture, args.device, args.endpoint, args.output)
    elif args.command == 'probe':
        read_only_probe()
    elif args.command == 'led-output':
        write_led_output(args.mask, args.confirm)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
