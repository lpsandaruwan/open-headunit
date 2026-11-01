package com.andrerinas.openheadunit.connection.usb

import com.andrerinas.openheadunit.connection.usb.UsbDeviceIdentityPolicy.Interface

/**
 * Which interface of an accessory-mode device carries Android Auto.
 *
 * A plain AOAP device (0x2D00) has one interface, and a phone with ADB adds a second after it, so
 * "interface 0" used to be right. A device that also exposes a network function (the AI box adds
 * one beside the accessory so Link can use the same cable) lists that first, and Android counts
 * every alternate setting of a network data interface as an interface of its own. The accessory
 * can then sit at any index, which is why the choice is made from the descriptors and not by
 * position. Both USB transports ask here so they cannot drift apart.
 */
object UsbInterfaceSelectionPolicy {

    /**
     * The index into the device's interface list to claim, in the order [UsbDevice.getInterface]
     * returns them. Falls back to 0, what both transports always did, when nothing identifies the
     * accessory, so a device that worked before still works.
     */
    fun selectAccessory(interfaces: List<Interface>): Int {
        // The accessory gadget names itself, and that is the only unambiguous signal.
        interfaces.indexOfFirst {
            it.name == UsbDeviceIdentityPolicy.NAME_ACCESSORY && hasBulkPair(it)
        }.let { if (it >= 0) return it }

        // Unnamed (the name needs API 21, and some stacks leave it empty): the vendor triple with a
        // bulk pair, skipping the MTP function a phone in file transfer shows with the same triple.
        interfaces.indexOfFirst {
            isVendorClass(it) && hasBulkPair(it) && it.name != UsbDeviceIdentityPolicy.NAME_MTP
        }.let { if (it >= 0) return it }

        // Anything else that can carry bytes, but never a network function or ADB.
        interfaces.indexOfFirst { hasBulkPair(it) && !isNetwork(it) && !isAdb(it) }
            .let { if (it >= 0) return it }

        return 0
    }

    private fun hasBulkPair(iface: Interface) = iface.hasBulkIn && iface.hasBulkOut

    private fun isVendorClass(iface: Interface) =
        iface.ifaceClass == 0xFF && iface.subclass == 0xFF && iface.protocol == 0x00

    private fun isAdb(iface: Interface) =
        iface.ifaceClass == 0xFF && iface.subclass == 0x42 && iface.protocol == 0x01

    /** CDC communications (0x02) and CDC data (0x0A): both halves of a network or serial function. */
    private fun isNetwork(iface: Interface) = iface.ifaceClass == 0x02 || iface.ifaceClass == 0x0A
}
