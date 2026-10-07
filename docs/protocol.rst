Protocol overview
=================

The OS 7.1 :term:`SmartPad` :term:`HID` work is kept separate from this
:term:`CDC`/:term:`Kermit` stack. See
:doc:`smartpad` for the captured composite USB descriptors, HID report model,
raw-capture tooling, and current unknowns.

The implementation is split into transport, transaction, framing, resource,
and Python-upload layers:

Layer responsibilities
----------------------

.. list-table::
   :header-rows: 1
   :widths: 30 70

   * - Layer
     - Responsibility
   * - UI and service
     - Run operations away from the UI thread and present progress and failures
   * - Resource and variable operations
     - Address calculator endpoints and interpret their CBOR envelopes
   * - Transaction engine
     - Coordinate the ordered Kermit request/response exchange
   * - Packet codec
     - Encode lengths, quoting, repetition, sequences, and block checks
   * - Serial transport
     - Detect the Evo USB CDC port and move complete packet bytes

.. graphviz::
   :align: center
   :class: only-light architecture-diagram

   digraph architecture {
       rankdir=TB;
       splines=ortho;

       graph [
           bgcolor="#ffffff",
           pad=0.25,
           nodesep=0.35,
           ranksep=0.5
       ];

       node [
           shape=box,
           style="rounded,filled",
           fillcolor="#f3fbf5",
           color="#269745",
           fontcolor="#173b22",
           fontname="Arial",
           fontsize=10,
           margin="0.18,0.10",
           penwidth=1.2
       ];

       edge [
           color="#52605a",
           fontcolor="#365141",
           fontname="Arial",
           fontsize=9,
           arrowsize=0.7
       ];

       tool [label="PyCharm tool window"];
       service [label="EvoDeviceService"];

       evolink [label="EvoLink"];
       python_transfer [label="EvoPythonTransfer"];

       transaction [label="EvoTransactionEngine"];
       python_payload [label="EvoPythonPayload"];

       codec [label="KermitPacketCodec"];
       transport [label="EvoSerialTransport"];

       tool -> service;

       service -> evolink [label="resource reads"];
       service -> python_transfer [label="Python uploads"];

       evolink -> transaction;
       python_transfer -> python_payload;

       transaction -> codec [label="resource compatibility"];
       python_payload -> codec;

       codec -> transport;

       { rank = same; evolink; python_transfer; }
       { rank = same; transaction; python_payload; }
   }

.. graphviz::
   :align: center
   :class: only-dark architecture-diagram

   digraph architecture_dark {
       rankdir=TB;
       splines=ortho;

       graph [
           bgcolor="#131416",
           pad=0.25,
           nodesep=0.35,
           ranksep=0.5
       ];

       node [
           shape=box,
           style="rounded,filled",
           fillcolor="#241a31",
           color="#a161f0",
           fontcolor="#eadffd",
           fontname="Arial",
           fontsize=10,
           margin="0.18,0.10",
           penwidth=1.2
       ];

       edge [
           color="#b49acb",
           fontcolor="#c7b7d6",
           fontname="Arial",
           fontsize=9,
           arrowsize=0.7
       ];

       tool [label="PyCharm tool window"];
       service [label="EvoDeviceService"];

       evolink [label="EvoLink"];
       python_transfer [label="EvoPythonTransfer"];

       transaction [label="EvoTransactionEngine"];
       python_payload [label="EvoPythonPayload"];

       codec [label="KermitPacketCodec"];
       transport [label="EvoSerialTransport"];

       tool -> service;

       service -> evolink [label="resource reads"];
       service -> python_transfer [label="Python uploads"];

       evolink -> transaction;
       python_transfer -> python_payload;

       transaction -> codec [label="resource compatibility"];
       python_payload -> codec;

       codec -> transport;

       { rank = same; evolink; python_transfer; }
       { rank = same; transaction; python_payload; }
   }

Transaction flow
----------------

Most resource reads and writes use the same negotiated transaction ladder. The
descriptor and data direction change by operation, while acknowledgements gate
each step.

.. graphviz::
   :align: center

   digraph transaction_flow {
       rankdir=LR;
       graph [bgcolor="transparent", pad=0.15, nodesep=0.22, ranksep=0.4];
       node [shape=circle, style="filled", fillcolor="#f3fbf5",
             color="#269745", fontcolor="#173b22", fontname="Arial",
             fontsize=10, width=0.46, fixedsize=true];
       edge [color="#52605a", fontcolor="#365141", fontname="Arial",
             fontsize=9, arrowsize=0.7];

       s [label="S"];
       f [label="F"];
       a [label="A"];
       d [label="D"];
       z [label="Z"];
       b [label="B"];

       s -> f [label="negotiate"];
       f -> a [label="descriptor"];
       a -> d [label="attributes"];
       d -> d [label="more data"];
       d -> z [label="complete"];
       z -> b [label="finish"];
   }

``S`` negotiates transfer capabilities, ``F`` carries the endpoint descriptor,
``A`` announces attributes, one or more ``D`` packets carry data, ``Z`` ends
the file, and ``B`` ends the transaction. See :term:`transaction ladder` and
:term:`block check` for concise definitions.

Transport setup
---------------

The calculator's USB CDC serial port is opened at 115200 baud with 8 data
bits, no parity, one stop bit, and flow control disabled. Both DTR and RTS are
asserted after opening the port, matching the control-line state used by a
working calculator directory session.

Read path
---------

The :term:`resource` path uses the observed
:term:`transaction ladder`. It supports resource requests such as
``hh01/get/hh01/sys/attributes`` and ``hh01/get/hh01/sys/screen`` in the
:term:`hh01` namespace. Screen data is
decoded from the Evo run encoding and converted from little-endian
:term:`RGB565` to a Java image. The file browser reads
``hh01/get/hh01/inf/res?name=directory`` and decodes the returned
:term:`CBOR` entries, including Evo :term:`tokenized name` values and memory
locations. The directory is a :term:`dynamic resource`: its transfers use a zero
length as an unknown-size sentinel
and apply Kermit :term:`control quoting` and :term:`repeat encoding` across
their :term:`D frame` payloads; the reader decodes the complete wire stream
before parsing CBOR.

Individual variables are downloaded through
``hh01/get/hh01/xfr/var?name=...&type=...``. The complete CBOR envelope can be
inspected and exported with the Evo :term:`checksum` restored. Native number, list, and
matrix payloads are decoded into editable real, fraction, complex, and tabular
text. Creation/replacement uses the firmware's type-60 ASCII import envelope, so
the calculator remains responsible for encoding edited values back to native form.

Saving an existing :term:`RAM` variable to :term:`Archive` downloads that envelope, restores
the two-byte Evo file checksum, and uploads it through
``hh01/xfr/var?memtarget=1&policy=1``. The client then reads the directory again
and requires the same tokenized name and type to appear in Archive.

Delete path
-----------

The firmware's delete route template is::

   hh01/del/%s

For calculator variables, ``%s`` is replaced by this resource descriptor::

   var?name=<percent-encoded-token-name>&type=<type-id>

The resulting ``F`` packet therefore has this form::

   hh01/del/var?name=<percent-encoded-token-name>&type=<type-id>

The name and numeric type ID both come from the calculator directory entry.
Each little-endian token word in ``tokName`` is converted to its UTF-8 bytes,
then each byte is written as an uppercase ``%XX`` sequence. This preserves
built-in names, custom lists, Python programs, and other variable types without
reconstructing a name from display text.

Deletion uses a complete ``S/F/A/D/Z/B`` upload transaction for each ordinary
variable and custom list.
The ``F`` packet carries the resolved delete route, ``A`` announces a one-byte
payload, and ``D`` carries a logical NUL byte using the control quoting
negotiated by the ``S`` exchange. Sending the NUL as an unquoted raw byte can
still receive acknowledgements while leaving the variable unchanged, so the
delete path shares the proven upload encoder used by normal variable transfers.

An acknowledged transaction is not sufficient evidence of deletion. After
every attempt, the client closes and reopens the serial session, reads
``hh01/get/hh01/inf/res?name=directory``, and searches for the same
tokenized name and type ID. The operation reports progress—and the plugin
removes its table row—only when that entry is absent. This check also handles a
timeout after the calculator applied the request but before it returned the
final acknowledgement.

If the entry remains, or the first verification read fails, the idempotent
request is attempted once more. A variable still present after two attempts is
reported as a failure. Multi-selection deletion performs and verifies entries
one at a time, preserving the confirmed prefix for accurate partial-failure
reporting in both the plugin and CLI.

Persistent built-in lists
~~~~~~~~~~~~~~~~~~~~~~~~~

The calculator's ``L1`` through ``L6`` names are persistent List Editor slots,
so a user-facing delete clears their contents instead of leaving the slot
absent. The client first downloads and decodes the native type-1 envelope. An
already-empty list completes without a transfer. Otherwise, it rebuilds the
calculator's native empty-list form: ``len`` is zero; ``arraylen``, ``size``,
and ``data`` are absent; the two-byte built-in token name is retained without
the trailing NUL padding returned for populated lists; and the Evo checksum is
restored.

The populated variable is then removed through the verified delete route and
the empty native envelope is uploaded through
``hh01/xfr/var?memtarget=<0-or-1>&policy=1``. A final directory read must find
the same type-1 token, and downloading it must decode to no values. Only then is
the operation reported as cleared. This delete-and-recreate sequence is needed
because uploading an empty envelope over an existing built-in list can cause
the firmware to remove the slot instead of retaining it.

Variable presence and List Editor visibility are separate calculator state. To
make a newly added or cleared built-in list visible, the client sends the
calculator key sequence ``2nd``, ``Mode/Quit``, ``Stat``, ``5``, ``Enter``. Each
key is a CBOR indefinite array containing its calculator scancode, uploaded to::

   hh01/sys/scancode

The five ``F/A/D/Z`` key transactions share one negotiated Kermit session and
are followed by ``B``. This executes the calculator's ``SetUpEditor`` command,
which restores the default L1–L6 columns without changing their values. An
already-empty built-in list still runs this restoration because its directory
entry does not prove that its editor column is visible.

The corresponding hardware-confirmed scancodes are tracked independently from
SmartPad HID chords: ``2nd=36``, ``Mode=37``, ``Stat=20``, ``5=1B``, and
``Enter=09`` (hexadecimal). Release 0.8.0 exposes these through ``EvoKey`` and
``EvoScancodeMap``. No other key is marked confirmed or inferred yet. A future
complete map must come from the explicit, user-driven hardware probe rather
than assuming that HID usages and calculator scancodes correspond.

Python upload path
------------------

Python source is packaged as a type-15 Evo Python :term:`AppVar` and wrapped in
the CBOR variable-transfer representation expected by the calculator. Kermit
send-init negotiation selects the :term:`block check` type and maximum packet
size. The sender supports :term:`long packet` transfers, control quoting,
repeat encoding, and element-aligned data chunks, then transfers the payload
through the Evo variable :term:`endpoint`. The plugin does not treat the
calculator as a normal desktop filesystem and does not upload the bundled
editor stubs.

Python launch path
------------------

The Evo protocol does not expose a confirmed execute-by-name operation. After
project synchronization, the launcher deliberately reads::

   hh01/get/hh01/inf/res?name=directory&gotohome=1

This returns the calculator to a deterministic Calculator-app state while also
providing the RAM Python program list. Archived type-15 entries are rejected
because the Python runtime executes source from RAM.

The launcher then sends ``PRGM``, ``Down``, and ``Enter`` to open the Python
File Manager. Once Python has initialized, it sends the target's first-letter
scancode to use File Manager alpha search, moves down only among matching
initials, and sends ``Y=`` for the on-screen **Run** action. Each batch uses the
same ``hh01/sys/scancode`` transaction used by List Editor restoration. The
launcher reports successful dispatch; it does not provide a remote process or
Python output channel.

Python project pull path
------------------------

Project pull filters the calculator directory for type 15, downloads each
variable through the normal resource path, validates the CBOR metadata and
Python AppVar lengths, and decodes the embedded source as strict UTF-8. A native
AppVar's 32-bit length describes its meaningful bytes through the required NUL
source terminator; the enclosing calculator variable may add zero through three
NUL bytes for four-byte alignment. The decoder validates the declared boundary,
requires every trailing alignment byte to be zero, and excludes those bytes from
the source. The host-side project layer then maps calculator names to existing
manifest paths or deterministic lowercase filenames and preserves each
variable's RAM or Archive location.

Incremental project push combines the host-side source fingerprint with a fresh
directory read. A configured entry is current only when a type-15 directory
entry has the same calculator name and requested RAM/Archive location. A missing
or relocated calculator program is uploaded again even when its local source
fingerprint is unchanged. The PyCharm plugin and CLI share this decision logic.

Picture upload path
-------------------

Desktop raster images are encoded in one of two calculator formats. Native
Image uploads use the calculator's fixed 160×105 little-endian RGB565 type-5
Image payload for ``Image1`` through ``Image9`` and ``Image0``. They use the
exported-sample marker ``0x16`` followed by rows ordered bottom-to-top and
pixels ordered left-to-right. Sources are center-cropped to fill the canvas
without stretching, then progressively downsampled with bicubic interpolation.
Python image AppVar uploads preserve aspect ratio, are quantized to a
configurable RGB565 palette, and encoded as run-length-compressed
``IM8C`` data inside a type-8 Evo AppVar envelope; the converter reduces the
dimensions further when necessary to fit the IM8C format's 16-bit image-length
field.
The IM8C header stores 24-bit little-endian width and height, palette version,
alpha metadata, an 8-bit palette count, RGB565 palette entries, and the RLE
pixel stream. The complete Evo variable file receives its native checksum
before upload. Firmware can reject an image variable class in RAM, so a rejected
RAM transfer is retried in Archive.
The settings live in PyCharm's user-level ``ti84-evo.xml`` rather than a project
manifest. Native ``.8ci2``, ``.8ca2``, and ``.8xv2`` files bypass conversion.

The protocol code is independently implemented in Kotlin. The public
`Evo-Programming/evo_usb_py implementation
<https://github.com/Evo-Programming/evo_usb_py>`_ was used as a protocol
reference during development and is not a runtime dependency.
The public `TI-Planet img2calc IM8C encoder
<https://github.com/TI-Planet/img2calc>`_ was used as the image-format reference.
The `tivars_lib_cpp Evo background format notes
<https://github.com/adriweb/tivars_lib_cpp/blob/evo/evo-doc/8ca2-background-image.md>`_
document the native image marker, dimensions, and bottom-up scanline order.
