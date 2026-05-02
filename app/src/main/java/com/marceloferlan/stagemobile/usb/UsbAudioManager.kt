package com.marceloferlan.stagemobile.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * UsbAudioManager — Gerenciador de hotplug USB e bridge JNI para o Driver USB Nativo.
 *
 * Responsabilidades:
 *  - Registrar BroadcastReceiver para ação USB_DEVICE_ATTACHED / DETACHED
 *  - Solicitar permissão de acesso ao device Android UsbManager
 *  - Passar o file descriptor USB para o driver nativo via JNI
 *  - Expor callbacks para o MixerViewModel monitorar o estado
 *
 * Uso:
 *   val usbManager = UsbAudioManager(context)
 *   usbManager.onDriverActive = { active -> updateUI(active) }
 *   usbManager.register()
 *   // ... later ...
 *   usbManager.unregister()
 */
class UsbAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbAudioManager"

        // Permissão USB — action usada no PendingIntent de request
        private const val USB_PERMISSION = "com.marceloferlan.stagemobile.USB_PERMISSION"

        // Carrega a lib nativa (já carregada pelo FluidSynthEngine, declare aqui como safety)
        init {
            try {
                System.loadLibrary("synthmodule")
            } catch (e: UnsatisfiedLinkError) {
                // Já carregada — ignorar
            }
        }
    }

    // ── Callbacks para o ViewModel ────────────────────────────────────────────
    /** Chamado quando o Driver USB é ativado (true) ou desativado (false). */
    var onDriverStateChanged: ((active: Boolean) -> Unit)? = null

    /** Chamado com o file descriptor logo antes de chamar nativeUsbStart. */
    var onDeviceConnected: ((device: UsbDevice) -> Unit)? = null

    // ── Estado interno ────────────────────────────────────────────────────────
    private var registrado = false
    private var deviceConectado: UsbDevice? = null
    // CRÍTICO: manter a UsbDeviceConnection viva enquanto o driver USB estiver
    // ativo. Se o GC coletar este objeto, ele fecha o fd e causa
    // LIBUSB_TRANSFER_NO_DEVICE em todas as transferências pendentes.
    private var activeConnection: android.hardware.usb.UsbDeviceConnection? = null
    
    /** Taxa de amostragem configurada no motor de áudio. */
    var currentSampleRate: Int = 48000

    // ─── BroadcastReceiver USB ────────────────────────────────────────────────
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return

            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.i(TAG, "USB ATTACHED: ${device.deviceName} VID=${device.vendorId.toString(16)} PID=${device.productId.toString(16)}")
                    tentarConectar(device)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.i(TAG, "USB DETACHED: ${device.deviceName}")
                    if (device.deviceName == deviceConectado?.deviceName) {
                        pararDriver()
                    }
                }
                USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        Log.i(TAG, "USB permission GRANTED para ${device.deviceName}")
                        abrirDriverComPermissao(device, currentSampleRate)
                    } else {
                        Log.w(TAG, "USB permission DENIED para ${device.deviceName}")
                    }
                }
            }
        }
    }

    // ─── Registro / desregistro ───────────────────────────────────────────────

    fun register() {
        if (registrado) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(USB_PERMISSION)
        }
        context.registerReceiver(usbReceiver, filter)
        registrado = true
        Log.i(TAG, "UsbAudioManager registrado")

        // Verificar devices já conectados no boot
        verificarDevicesExistentes()
    }

    fun unregister() {
        if (!registrado) return
        pararDriver()
        try { context.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
        registrado = false
        Log.i(TAG, "UsbAudioManager desregistrado")
    }

    // ─── Lógica de conexão ────────────────────────────────────────────────────

    fun scanDevices() {
        Log.i(TAG, "Forçando scan de dispositivos USB conectados")
        verificarDevicesExistentes()
    }

    private fun verificarDevicesExistentes() {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        for (device in manager.deviceList.values) {
            Log.i(TAG, "Device USB já conectado: ${device.deviceName}")
            tentarConectar(device)
        }
    }

    private fun tentarConectar(device: UsbDevice) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        if (manager.hasPermission(device)) {
            Log.i(TAG, "Permissão já concedida para ${device.deviceName}")
            abrirDriverComPermissao(device, currentSampleRate)
        } else {
            Log.i(TAG, "Solicitando permissão USB para ${device.deviceName}")
            val permissionIntent = android.app.PendingIntent.getBroadcast(
                context, 0,
                Intent(USB_PERMISSION).apply { setPackage(context.packageName) },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_MUTABLE
            )
            manager.requestPermission(device, permissionIntent)
        }
    }

    private fun abrirDriverComPermissao(device: UsbDevice, sampleRate: Int) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val connection = manager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Não foi possível abrir a conexão USB para ${device.deviceName}")
            return
        }

        // CRÍTICO: armazenar ANTES de passar o fd para o driver nativo.
        // O GC não pode coletar este objeto enquanto o libusb tiver transfers
        // pendentes — caso contrário: LIBUSB_TRANSFER_NO_DEVICE após ~1s.
        activeConnection?.close()
        activeConnection = connection

        val fd = connection.fileDescriptor
        Log.i(TAG, "Abrindo Driver USB Nativo: device=${device.deviceName} fd=$fd")

        // CRÍTICO: Claim das interfaces pelo Android antes de passar ao libusb!
        // Sem isso, ao reiniciar o driver, o uso de FileDescriptor cru pode ser rejeitado
        // pelo kernel originando 'errno=2' e 'status=5' (ENOENT) em submit_URB, pois a API
        // do Android não registrou formalmente nossa posse.
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            // Class 1 = USB_CLASS_AUDIO
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                val success = connection.claimInterface(iface, true)
                Log.i(TAG, "Kotlin claimInterface(id=${iface.id}, class=Audio): $success")
            }
        }

        onDeviceConnected?.invoke(device)

        // Chamar o driver nativo — Fase 2+3: probe + claim + ISO transfers
        val ok = nativeUsbStart(fd, sampleRate = sampleRate, channels = 2)

        if (ok) {
            deviceConectado = device
            onDriverStateChanged?.invoke(true)
            Log.i(TAG, "Driver USB Nativo ATIVO — ${device.deviceName}")
        } else {
            // Falhou: liberar a conexão imediatamente
            activeConnection?.close()
            activeConnection = null
            Log.w(TAG, "Driver USB: falha ao iniciar streaming em ${device.deviceName}. Veja UsbAudioDriver no logcat.")
        }
    }

    private fun pararDriver() {
        nativeUsbStop()
        onDriverStateChanged?.invoke(false)
        Log.i(TAG, "Driver USB Nativo PARADO")

        // Liberar a conexão USB APÓS o driver parar
        // (libusb já fechou seus handles internos via stop())
        activeConnection?.close()
        activeConnection = null
        deviceConectado = null
    }

    /** Para o streaming USB mantendo o receiver e a conexão vivos. */
    fun stopStreaming() {
        Log.i(TAG, "Prividing explicit nativeUsbStop request...")
        nativeUsbStop()
        onDriverStateChanged?.invoke(false)
    }

    // ─── JNI ─────────────────────────────────────────────────────────────────

    /**
     * Abre o device USB e detecta endpoint de áudio.
     * Fase 1: probe + log de descritores.
     * Fase 3: inicia isochronous OUT transfers.
     */
    external fun nativeUsbStart(fd: Int, sampleRate: Int, channels: Int): Boolean

    /** Para o driver e libera recursos libusb. */
    external fun nativeUsbStop()

    /** Retorna true se o driver USB Nativo está ativo. */
    external fun nativeUsbIsActive(): Boolean
}
