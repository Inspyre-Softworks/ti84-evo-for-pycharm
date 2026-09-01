Protocol overview
=================

The implementation is split into transport, transaction, framing, resource,
and Python-upload layers:

.. code-block:: text

   PyCharm tool window
       |
   EvoDeviceService
       +-- resource reads
       |   +-- EvoLink
       |       +-- EvoTransactionEngine
       |           +-- EvoFrameCodec / EvoResourceCodec
       |               +-- EvoSerialTransport
       |
       +-- Python uploads
           +-- EvoPythonTransfer
               +-- EvoPythonPayload / KermitPacketCodec
                   +-- EvoSerialTransport

Transport setup
---------------

The calculator's USB CDC serial port is opened at 115200 baud with 8 data
bits, no parity, one stop bit, and flow control disabled. Both DTR and RTS are
asserted after opening the port, matching the control-line state used by a
working calculator directory session.

Read path
---------

The read-only :term:`resource` path uses the observed
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

Selected calculator files are deleted through
``hh01/del/var?name=...&type=...``. Tokenized names are percent-encoded from
their calculator representation and the directory entry supplies the numeric
variable type. Each selected file uses its own complete transaction so a
partial failure can be reported accurately.

Individual variables are downloaded through
``hh01/get/hh01/xfr/var?name=...&type=...``. The complete CBOR envelope can be
inspected and exported with the Evo checksum restored. Native number, list, and
matrix payloads are decoded into editable real, fraction, complex, and tabular
text. Creation/replacement uses the firmware's type-60 ASCII import envelope, so
the calculator remains responsible for encoding edited values back to native form.

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
