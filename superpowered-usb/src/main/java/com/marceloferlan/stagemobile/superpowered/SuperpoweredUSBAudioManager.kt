package com.marceloferlan.stagemobile.superpowered

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Gerenciador de áudio USB via Superpowered.
 * 
 * Essa classe roda no módulo :superpowered-usb que usa c++_static,
 * isolado do módulo :app que usa c++_shared (Oboe).
 */
class SuperpoweredUSBAudioManager(
    private val context: Context,
    private val handler: SuperpoweredUSBAudioHandler? = null
) {
    companion object {
        private const val TAG = "SuperpoweredUSB"
        private const val ACTION_USB_PERMISSION = "com.marceloferlan.stagemobile.USB_PERMISSION"

        init {
            System.loadLibrary("spbridge")
            Log.i(TAG, "libspbridge.so loaded (c++_static, isolated)")
        }
    }

    private val permissionIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        },
        PendingIntent.FLAG_MUTABLE
    )

    private var isInitialized = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            Log.i(TAG, "BroadcastReceiver: action=${intent.action}")
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null) {
                        Log.i(TAG, "USB ATTACHED: ${device.productName} (id=${device.deviceId}, vendor=${device.vendorId})")
                        val manager = ctx.getSystemService(Context.USB_SERVICE) as? UsbManager
                        if (manager != null) {
                            if (manager.hasPermission(device)) {
                                Log.i(TAG, "Permission already granted for ${device.productName}")
                                addUSBDevice(device)
                            } else {
                                Log.i(TAG, "Requesting USB permission for ${device.productName}...")
                                manager.requestPermission(device, permissionIntent)
                            }
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (device != null) {
                        val id = device.deviceId
                        nativeOnDisconnect(id)
                        handler?.onUSBDeviceDetached(id)
                        Log.i(TAG, "USB DETACHED: ${device.productName} (id=$id)")
                    }
                }
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    Log.i(TAG, "USB PERMISSION result: granted=$granted, device=${device?.productName}")
                    if (device != null && granted) {
                        addUSBDevice(device)
                    }
                }
            }
        }
    }

    fun initialize(licenseKey: String = "ExampleLicenseKey-WillExpire-OnNextUpdate") {
        if (isInitialized) return

        nativeInitialize(licenseKey)

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        isInitialized = true
        Log.i(TAG, "Superpowered USB Audio Manager initialized")
    }

    fun checkConnectedDevices() {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val devices = manager.deviceList
        Log.i(TAG, "Scanning ${devices.size} USB device(s) already connected...")
        for (device in devices.values) {
            Log.i(TAG, "  Found: ${device.productName} (id=${device.deviceId}, class=${device.deviceClass})")
            addUSBDevice(device)
        }
    }

    fun isActive(): Boolean = nativeIsActive()
    fun getSampleRate(): Int = nativeGetSampleRate()

    private fun addUSBDevice(device: UsbDevice) {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return

        // Verificação Robusta de Hardware: Ignorar aparelhos Puros MIDI.
        // O Android esconde Endpoints Isócronos nos "AltSettings > 0", então nossa checagem anterior ocultava Interfaces Reais de Áudio!
        // Nova Heurística: 
        // 1. Teclados MIDI só possuem Interface Subclasses 1 (Control) e 3 (MIDI Streaming).
        // 2. Interfaces de Audio possuem SubClass 2 (Audio Streaming) OU são Vendor-Specific (Class 255 - Focusrite/Presonus).
        var isPureMidi = true
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            // Se encontramos Subclasse de Streaming de Áudio padrão (2)
            if (intf.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_AUDIO && 
                intf.interfaceSubclass == 2) {
                isPureMidi = false
                break
            }
            // Interfaces e Placas Profissionais geralmente mascaram como Vendor Specific (255)
            if (intf.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_VENDOR_SPEC) {
                isPureMidi = false
                break
            }
        }

        if (isPureMidi) {
            Log.i(TAG, "Device ${device.productName} identified as PURE MIDI or Generic. Bypassing Superpowered to preserve System MIDI.")
            return
        }

        if (!manager.hasPermission(device)) {
            manager.requestPermission(device, permissionIntent)
            return
        }

        val connection = manager.openDevice(device) ?: run {
            Log.e(TAG, "Failed to open USB device: ${device.deviceName}")
            return
        }

        val id = device.deviceId
        val result = nativeOnConnect(id, connection.fileDescriptor, connection.rawDescriptors)

        Log.i(TAG, "USB onConnect result=$result (0=Audio+MIDI, 1=Audio, 2=MIDI, 3=None) for ${device.productName}")

        when (result) {
            0 -> {
                handler?.onUSBAudioDeviceAttached(id)
                // O Superpowered assumiu (fd locked). MidiManager do Android pode não conseguir ver a porta MIDI se fizer parte da mesma interface.
            }
            1 -> {
                handler?.onUSBAudioDeviceAttached(id)
            }
            2, 3 -> {
                // Não é interface de áudio. Devemos liberar a raw connection IMEDIATAMENTE
                // para que o Android MidiManager possa abrir sem erro de "Device already open/busy".
                nativeOnDisconnect(id)
                connection.close()
                if (result == 2) handler?.onUSBMIDIDeviceAttached(id)
            }
        }
    }

    // --- Native methods (implementadas em sp_bridge.cpp / libspbridge.so) ---
    private external fun nativeInitialize(licenseKey: String)
    private external fun nativeOnConnect(deviceID: Int, fd: Int, rawDescriptor: ByteArray): Int
    private external fun nativeOnDisconnect(deviceID: Int)
    private external fun nativeIsActive(): Boolean
    private external fun nativeGetSampleRate(): Int
}
