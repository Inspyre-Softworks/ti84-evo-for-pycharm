Protocol overview
=================

The implementation is split into transport, transaction, framing, resource,
and Python-upload layers:

.. graphviz::

   digraph architecture {
       rankdir=TB;
       graph [bgcolor="transparent"];
       node [
           shape=box,
           style="rounded",
           fontname="sans-serif",
           fontsize=10
       ];
       edge [
           fontname="sans-serif",
           fontsize=9
       ];

       tool [label="PyCharm tool window"];
       service [label="EvoDeviceService"];

       evolink [label="EvoLink"];
       transaction [label="EvoTransactionEngine"];
       codec [label="KermitPacketCodec"];
       transport [label="EvoSerialTransport"];

       python [label="EvoPythonTransfer"];
       payload [label="EvoPythonPayload"];

       tool -> service;

       service -> evolink [label="resource reads"];
       evolink -> transaction;
       transaction -> codec [
           label="resource compatibility"
       ];
       codec -> transport;

       service -> python [label="Python uploads"];
       python -> payload;
       payload -> codec;
       codec -> transport;
   }

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
``hh01/get/hh01/sys/attributes`` and ``hh01/get/hh01/sys/screen``. Screen data is
decoded from the Evo run encoding and converted from little-endian
:term:`RGB565` to a Java image. The file browser reads
``hh01/get/hh01/inf/res?name=directory&gotohome=1`` and decodes the returned
:term:`CBOR` entries, including Evo :term:`tokenized name` values and memory
locations. Directory transfers use a zero length as an unknown-size sentinel
and apply Kermit :term:`control quoting` and :term:`repeat encoding` across
their :term:`D frame` payloads; the reader decodes the complete wire stream
before parsing CBOR.

Individual variables are downloaded through
``hh01/get/hh01/xfr/var?name=...&type=...``. The complete CBOR envelope can be
inspected and exported with the Evo checksum restored. Native number, list, and
matrix payloads are decoded into editable real, fraction, complex, and tabular
text. Creation/replacement uses the firmware's type-60 ASCII import envelope, so
the calculator remains responsible for encoding edited values back to native form.

Saving an existing RAM variable to Archive downloads that envelope, restores
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
``hh01/get/hh01/inf/res?name=directory&gotohome=1``, and searches for the same
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

Desktop raster images are resized with their aspect ratio intact, quantized to
a configurable palette, converted to RGB565, and encoded as run-length-compressed
``IM8C`` data inside a type-8 Evo AppVar envelope. The converter reduces the
dimensions further when necessary to fit the format's 16-bit image-length field.
The settings live in PyCharm's user-level ``ti84-evo.xml`` rather than a project
manifest. Native ``.8ci2``, ``.8ca2``, and ``.8xv2`` files bypass conversion.

The protocol code is independently implemented in Kotlin. The public
`Evo-Programming/evo_usb_py implementation
<https://github.com/Evo-Programming/evo_usb_py>`_ was used as a protocol
reference during development and is not a runtime dependency.
