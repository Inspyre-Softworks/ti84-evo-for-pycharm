Glossary
========

Glossary table of contents
--------------------------

**A** — :term:`acceptance test`, :term:`AppVar`, :term:`Archive`

**B–C** — :term:`block check`, :term:`CBOR`, :term:`CDC`, :term:`checksum`,
:term:`control quoting`

**D–F** — :term:`D frame`, :term:`dynamic resource`, :term:`endpoint`,
:term:`extended frame`, :term:`frame`, :term:`framebuffer`

**H–P** — :term:`hh01`, :term:`Kermit`, :term:`long packet`, :term:`PID`

**R** — :term:`RAM`, :term:`repeat encoding`, :term:`resource`, :term:`RGB565`

**S–V** — :term:`short frame`, :term:`synthetic library`, :term:`tokenized name`,
:term:`transaction ladder`, :term:`type stub`, :term:`VID`

.. glossary::
   :sorted:

   acceptance test
      A test performed with a physical TI-84 Evo and USB connection. Host-side
      unit tests can validate codecs and transaction logic, but they do not
      prove that a hardware operation works with the calculator.

   AppVar
      The calculator variable container used by the Python upload format. The
      plugin packages UTF-8 source in the Evo representation and transfers it
      as variable type 15 (Python Program).

   Archive
      Nonvolatile calculator storage. Directory entries report whether a
      variable is in Archive or :term:`RAM`. Single-file uploads and project
      entries can target Archive.

   block check
      The Kermit integrity value attached to a packet. During send-init, the
      calculator and plugin select a one-, two-, or three-character block-check
      type.

   CBOR
      Concise Binary Object Representation, a compact binary data format. Evo
      resources such as attributes, the screen, and the directory use CBOR
      maps and arrays; Python uploads also use a CBOR envelope.

   CDC
      USB Communications Device Class. The TI-84 Evo exposes a CDC interface
      that the operating system presents as a serial-style port, such as a
      Windows COM port.

   checksum
      A value calculated from frame bytes so corruption can be detected. Evo
      resource frames use their own checksum; Kermit upload packets use a
      negotiated :term:`block check`.

   control quoting
      Kermit encoding that represents control bytes using printable byte
      sequences so packet delimiters and other reserved values can be carried
      safely as data.

   D frame
      The data-carrying step of an Evo transaction. A transfer can contain one
      or more D frames between its attributes and end-of-file steps.

   dynamic resource
      A resource whose response announces zero as an unknown-length sentinel
      even though its D frames contain data. The calculator directory is read
      this way.

   endpoint
      A URI-like calculator request descriptor. For example, Python programs
      are sent through ``hh01/xfr/var`` with name, type, memory-target, and
      overwrite-policy parameters.

   extended frame
      The Evo frame form used when a packet is too large for the short form. It
      stores the frame span as two printable base-95 digits and includes an
      auxiliary byte.

   frame
      A unit exchanged over the Evo serial protocol. Resource frames include a
      start marker, length and sequence fields, a command, an optional payload,
      an integrity check, and a terminator.

   framebuffer
      The raw array of pixel values returned by the screen resource. The plugin
      validates its dimensions and converts it into an image.

   hh01
      The calculator namespace used at the start of Evo resource and variable-
      transfer descriptors, such as ``hh01/get/hh01/sys/screen``.

   Kermit
      The packet-transfer protocol used by the Evo transaction ladder. The
      plugin uses it for calculator resource exchanges and Python variable
      uploads.

   long packet
      Kermit's extended packet form. It carries a larger data area than a
      normal packet and uses a two-character base-95 length field.

   PID
      USB product identifier. The plugin recognizes the TI-84 Evo product ID
      ``E018`` together with vendor ID :term:`VID` ``0451``.

   RAM
      Volatile calculator storage. It is the default upload target; Archive can
      instead be selected for a single file or configured per project entry.

   repeat encoding
      Kermit compression that replaces a run of repeated bytes with a repeat
      marker, count, and encoded byte.

   resource
      Calculator data addressed by a descriptor and returned through an Evo
      transaction. Examples include attributes, the screen, and the variable
      directory.

   RGB565
      A 16-bit pixel format with five red bits, six green bits, and five blue
      bits. Evo screen pixels are returned in little-endian RGB565 order.

   short frame
      The compact Evo frame form whose length fits in one printable length
      byte. Larger packets require an :term:`extended frame`.

   synthetic library
      An editor-only PyCharm library created by the plugin. It exposes the
      bundled TI Python type stubs without installing a desktop package or
      uploading those files to the calculator.

   tokenized name
      A calculator variable name represented with Evo token values rather than
      ordinary desktop filename bytes. The directory decoder converts these
      values to readable names, and the uploader performs the reverse mapping.

   transaction ladder
      The ordered ``S → F → A → D… → Z → B`` Kermit exchange: send-init, file
      descriptor, attributes, one or more data packets, end of file, and end of
      transaction.

   type stub
      A ``.pyi`` declaration file that describes Python modules, functions,
      parameters, and return types to the editor without providing a runtime
      implementation.

   VID
      USB vendor identifier. Texas Instruments uses VID ``0451``; the plugin
      combines it with the Evo :term:`PID` to find the calculator's CDC port.
