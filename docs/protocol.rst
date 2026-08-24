Protocol overview
=================

The implementation is split into transport, transaction, framing, resource,
and Python-upload layers:

.. code-block:: text

   PyCharm tool window
         |
   EvoDeviceService
         |
   EvoLink / EvoTransactionEngine
         |
   EvoFrameCodec / EvoResourceCodec / EvoPythonPayload
         |
   EvoSerialTransport

Read path
---------

The read-only resource path uses the observed ``S → F → A → D → Z → B``
transaction ladder. It supports resource requests such as
``hh01/get/hh01/sys/attributes`` and ``hh01/get/hh01/sys/screen``. Screen data is
decoded from the Evo run encoding and converted from little-endian RGB565 to
a Java image. The file browser reads
``hh01/get/hh01/inf/res?name=directory&gotohome=1`` and decodes the returned
CBOR entries, including Evo tokenized variable names and memory locations.
Directory transfers use a zero length as an unknown-size sentinel and apply
Kermit control quoting and repeat encoding across their D frames; the reader
decodes the complete wire stream before parsing CBOR.

Python upload path
------------------

Python source is packaged as a type-15 Evo Python AppVar and wrapped in the
CBOR variable-transfer representation expected by the calculator. Kermit
negotiates packet sizes and transfers the payload through the Evo variable
endpoint. The plugin does not treat the calculator as a normal desktop
filesystem and does not upload the bundled editor stubs.

The protocol code is independently implemented in Kotlin. Public protocol
references used during reverse engineering are documented in the source and
should not be mistaken for a runtime dependency.
