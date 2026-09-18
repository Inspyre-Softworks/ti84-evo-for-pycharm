TI-84 Evo OS 7.1 SmartPad / HID protocol
=========================================

This page records the SmartPad investigation performed on 2026-09-16. It is
deliberately conservative: an interface seen in a descriptor is not treated as
an active protocol, and mappings from TI's public keyboard guide are not used
as substitutes for captured USB reports.

This protocol and diagnostic foundation ships with project release 0.5.0.
It does not enable global keyboard interception or SmartPad IDE bindings in the
production plugin. The capture utility and monitor are explicit developer
tools, kept separate from the known-good CDC/Kermit implementation.

Evidence labels
---------------

``Hardware``
   Observed on a physical TI-84 Evo running BSP ``7.1.0.4413`` and package
   ``7.1.0.4421``.

``Descriptor``
   Present in a descriptor returned by the physical device or in the Windows
   HID model. The source is identified because Windows reconstructs report
   descriptors from preparsed data.

``Packet capture``
   Present in a calculator-only USBPcap capture.

``Inferred``
   A reasoned interpretation that still needs a direct capture.

``Unknown``
   Not established by the captures available so far.

Summary of findings
-------------------

* **Hardware + packet capture:** the calculator enumerated as
  ``0451:E018`` with configuration 1 and four interfaces while it was on the
  ordinary home screen. No SmartPad application was active on the visible
  screen.
* **Hardware:** the interfaces were CDC control, CDC data, a vendor-specific
  bulk interface, and a standard boot-keyboard HID interface. The HID
  interface therefore exists before SmartPad is launched.
* **Descriptor + static package:** the exact 63-byte HID report descriptor is
  a conventional Generic Desktop/Keyboard collection. It has an 8-byte
  boot-keyboard input report, one standard keyboard-LED output byte, no Report
  IDs, no Feature reports, and no vendor-defined usage pages.
* **Static package:** OS 7.0 and 7.1 contain byte-identical USB device and
  configuration descriptors. The report descriptor differs only by widening
  the six-key array's logical/usage maximum from ``0x65`` to ``0xFF``. The
  composite HID interface is therefore not newly introduced in 7.1.
* **Hardware + packet capture:** opening SmartPad did not change VID/PID,
  configuration number, device/configuration/string descriptors, interfaces,
  alternate settings, or endpoints. SmartPad activates the HID interface that
  was already enumerated on the home screen; it does not add an interface.
* **Hardware + packet capture:** every physical key was swept. Reports are
  conventional 8-byte boot-keyboard reports. TI encodes most calculator keys
  as unique Left Control/Shift/Alt + F14--F20 chords; arrows, ``DEL``, ``ON``,
  ``ENTER``, and the arithmetic operators use ordinary keyboard/keypad usages.
* **Hardware + packet capture:** while endpoint ``84 IN`` was producing arrow
  reports, the unchanged CDC/Kermit implementation read ``sys/attributes``,
  ``dynamicinfo``, directory, and screen resources over COM17. A later pull
  downloaded three complete Python variable bodies before encountering an
  unrelated existing decoder limitation on a TI library AppVar.
* **Hardware + packet capture:** held keys can yield identical unchanged
  reports, but the device supplies no device-side repeat presses. A second key
  held with the first does not occupy a second key slot. ``2nd`` and ``ALPHA``
  are normal independent SmartPad key reports; ``ON`` maps to HID Escape.
* **Unknown:** whether launch/exit causes a momentary USB reset not visible in
  the before/after snapshots, whether the standard LED output bits affect the
  calculator, and whether SmartPad is byte-for-byte identical to the older
  Engineering-menu keyboard test at runtime.

Consequently, OS 7.1's SmartPad is built on the standard boot-keyboard surface
already present in the 7.0 firmware. The available evidence does not support
claiming an additional SmartPad-specific HID or vendor protocol.

USB enumeration on the home screen
----------------------------------

The complete device descriptor was:

.. code-block:: text

   12 01 10 02 EF 02 01 40 51 04 18 E0 00 01 01 02 03 01

Decoded:

.. list-table::
   :header-rows: 1

   * - Field
     - Value
   * - USB version
     - ``2.10``
   * - Device class/subclass/protocol
     - ``EF/02/01`` (miscellaneous/IAD composite)
   * - EP0 maximum packet
     - 64 bytes
   * - VID:PID
     - ``0451:E018``
   * - Device version
     - ``1.00``
   * - Manufacturer
     - ``Texas Instruments Incorporated``
   * - Product
     - ``TI-84 Evo``
   * - Serial string
     - ``Evo``
   * - Configurations
     - 1

The exact 123-byte configuration descriptor was:

.. code-block:: text

   09 02 7B 00 04 01 00 C0 01
   08 0B 00 02 02 02 01 00
   09 04 00 00 01 02 02 01 00
   05 24 00 10 01
   05 24 06 00 01
   05 24 01 03 01
   04 24 02 07
   07 05 83 03 10 00 10
   09 04 01 00 02 0A 00 00 00
   07 05 01 02 40 00 00
   07 05 82 02 40 00 00
   09 04 02 00 02 FF 00 00 00
   07 05 05 02 40 00 00
   07 05 86 02 40 00 00
   09 04 03 00 01 03 01 01 00
   09 21 11 01 00 01 22 3F 00
   07 05 84 03 40 00 10

Configuration 1 reports four interfaces, one alternate setting per interface,
self power, and 2 mA maximum power:

.. list-table::
   :header-rows: 1

   * - Interface
     - Class/subclass/protocol
     - Endpoints
     - Interpretation
   * - 0, alt 0
     - ``02/02/01``
     - ``83 IN``, interrupt, 16 bytes, interval 16
     - CDC ACM control
   * - 1, alt 0
     - ``0A/00/00``
     - ``01 OUT`` and ``82 IN``, bulk, 64 bytes
     - CDC data used by Kermit
   * - 2, alt 0
     - ``FF/00/00``
     - ``05 OUT`` and ``86 IN``, bulk, 64 bytes
     - Vendor-specific; purpose not established here
   * - 3, alt 0
     - ``03/01/01``
     - ``84 IN``, interrupt, 64 bytes, interval 16
     - HID boot keyboard

There is no separate HID OUT endpoint. HID output reports, if accepted, use a
class control transfer through endpoint zero.

The firmware package contains a device-descriptor template whose final byte is
``02`` (two configurations), whereas the live home-screen device returned
``01``. It also contains this 78-byte second configuration, identically in 7.0
and 7.1:

.. code-block:: text

   09 02 4E 00 01 02 00 C0 01
   09 04 00 00 02 FF 00 00 00
   07 05 01 02 40 00 00
   07 05 82 02 40 00 00
   09 04 00 01 02 FF 00 00 00
   07 05 01 02 40 00 00
   07 05 82 03 40 00 01
   09 04 00 02 02 FF 00 00 00
   07 05 01 02 40 00 00
   07 05 82 03 40 00 08

It is configuration value 2 with one vendor-specific interface and three
alternate settings. Endpoint ``01 OUT`` remains bulk; endpoint ``82 IN`` is
bulk at alt 0 and interrupt (interval 1 or 8) at alts 1 and 2. Because the
runtime device advertised only one configuration, this appears to be a hidden
or diagnostic configuration. Its purpose and runtime selection mechanism are
**unknown**; the diagnostic tool does not attempt ``SET_CONFIGURATION(2)``.

Enumeration-state matrix
------------------------

Home-screen and active-SmartPad snapshots were captured independently and
compared field by field. The only JSON difference was capture provenance for
the Windows-reconstructed report descriptor; the USB identity itself was
identical. The transition was not captured continuously, so a momentary reset
or re-enumeration during launch/exit remains unknown.

.. list-table::
   :header-rows: 1

   * - State
     - VID:PID / interfaces
     - Status
   * - Connected on home screen
     - ``0451:E018``; CDC control/data + vendor bulk + HID boot keyboard
     - **Hardware + packet capture**
   * - Connected with SmartPad open
     - ``0451:E018``; configuration 1; the same four interfaces/endpoints
     - **Hardware + packet capture; identical snapshot**
   * - SmartPad running before connection
     - Active reports observed after reconnect; identity remained ``0451:E018``
     - **Hardware for resulting state; transition not continuously captured**
   * - USB connected before SmartPad launch
     - Active reports observed with the same connected composite device
     - **Hardware for resulting state; launch reset still unknown**
   * - SmartPad exited while connected/reconnected
     - Not captured
     - **Unknown**

HID report descriptor
---------------------

The live interface HID descriptor advertises report descriptor type ``22``
and a length of **63 bytes**:

.. code-block:: text

   09 21 11 01 00 01 22 3F 00

The OS 7.1 update package contains the following exact 63-byte descriptor,
matching that advertised length:

.. code-block:: text

   05 01 09 06 A1 01 05 07 19 E0 29 E7 15 00 25 01
   75 01 95 08 81 02 95 01 75 08 81 01 95 05 75 01
   05 08 19 01 29 05 91 02 95 01 75 03 91 01 95 06
   75 08 15 00 25 FF 05 07 19 00 29 FF 81 00 C0

Windows/hidapi independently reconstructed a semantically equivalent 65-byte
descriptor from HID preparsed data. Its extra two bytes and reordered global
items are a Windows reconstruction artifact, not the device's raw descriptor.
A physical ``GET_DESCRIPTOR(Report)`` packet is still desirable, but the live
advertised length and static package now agree.

Human-readable reconstruction:

.. code-block:: text

   Usage Page (Generic Desktop)
   Usage (Keyboard)
   Collection (Application)
     Usage Page (Keyboard/Keypad)
     Usage Minimum (Keyboard LeftControl, E0)
     Usage Maximum (Keyboard Right GUI, E7)
     Logical Minimum 0; Logical Maximum 1
     Report Size 1; Report Count 8
     Input (Data, Variable, Absolute)
     Report Count 1; Report Size 8
     Input (Constant, Array, Absolute)
     Usage Page (LEDs)
     Usage Minimum (Num Lock); Usage Maximum (Kana)
     Report Count 5; Report Size 1
     Output (Data, Variable, Absolute)
     Report Count 1; Report Size 3
     Output (Constant, Array, Absolute)
     Report Count 6; Report Size 8
     Logical Minimum 0; Logical Maximum 255
     Usage Page (Keyboard/Keypad)
     Usage Minimum 00; Usage Maximum FF
     Input (Data, Array, Absolute)
   End Collection

Report layouts
~~~~~~~~~~~~~~

There are no Report ID items. The 8-byte input layout is the USB HID boot
keyboard layout:

.. list-table::
   :header-rows: 1

   * - Byte(s)
     - Meaning
   * - 0
     - Modifier bitmap: Left Control, Left Shift, Left Alt, Left GUI, Right
       Control, Right Shift, Right Alt, Right GUI
   * - 1
     - Constant/reserved byte; preserved by the decoder
   * - 2--7
     - Six Keyboard/Keypad usage slots; zero means no key

The one-byte output layout is:

.. list-table::
   :header-rows: 1

   * - Bits
     - Meaning
   * - 0--4
     - Num Lock, Caps Lock, Scroll Lock, Compose, Kana LEDs
   * - 5--7
     - Constant padding; must be zero

No Feature fields, vendor-defined usage pages, extra Report IDs, or additional
HID endpoints appear in either the exact descriptor or Windows model. The read-only ``probe`` command
does not issue speculative ``GET_REPORT`` requests. ``led-output`` is the only
write command and requires both an explicit value in ``0..31`` and
``--confirm``; it has not been run on the test calculator.

Raw SmartPad reports and calculator-key map
-------------------------------------------

The map below comes from a physical top-left-to-bottom-right sweep, repeated
with partial sweeps and decoded from 224 raw reports. It was not copied from
TI's online-calculator mapping. Modifier names are standard HID bits in byte 0;
the named usage occupies byte 2 and bytes 3--7 are zero.

.. list-table::
   :header-rows: 1

   * - Calculator key
     - Modifier byte
     - HID usage / host key
     - Press report
   * - ``Y=``
     - ``00``
     - ``6F`` / F20
     - ``00 00 6F 00 00 00 00 00``
   * - ``WINDOW``
     - ``05`` (Left Control + Left Alt)
     - ``6C`` / F17
     - ``05 00 6C 00 00 00 00 00``
   * - ``ZOOM``
     - ``05`` (Left Control + Left Alt)
     - ``6B`` / F16
     - ``05 00 6B 00 00 00 00 00``
   * - ``TRACE``
     - ``03`` (Left Control + Left Shift)
     - ``6F`` / F20
     - ``03 00 6F 00 00 00 00 00``
   * - ``GRAPH``
     - ``06`` (Left Shift + Left Alt)
     - ``6C`` / F17
     - ``06 00 6C 00 00 00 00 00``
   * - ``2nd``
     - ``05``
     - ``6E`` / F19
     - ``05 00 6E 00 00 00 00 00``
   * - ``MODE``
     - ``05``
     - ``6F`` / F20
     - ``05 00 6F 00 00 00 00 00``
   * - ``DEL``
     - ``00``
     - ``4C`` / Delete
     - ``00 00 4C 00 00 00 00 00``
   * - Up
     - ``00``
     - ``52`` / Up Arrow
     - ``00 00 52 00 00 00 00 00``
   * - ``ALPHA``
     - ``03``
     - ``6D`` / F18
     - ``03 00 6D 00 00 00 00 00``
   * - ``X,T,theta,n``
     - ``04`` (Left Alt)
     - ``6B`` / F16
     - ``04 00 6B 00 00 00 00 00``
   * - ``STAT``
     - ``01`` (Left Control)
     - ``6C`` / F17
     - ``01 00 6C 00 00 00 00 00``
   * - Left / Right / Down
     - ``00``
     - ``50`` / ``4F`` / ``51``
     - ``00 00 50|4F|51 00 00 00 00 00``
   * - ``MATH``
     - ``03``
     - ``6C`` / F17
     - ``03 00 6C 00 00 00 00 00``
   * - fraction-template key (``FRAC``)
     - ``01``
     - ``6F`` / F20
     - ``01 00 6F 00 00 00 00 00``
   * - ``PRGM``
     - ``01``
     - ``6B`` / F16
     - ``01 00 6B 00 00 00 00 00``
   * - ``VARS``
     - ``02`` (Left Shift)
     - ``6E`` / F19
     - ``02 00 6E 00 00 00 00 00``
   * - ``CLEAR``
     - ``02``
     - ``6C`` / F17
     - ``02 00 6C 00 00 00 00 00``
   * - ``x^-1`` / ``SIN`` / ``COS`` / ``TAN``
     - ``02`` / ``01`` / ``02`` / ``02``
     - ``6B`` / ``6E`` / ``6F`` / ``6D`` (F16/F19/F20/F18)
     - ``02 00 6B``, ``01 00 6E``, ``02 00 6F``, ``02 00 6D`` + five zeros
   * - Divide
     - ``00``
     - ``54`` / Keypad ``/``
     - ``00 00 54 00 00 00 00 00``
   * - ``x^2`` / comma / ``(`` / ``)``
     - ``04`` / ``01`` / ``06`` / ``06``
     - ``6F`` / ``6D`` / ``6E`` / ``6D`` (F20/F18/F19/F18)
     - ``04 00 6F``, ``01 00 6D``, ``06 00 6E``, ``06 00 6D`` + five zeros
   * - Multiply
     - ``00``
     - ``55`` / Keypad ``*``
     - ``00 00 55 00 00 00 00 00``
   * - ``LOG`` / ``7`` / ``8`` / ``9``
     - ``02`` / ``04`` / ``05`` / ``03``
     - ``69`` / ``6E`` / ``6A`` / ``69`` (F14/F19/F15/F14)
     - ``02 00 69``, ``04 00 6E``, ``05 00 6A``, ``03 00 69`` + five zeros
   * - Minus
     - ``00``
     - ``56`` / Keypad ``-``
     - ``00 00 56 00 00 00 00 00``
   * - ``LN`` / ``4`` / ``5`` / ``6``
     - ``04`` / ``05`` / ``06`` / ``00``
     - ``6D`` / ``69`` / ``6B`` / ``6D`` (F18/F14/F16/F18)
     - ``04 00 6D``, ``05 00 69``, ``06 00 6B``, ``00 00 6D`` + five zeros
   * - Plus
     - ``00``
     - ``57`` / Keypad ``+``
     - ``00 00 57 00 00 00 00 00``
   * - ``STO->`` / ``1`` / ``2`` / ``3`` / fraction-decimal toggle
     - ``04`` / ``02`` / ``00`` / ``00`` / ``03``
     - ``6C`` / ``6A`` / ``6B`` / ``6C`` / ``6B``
     - ``04 00 6C``, ``02 00 6A``, ``00 00 6B``, ``00 00 6C``, ``03 00 6B`` + five zeros
   * - ``ON``
     - ``00``
     - ``29`` / Escape
     - ``00 00 29 00 00 00 00 00``
   * - ``0`` / decimal point / ``(-)``
     - ``03`` / ``00`` / ``06``
     - ``6A`` / ``6E`` / ``6F`` (F15/F19/F20)
     - ``03 00 6A``, ``00 00 6E``, ``06 00 6F`` + five zeros
   * - ``ENTER``
     - ``00``
     - ``28`` / Enter
     - ``00 00 28 00 00 00 00 00``

The tested Evo has a fraction-template key rather than an ``APPS``-labelled
key; therefore no separate ``APPS`` mapping exists in this physical map.

Each ordinary press is followed by ``00 00 00 00 00 00 00 00`` on release.
A held key sometimes causes the same 8-byte state to be retransmitted, but no
additional DOWN transition is generated by the device; OS keyboard repeat is
host-side behavior. During ``2nd`` + ``Y=`` and ``ALPHA`` + ``X,T,theta,n``
tests, the first key remained the sole reported key. During Left + Right, the
report remained Left. Although the descriptor allocates six key slots,
SmartPad was empirically single-key-only in these tests.

Representative raw log lines are:

.. code-block:: text

   18:35:51.103 IF03 IN EP84 id=0 00 00 6F 00 00 00 00 00  Y=/F20 DOWN
   18:35:52.815 IF03 IN EP84 id=0 00 00 00 00 00 00 00 00  Y=/F20 UP
   18:37:28.285 IF03 IN EP84 id=0 00 00 50 00 00 00 00 00  LEFT DOWN
   18:37:30.141 IF03 IN EP84 id=0 00 00 50 00 00 00 00 00  HELD/UNCHANGED
   18:28:06.198 IF03 IN EP84 id=0 03 00 69 00 00 00 00 00  9 DOWN
   18:28:06.294 IF03 IN EP84 id=0 02 00 69 00 00 00 00 00  9 UP, LOG DOWN

Tapping ``2nd`` and then ``ALPHA`` (the calculator's Alpha Lock sequence)
produces the ordinary ``2nd`` press/release followed by the ordinary
``ALPHA`` press/release. There is no separate Alpha Lock usage or state report.

The decoder handles press, release, held/unchanged state, all eight modifier
bits, six input slots, rollover/error usages, unknown usages, malformed
lengths, and optional Report IDs for future descriptors. It keeps the raw
report, reserved byte, and slot order. Calculator mapping is keyed by the full
modifier-byte + usage chord, because usage alone is not unique.

CDC and HID coexistence
-----------------------

CDC/Kermit and active SmartPad HID coexist, not merely at enumeration time. In
a single timed test, endpoint ``84 IN`` delivered 67 arrow press/release reports
while the unchanged production serial/Kermit stack opened COM17 and completed
all four read-only requests below:

* ``hh01/get/hh01/sys/attributes`` -- 197-byte CBOR response;
* ``hh01/get/hh01/inf/res?name=dynamicinfo`` -- 66-byte CBOR response;
* directory listing -- 1465-byte CBOR response containing 23 entries;
* ``hh01/get/hh01/sys/screen`` -- 153635-byte CBOR response.

A subsequent project pull received complete bodies for ``GRAPH`` (976 bytes),
``HELLO`` (258 bytes), and ``LINREGR`` (641 bytes) while SmartPad remained in
scope. It then stopped at ``TI_DRAW`` because the existing Python decoder does
not recognize that TI library AppVar header. This is a decoding limitation
after transfer, not evidence of CDC or SmartPad interference; no calculator
writes were attempted. The pull's transactional design did not materialize
the three partial results as project files after the later decode failure.

These results establish simultaneous ``CDC + SmartPad HID`` operation on the
physical OS 7.1 calculator. SmartPad neither removes nor blocks the CDC
interfaces, and the known-good Kermit implementation requires no replacement.

Bidirectional behavior
----------------------

At the HID layer, endpoint ``84`` is input-only. The descriptor's only
host-to-device field is the standard one-byte keyboard LED output report,
delivered over endpoint zero because there is no HID OUT endpoint. No Feature
report, vendor usage, acknowledgement, status input, or SmartPad command
channel is descriptor-defined. The LED output has not been sent, so whether
the calculator consumes it or simply accepts/ignores it is **unknown**.

The pre-existing composite interfaces remain bidirectional: CDC has
``01 OUT``/``82 IN``, and the separate vendor interface has
``05 OUT``/``86 IN``. Nothing in the descriptor associates either with
SmartPad. Static OS 7.1 strings add the candidate token
``exitsmartpadapp``, but calling semantics and direction are unknown.

The existing ``hh01/sys/scancode`` path supplies the opposite direction,
host-to-calculator key injection. It was not changed or bridged automatically.
A future opt-in experiment can pair captured HID events with that existing
resource now that active CDC/HID coexistence is confirmed. It must tag origins,
suppress echoes, and never forward injected events back into the calculator.

OS 7.0 versus 7.1 resource data
-------------------------------

The following values came from physical hardware. The per-device ``id`` is
intentionally redacted in documentation; the diagnostic command saves the
unmodified CBOR locally.

.. list-table::
   :header-rows: 1

   * - ``sys/attributes`` field
     - OS 7.0 capture
     - OS 7.1 capture
   * - ``product``
     - ``23-10-28-2100``
     - ``23-10-28-21EF``
   * - ``bl1-version``
     - ``1.0.0.260``
     - ``1.0.0.260``
   * - ``bl2-version``
     - ``7.0.0.3386``
     - ``7.0.0.3386``
   * - ``bsp-version``
     - ``7.0.0.3992``
     - ``7.1.0.4413``
   * - ``pkg-version``
     - ``7.0.0.3996``
     - ``7.1.0.4421``
   * - ``total-ram`` / ``total-flash``
     - ``961536`` / ``3141632``
     - ``961536`` / ``3141632``
   * - ``battery`` / ``charging``
     - ``5`` / ``1``
     - ``5`` / ``1``
   * - ``dev-cert`` / ``dbg-cert``
     - ``no`` / ``no``
     - ``no`` / ``no``

The OS 7.1 ``dynamicinfo`` response decoded as:

.. code-block:: text

   {
     "metaData": {"type": 58, "version": 1},
     "ram": 498708,
     "archive": 2886672,
     "language": 1,
     "ptt": false
   }

Its complete 66-byte CBOR response was:

.. code-block:: text

   BF 68 6D 65 74 61 44 61 74 61 BF 64 74 79 70 65 18 3A
   67 76 65 72 73 69 6F 6E 01 FF 63 72 61 6D 1A 00 07 9C
   14 67 61 72 63 68 69 76 65 1A 00 2C 0C 10 68 6C 61 6E
   67 75 61 67 65 01 63 70 74 74 F4 FF

The ``sys/attributes`` response was 197 bytes. Its complete field topology and
values are represented in the table above; the four-byte per-device ``id`` is
redacted from the repository documentation rather than publishing a hardware
identifier. The new diagnostic command stores the untouched response locally
so future unknown fields and exact encodings remain available for comparison.

No new field was found in the 7.1 ``sys/attributes`` response. A raw OS 7.0
``dynamicinfo`` response and an OS 7.0 USB descriptor capture were not
available. However, the 7.0 and 7.1 firmware packages contain identical
device, configuration 1, and hidden configuration 2 descriptor templates.
Only two report-descriptor bytes changed: OS 7.0 limited the six-key input
array to logical/usage maximum ``0x65``; OS 7.1 uses ``0xFF``. No new
interface, endpoint, configuration, Report ID, output report, or Feature report
was introduced in the package descriptors. No new callable ``hh01`` resource
was confirmed on hardware.

Application and Engineering-menu investigation
-----------------------------------------------

TI's public 7.1 page and SmartPad guide identify SmartPad as the new user
feature. Static comparison used the published US packages with these hashes:

.. list-table::
   :header-rows: 1

   * - Package
     - Bundle metadata
     - SHA-256 of outer ``.84b2``
   * - OS 7.0
     - ``7.0.0.3996``
     - ``7078A8AA068148D7420BEA63DEB60EC5A1581E9E7976A6D09EB48AF88096BE9E``
   * - OS 7.1
     - ``7.1.0.4421``
     - ``86FE1B026BBEA4538511F6B3241B19C77361DFB95259D0105C941A2459953A96``

Both packages contain ``HID Keyboard Test`` and ``Smart Pad - Test`` strings,
the same device/configuration descriptors, and almost the same HID report
descriptor. OS 7.1 newly adds the user-facing ``SmartPad``/``SmartPadScreen``
strings, localized launch/stop text, ``usbHidStates``, and
``exitsmartpadapp``. The last name sits near known resource tokens such as
``attributes``, ``settings``, and ``reboot``, but it was not invoked and must
not yet be described as a callable ``hh01`` endpoint.

TI-Planet independently reports that OS 7.1's Engineering menu has
``Utilities -> SmartPad`` as well as the older
``Tests -> Link -> HID Device Test -> Keyboard``. Together, the static
descriptor/string evidence makes it a **strong inference** that the public
SmartPad app is a user-facing wrapper over the pre-existing firmware keyboard
facility. A simultaneous packet capture of both menu paths is still required
before calling their report streams byte-identical.

A string search of the public TI Connect Evo web application did not find
SmartPad-specific resource names, ``dynamicinfo``, or ``sys/scancode``; that
web application is not the calculator OS bundle, so this is weak negative
evidence only. No separately downloadable SmartPad application appeared in
the calculator variable directory.

Diagnostic tools
----------------

Install the isolated Python dependencies:

.. code-block:: powershell

   py -m pip install -r scripts/requirements-smartpad.txt

Capture and compare the five enumeration states:

.. code-block:: powershell

   py scripts/smartpad_usb.py matrix --output captures/smartpad-os-7.1/matrix

On Windows, capture raw endpoint ``84`` traffic without relying on operating
system keyboard events:

.. code-block:: powershell

   py scripts/smartpad_usb.py usbpcap-monitor --seconds 120 `
     --pcap captures/smartpad-os-7.1/active/calculator-only.pcapng `
     --log captures/smartpad-os-7.1/active/reports.log

The command locates the correct USBPcap root, discovers the calculator's
current USB address, captures to a temporary root trace, writes only frames for
that device to the requested pcap, and decodes endpoint ``84``. The temporary
root trace is discarded so unrelated USB traffic is not retained.

Useful Wireshark display filters are:

.. code-block:: text

   usb.idVendor == 0x0451 && usb.idProduct == 0xe018
   usb.device_address == <captured-address> && usb.endpoint_address == 0x84
   usb.device_address == <captured-address> &&
     (usb.endpoint_address == 0x01 || usb.endpoint_address == 0x82 ||
      usb.endpoint_address == 0x84)

Save raw resources without discarding unknown CBOR keys or byte strings:

.. code-block:: powershell

   .\gradlew cliJar
   java -jar build/libs/ti84-evo-cli.jar diagnose-resources `
     --output captures/smartpad-os-7.1/resources `
     --include-directory --include-screen

Each resource is saved as untouched ``.cbor``, a lossless diagnostic text
rendering, and a SHA-256 entry in ``manifest.tsv``. Additional explicitly known
resource URIs can be supplied with repeated ``--resource`` arguments.

Implementation boundary and recommendations
-------------------------------------------

The production Kermit code was not changed. New parsing and monitoring logic
is independent of PyCharm UI classes:

* ``SmartPadHidDescriptor`` losslessly parses HID items and report fields.
* ``SmartPadReportDecoder`` creates state and transition events while retaining
  unknown bytes/usages.
* ``SmartPadDeviceDetector`` keeps HID presence separate from observed
  SmartPad activity.
* ``SmartPadMonitor`` formats UI-independent diagnostic frames.

Based only on confirmed behavior, the safe plugin candidates are:

* an opt-in SmartPad monitor showing calculator key, full HID chord, raw report,
  and press/release/unchanged state;
* a mapping inspector initialized from the captured 50-key table and able to
  retain/display unknown future chords;
* opt-in calculator-key-to-IDE-action bindings, registered only while the
  monitor owns the selected TI HID device and never as global keyboard hooks;
* device status that distinguishes ``HID interface present`` from
  ``SmartPad input observed`` and can show confirmed ``CDC + SmartPad HID``;
* an experimental bidirectional bridge using the existing
  ``hh01/sys/scancode`` path, guarded by origin tags, deduplication, and an
  explicit loop-prevention switch.

The captured single-key behavior means action binding should match the entire
modifier-byte + key usage atomically; treating TI's synthetic Ctrl/Shift/Alt
bits as ordinary IDE modifiers would create unintended shortcuts. LED output,
the vendor interface, and ``exitsmartpadapp`` should not be exposed as plugin
features until their behavior is confirmed.

External references
-------------------

* `TI-84 Evo OS update page <https://education.ti.com/en/products/calculators/graphing-calculators/ti-84-evo/update>`_
* `TI SmartPad guide <https://education.ti.com/en/product-resources/eguides/eguide-84-evo/smartpad>`_
* `TI online-calculator keyboard mapping <https://education.ti.com/en/product-resources/eguides/eguide-84-evo-online-calculator/keyboard-mapping>`_
* `hidapi Windows report-descriptor implementation <https://github.com/libusb/hidapi/blob/master/windows/hid.c>`_
* `CEyboard prior TI-84 Plus CE keyboard work <https://github.com/TheLastMillennial/CEyboard>`_
* `TI-Planet OS 7.1 and Engineering-menu investigation <https://tiplanet.org/forum/viewtopic.php?lang=en&t=27494>`_
