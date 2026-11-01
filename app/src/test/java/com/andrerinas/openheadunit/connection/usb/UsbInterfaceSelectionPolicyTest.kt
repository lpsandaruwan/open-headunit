package com.andrerinas.openheadunit.connection.usb

import com.andrerinas.openheadunit.connection.usb.UsbDeviceIdentityPolicy.Interface
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which interface Android Auto is claimed on. Both USB transports ask this. */
class UsbInterfaceSelectionPolicyTest {

    private val accessoryName = UsbDeviceIdentityPolicy.NAME_ACCESSORY

    private fun accessory(name: String? = accessoryName) =
        Interface(0xFF, 0xFF, 0x00, name, hasBulkIn = true, hasBulkOut = true)

    private fun adb() = Interface(0xFF, 0x42, 0x01, "ADB Interface", hasBulkIn = true, hasBulkOut = true)
    private fun mtp() = Interface(0xFF, 0xFF, 0x00, "MTP", hasBulkIn = true, hasBulkOut = true)

    /** CDC NCM: a control interface, then a data interface whose alt 0 has no endpoints. */
    private fun ncmControl() = Interface(0x02, 0x0D, 0x00)
    private fun ncmDataIdle() = Interface(0x0A, 0x00, 0x01)
    private fun ncmDataActive() = Interface(0x0A, 0x00, 0x01, hasBulkIn = true, hasBulkOut = true)

    /** What AOAP gave on 0x2D00 for as long as this code has existed. */
    @Test
    fun `the plain accessory is interface zero as before`() {
        assertEquals(0, UsbInterfaceSelectionPolicy.selectAccessory(listOf(accessory())))
    }

    @Test
    fun `accessory with adb behind it stays interface zero`() {
        assertEquals(0, UsbInterfaceSelectionPolicy.selectAccessory(listOf(accessory(), adb())))
    }

    /**
     * The AI box with its network function: the Desktop Head Unit logged "accessory ifnum: 2",
     * and Android lists the network data interface's two alternate settings separately, so the
     * accessory is the fourth entry. Interface 0 here is the network control interface.
     */
    @Test
    fun `an accessory behind a network function is found by its descriptors`() {
        val interfaces = listOf(ncmControl(), ncmDataIdle(), ncmDataActive(), accessory())

        assertEquals(3, UsbInterfaceSelectionPolicy.selectAccessory(interfaces))
    }

    @Test
    fun `the network function can come after the accessory too`() {
        val interfaces = listOf(accessory(), ncmControl(), ncmDataIdle(), ncmDataActive())

        assertEquals(0, UsbInterfaceSelectionPolicy.selectAccessory(interfaces))
    }

    @Test
    fun `the name beats an earlier vendor interface that merely looks the same`() {
        val interfaces = listOf(accessory(name = null), accessory())

        assertEquals(1, UsbInterfaceSelectionPolicy.selectAccessory(interfaces))
    }

    /** The name needs API 21 and some stacks leave it empty. */
    @Test
    fun `an unnamed accessory is still found by the vendor triple`() {
        val interfaces = listOf(ncmControl(), ncmDataIdle(), ncmDataActive(), accessory(name = null))

        assertEquals(3, UsbInterfaceSelectionPolicy.selectAccessory(interfaces))
    }

    @Test
    fun `a file transfer interface is not preferred over an unnamed accessory`() {
        val interfaces = listOf(mtp(), accessory(name = null))

        assertEquals(1, UsbInterfaceSelectionPolicy.selectAccessory(interfaces))
    }

    @Test
    fun `a network data interface is never chosen when something else carries bytes`() {
        val other = Interface(0xFE, 0x01, 0x01, hasBulkIn = true, hasBulkOut = true)

        assertEquals(1, UsbInterfaceSelectionPolicy.selectAccessory(listOf(ncmDataActive(), other)))
    }

    @Test
    fun `adb is never chosen over the accessory even when it is listed first`() {
        assertEquals(1, UsbInterfaceSelectionPolicy.selectAccessory(listOf(adb(), accessory())))
    }

    /** Nothing identifies an accessory: do what both transports always did. */
    @Test
    fun `the old answer is the fallback`() {
        assertEquals(0, UsbInterfaceSelectionPolicy.selectAccessory(emptyList()))
        assertEquals(0, UsbInterfaceSelectionPolicy.selectAccessory(listOf(ncmControl(), ncmDataIdle())))
        assertEquals(0, UsbInterfaceSelectionPolicy.selectAccessory(listOf(adb())))
    }

    @Test
    fun `a device the identity policy accepts is never left without an interface`() {
        // The composite the box presents is accepted outright by its accessory PID.
        assertEquals(
            true,
            UsbDeviceIdentityPolicy.evaluate(
                UsbDeviceIdentityPolicy.Device(
                    vendorId = 0x18D1, productId = 0x2D00, deviceClass = 0x00,
                    interfaces = listOf(ncmControl(), ncmDataIdle(), ncmDataActive(), accessory()),
                )
            ).accepted
        )
    }
}
