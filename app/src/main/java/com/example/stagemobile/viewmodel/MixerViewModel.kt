package com.example.stagemobile.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.stagemobile.audio.engine.AudioEngine
import com.example.stagemobile.audio.engine.DummyAudioEngine
import com.example.stagemobile.audio.engine.FluidSynthEngine
import com.example.stagemobile.audio.AudioDeviceState
import com.example.stagemobile.domain.model.InstrumentChannel
import com.example.stagemobile.utils.SystemResourceMonitor
import com.example.stagemobile.midi.MidiConnectionManager
import com.example.stagemobile.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class MixerViewModel : ViewModel() {

    companion object {
        private const val TAG = "MixerViewModel"
    }

    private var audioEngine: AudioEngine = DummyAudioEngine()
    private var midiManager: MidiConnectionManager? = null
    private var deviceAudioManager: com.example.stagemobile.audio.DeviceAudioManager? = null
    private var settingsRepo: SettingsRepository? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // --- State Flows ---

    private val _channels = MutableStateFlow(
        listOf(
            InstrumentChannel(0, "Instrumento 01", program = 0),
            InstrumentChannel(1, "Instrumento 02", program = 0),
            InstrumentChannel(2, "Instrumento 03", program = 0),
            InstrumentChannel(3, "Instrumento 04", program = 0),
            InstrumentChannel(4, "Instrumento 05", program = 0),
            InstrumentChannel(5, "Instrumento 06", program = 0),
            InstrumentChannel(6, "Instrumento 07", program = 0)
        )
    )
    val channels: StateFlow<List<InstrumentChannel>> = _channels

    private val _midiDeviceConnected = MutableStateFlow(false)
    val midiDeviceConnected: StateFlow<Boolean> = _midiDeviceConnected

    private val _midiDeviceName = MutableStateFlow("")
    val midiDeviceName: StateFlow<String> = _midiDeviceName

    private val _availableMidiDevices = MutableStateFlow<List<com.example.stagemobile.midi.MidiDeviceState>>(emptyList())
    val availableMidiDevices: StateFlow<List<com.example.stagemobile.midi.MidiDeviceState>> = _availableMidiDevices

    private val _activeMidiDevices = MutableStateFlow<Set<String>>(emptySet())
    val activeMidiDevices: StateFlow<Set<String>> = _activeMidiDevices

    private val _availableAudioDevices = MutableStateFlow<List<AudioDeviceState>>(listOf(AudioDeviceState(-1, "Auto (Padrão do Sistema)")))
    val availableAudioDevices: StateFlow<List<AudioDeviceState>> = _availableAudioDevices

    private val _selectedAudioDeviceId = MutableStateFlow(-1)
    val selectedAudioDeviceId: StateFlow<Int> = _selectedAudioDeviceId

    private val _sf2Loaded = MutableStateFlow(false)
    val sf2Loaded: StateFlow<Boolean> = _sf2Loaded

    private val _sf2Name = MutableStateFlow("")
    val sf2Name: StateFlow<String> = _sf2Name

    private val _ramUsageMb = MutableStateFlow(0)
    val ramUsageMb: StateFlow<Int> = _ramUsageMb

    private val _cpuUsagePercent = MutableStateFlow(0f)
    val cpuUsagePercent: StateFlow<Float> = _cpuUsagePercent

    // --- Settings States ---
    private val _bufferSize = MutableStateFlow(64)
    val bufferSize: StateFlow<Int> = _bufferSize

    private val _sampleRate = MutableStateFlow(48000)
    val sampleRate: StateFlow<Int> = _sampleRate

    private val _midiChannel = MutableStateFlow(0) // 0 = All
    val midiChannel: StateFlow<Int> = _midiChannel

    // --- Master States ---
    private val _isMasterVisible = MutableStateFlow(false)
    val isMasterVisible: StateFlow<Boolean> = _isMasterVisible

    private val _masterVolume = MutableStateFlow(0.8f) // Default 80%
    val masterVolume: StateFlow<Float> = _masterVolume

    private val _masterLevel = MutableStateFlow(0f)
    val masterLevel: StateFlow<Float> = _masterLevel

    private val activeNotesCount = ConcurrentHashMap<Int, Int>()
    private val channelInternalLevels = ConcurrentHashMap<Int, Float>()

    private var nextId = 7

    init {
        startVuMeterUpdate()
    }

    // --- Audio Engine ---
    
    fun initSettings(context: Context) {
        if (settingsRepo != null) return
        val repo = SettingsRepository(context)
        settingsRepo = repo
        _bufferSize.value = repo.bufferSize
        _sampleRate.value = repo.sampleRate
        _midiChannel.value = repo.midiChannel
        _isMasterVisible.value = repo.showMaster
    }

    fun loadSoundFontForChannel(context: Context, channelId: Int, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open URI: $uri")

                // Get the real filename from ContentResolver
                val sf2FileName = getDisplayName(context, uri)
                    ?: uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.substringAfterLast(':')
                    ?: "soundfont_ch$channelId.sf2"
                val outputFile = File(context.filesDir, "sf2_ch${channelId}_$sf2FileName")

                inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val sfId = audioEngine.loadSoundFont(outputFile.absolutePath)
                if (sfId >= 0) {
                    // When loading a new SF2, always start with preset 0 (first available)
                    // The hardcoded programs (88, 32 etc) likely don't exist in the user's SF2
                    val loadBank = 0
                    val loadProgram = 0

                    // Update channel with SF2 name, sfId, and reset program
                    _channels.value = _channels.value.map {
                        if (it.id == channelId) it.copy(
                            soundFont = sf2FileName,
                            sfId = sfId,
                            bank = loadBank,
                            program = loadProgram
                        )
                        else it
                    }
                    _sf2Loaded.value = true
                    _sf2Name.value = sf2FileName

                    // Bind this specific SF2 to this MIDI channel using preset 0
                    audioEngine.programSelect(channelId, sfId, loadBank, loadProgram)

                    Log.i(TAG, "SF2 loaded for channel $channelId: $sf2FileName (sfId=$sfId, bank=$loadBank, prog=$loadProgram)")
                } else {
                    Log.e(TAG, "Failed to load SF2 for channel $channelId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading SF2 for channel $channelId: ${e.message}", e)
            }
        }
    }

    /**
     * Start the system resource monitor loop.
     * Called once when the actual Android Context is available (e.g., in initMidi).
     */
    private fun startResourceMonitor(context: Context) {
        val monitor = SystemResourceMonitor(context)
        scope.launch {
            while (true) {
                _ramUsageMb.value = monitor.getMemoryUsageMb()
                _cpuUsagePercent.value = monitor.getCpuUsagePercent()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // --- MIDI ---

    fun initMidi(context: Context) {
        initSettings(context)
        _activeMidiDevices.value = settingsRepo?.activeMidiDevices ?: emptySet()

        if (deviceAudioManager == null) {
            deviceAudioManager = com.example.stagemobile.audio.DeviceAudioManager(context)
            _selectedAudioDeviceId.value = settingsRepo?.selectedAudioDeviceId ?: -1
            
            scope.launch {
                deviceAudioManager!!.availableAudioDevices.collect { devices ->
                    _availableAudioDevices.value = devices
                }
            }
        }

        // Override with Real audio engine if full context is provided
        if (audioEngine is DummyAudioEngine) {
            audioEngine = FluidSynthEngine()
            val deviceId = _selectedAudioDeviceId.value
            audioEngine.init(_sampleRate.value, _bufferSize.value, deviceId)
            startResourceMonitor(context)
        }

        if (midiManager != null) return

        midiManager = MidiConnectionManager(
            context = context,
            onNoteOn = { deviceName, channel, key, velocity ->
                val activeDevices = settingsRepo?.activeMidiDevices ?: emptySet()
                if (!activeDevices.contains(deviceName)) return@MidiConnectionManager

                if (_midiChannel.value == 0 || _midiChannel.value - 1 == channel) {
                    val armedIds = getArmedChannelIds()
                    armedIds.forEach { chId ->
                        val ch = _channels.value.find { it.id == chId }
                        if (ch != null && key in ch.minNote..ch.maxNote && (ch.midiChannel == -1 || ch.midiChannel == channel)) {
                            // Filter by deviceName
                            if (ch.midiDeviceName == null || ch.midiDeviceName == deviceName) {
                                audioEngine.noteOn(chId, key, velocity)
                                triggerNoteOnVelocity(chId, velocity)
                            }
                        }
                    }
                }
            },
            onNoteOff = { deviceName, channel, key ->
                val activeDevices = settingsRepo?.activeMidiDevices ?: emptySet()
                if (!activeDevices.contains(deviceName)) return@MidiConnectionManager

                if (_midiChannel.value == 0 || _midiChannel.value - 1 == channel) {
                    val armedIds = getArmedChannelIds()
                    armedIds.forEach { chId ->
                        val ch = _channels.value.find { it.id == chId }
                        if (ch != null && (ch.midiChannel == -1 || ch.midiChannel == channel)) {
                            if (ch.midiDeviceName == null || ch.midiDeviceName == deviceName) {
                                audioEngine.noteOff(chId, key)
                                triggerNoteOff(chId)
                            }
                        }
                    }
                }
            },
            onControlChange = { deviceName, channel, controller, value ->
                val activeDevices = settingsRepo?.activeMidiDevices ?: emptySet()
                if (!activeDevices.contains(deviceName)) return@MidiConnectionManager

                if (_midiChannel.value == 0 || _midiChannel.value - 1 == channel) {
                    // CC 7 = Volume
                    if (controller == 7) {
                        val volume = value / 127f
                        updateVolume(channel, volume) // Map CC to Mixer channel (simplified mapping)
                    }
                }
            },
            onProgramChange = { deviceName, channel, program ->
                val activeDevices = settingsRepo?.activeMidiDevices ?: emptySet()
                if (!activeDevices.contains(deviceName)) return@MidiConnectionManager

                if (_midiChannel.value == 0 || _midiChannel.value - 1 == channel) {
                    audioEngine.programChange(channel, 0, program)
                }
            }
        )

        midiManager?.start()
        
        // Track available devices
        scope.launch {
            midiManager?.availableDevices?.collect { devices ->
                _availableMidiDevices.value = devices
            }
        }
    }

    fun toggleActiveMidiDevice(deviceName: String, isActive: Boolean) {
        val current = _activeMidiDevices.value.toMutableSet()
        if (isActive) {
            current.add(deviceName)
        } else {
            current.remove(deviceName)
        }
        _activeMidiDevices.value = current
        settingsRepo?.activeMidiDevices = current
    }

    private fun getArmedChannelIds(): List<Int> {
        return _channels.value.filter { it.isArmed && it.sfId >= 0 }.map { it.id }
    }

    // --- Virtual Keyboard ---

    fun noteOn(midiNote: Int) {
        val armedIds = getArmedChannelIds()
        armedIds.forEach { chId ->
            val ch = _channels.value.find { it.id == chId }
            if (ch != null && midiNote in ch.minNote..ch.maxNote) {
                audioEngine.noteOn(chId, midiNote, 100)
                triggerNoteOnVelocity(chId, 100)
            }
        }
    }

    fun noteOff(midiNote: Int) {
        val armedIds = getArmedChannelIds()
        armedIds.forEach { chId ->
            audioEngine.noteOff(chId, midiNote)
            triggerNoteOff(chId)
        }
    }

    // --- Channel Management ---

    fun addChannel(name: String) {
        if (_channels.value.size >= 16) return
        val newChannel = InstrumentChannel(id = nextId++, name = name)
        _channels.value = _channels.value + newChannel
    }

    fun removeChannel(channelId: Int) {
        _channels.value = _channels.value.filter { it.id != channelId }
        activeNotesCount.remove(channelId)
        channelInternalLevels.remove(channelId)
    }

    fun updateVolume(channelId: Int, newVolume: Float) {
        _channels.value = _channels.value.map {
            if (it.id == channelId) it.copy(volume = newVolume) else it
        }
        applyChannelVolumeToEngine(channelId)
    }

    // --- Master Control ---
    fun toggleMasterVisibility() {
        _isMasterVisible.value = !_isMasterVisible.value
        settingsRepo?.showMaster = _isMasterVisible.value
    }

    fun updateMasterVolume(newVolume: Float) {
        _masterVolume.value = newVolume
        // Apply new master volume scalar to all active channels
        _channels.value.forEach { ch ->
            applyChannelVolumeToEngine(ch.id)
        }
    }

    private fun applyChannelVolumeToEngine(channelId: Int) {
        val channelObj = _channels.value.find { it.id == channelId } ?: return
        val effective = getEffectiveVolume(channelObj)
        // Simulate Master Gain strictly by attenuation on the fader curve
        val finalLinearVolume = effective * _masterVolume.value
        audioEngine.setChannelVolume(channelId, faderToDb(finalLinearVolume))
    }

    fun toggleMute(channelId: Int) {
        _channels.value = _channels.value.map {
            if (it.id == channelId) it.copy(isMuted = !it.isMuted) else it
        }
    }

    fun toggleSolo(channelId: Int) {
        _channels.value = _channels.value.map {
            if (it.id == channelId) it.copy(isSolo = !it.isSolo) else it
        }
    }

    private fun updateChannel(channelId: Int, update: (com.example.stagemobile.domain.model.InstrumentChannel) -> com.example.stagemobile.domain.model.InstrumentChannel) {
        _channels.value = _channels.value.map {
            if (it.id == channelId) update(it) else it
        }
    }

    fun toggleArm(channelId: Int) {
        updateChannel(channelId) { it.copy(isArmed = !it.isArmed) }
    }

    fun updateChannelKeyRange(channelId: Int, minNote: Int, maxNote: Int) {
        if (minNote in 0..maxNote && maxNote in minNote..127) {
            updateChannel(channelId) { it.copy(minNote = minNote, maxNote = maxNote) }
        }
    }

    fun updateChannelMidiChannel(channelId: Int, midiChannel: Int) {
        updateChannel(channelId) { it.copy(midiChannel = midiChannel) }
    }

    fun updateChannelMidiDevice(channelId: Int, deviceName: String?) {
        updateChannel(channelId) { it.copy(midiDeviceName = deviceName) }
    }

    fun removeSoundFont(channelId: Int) {
        // Find sfId before resetting state
        val ch = _channels.value.find { it.id == channelId }
        val sfIdToUnload = ch?.sfId ?: -1

        // All notes off on this channel to stop any lingering sound
        for (key in 0..127) {
            audioEngine.noteOff(channelId, key)
        }
        activeNotesCount[channelId] = 0
        channelInternalLevels[channelId] = 0f

        // Instruct engine to free RAM
        if (sfIdToUnload >= 0) {
            audioEngine.unloadSoundFont(sfIdToUnload)
        }

        // Reset channel state
        _channels.value = _channels.value.map {
            if (it.id == channelId) it.copy(
                soundFont = null,
                sfId = -1,
                isArmed = false,
                program = 0,
                bank = 0
            ) else it
        }
        Log.i(TAG, "SF2 removed from channel $channelId")
    }

    fun getEffectiveVolume(channel: InstrumentChannel): Float {
        val anySoloActive = _channels.value.any { it.isSolo }
        return when {
            channel.isMuted -> 0f
            anySoloActive && !channel.isSolo -> 0f
            else -> channel.volume
        }
    }

    // --- Settings Updates ---
    
    fun updateBufferSize(context: Context, size: Int) {
        if (_bufferSize.value == size) return
        _bufferSize.value = size
        settingsRepo?.bufferSize = size
        reinitAudioEngine(context)
    }
    
    fun updateSampleRate(context: Context, rate: Int) {
        if (_sampleRate.value == rate) return
        _sampleRate.value = rate
        settingsRepo?.sampleRate = rate
        reinitAudioEngine(context)
    }

    fun updateMidiChannel(channel: Int) {
        _midiChannel.value = channel
        settingsRepo?.midiChannel = channel
    }

    fun updateAudioDevice(context: Context, deviceId: Int) {
        if (_selectedAudioDeviceId.value == deviceId) return
        _selectedAudioDeviceId.value = deviceId
        settingsRepo?.selectedAudioDeviceId = deviceId
        reinitAudioEngine(context)
    }

    private fun reinitAudioEngine(context: Context) {
        if (audioEngine !is DummyAudioEngine) {
            audioEngine.destroy()
            val deviceId = _selectedAudioDeviceId.value
            audioEngine.init(_sampleRate.value, _bufferSize.value, deviceId)
            
            // Reload SF2s silently for all configured channels
            _channels.value.forEach { ch ->
                if (ch.sfId >= 0 && ch.soundFont != null) {
                    val restoredFile = File(context.filesDir, "sf2_ch${ch.id}_${ch.soundFont}")
                    if (restoredFile.exists()) {
                        val newSfId = audioEngine.loadSoundFont(restoredFile.absolutePath)
                        if (newSfId >= 0) {
                            audioEngine.programSelect(ch.id, newSfId, ch.bank, ch.program)
                            // Update internal state
                            _channels.value = _channels.value.map {
                                if (it.id == ch.id) it.copy(sfId = newSfId) else it
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Utility ---

    /**
     * Converte posição do fader (0.0-1.0) para dB (-60 a +6).
     * Corresponde à escala visual: position = (dB + 60) / 66
     * Inverso: dB = (position * 66) - 60
     */
    private fun faderToDb(value: Float): Float {
        return if (value <= 0.001f) -60f
        else (value * 66f) - 60f
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    private fun startVuMeterUpdate() {
        scope.launch {
            while (true) {
                delay(40) // ~25 FPS
                _channels.value = _channels.value.map { channel ->
                    val chId = channel.id
                    val count = activeNotesCount[chId] ?: 0
                    val currentInternal = channelInternalLevels[chId] ?: 0f
                    
                    val targetInternal = if (count > 0) 1.0f else 0.0f
                    
                    val attackRate = 0.6f
                    val releaseRate = 0.15f
                    
                    val newInternal = if (targetInternal > currentInternal) {
                        currentInternal + attackRate * (targetInternal - currentInternal)
                    } else {
                        currentInternal + releaseRate * (targetInternal - currentInternal)
                    }
                    
                    channelInternalLevels[chId] = newInternal
                    
                    // Post-fader visual application (RMS approximation applied via fader modifier)
                    val displayLevel = newInternal * channel.volume
                    
                    if (kotlin.math.abs(displayLevel - channel.level) > 0.005f) {
                        channel.copy(level = displayLevel.coerceIn(0f, 1f))
                    } else {
                        channel
                    }
                }

                // Calculate Master VU level (Peak accumulation of all non-muted channels)
                var peakAccumulator = 0f
                _channels.value.forEach { ch ->
                    if (!ch.isMuted) {
                        peakAccumulator += ch.level
                    }
                }
                
                // Scale accumulator down naturally, and limit to 1.0f
                val masterPreFader = kotlin.math.min(1.0f, peakAccumulator * 0.7f)
                val masterPostFader = masterPreFader * _masterVolume.value
                
                if (kotlin.math.abs(_masterLevel.value - masterPostFader) > 0.005f) {
                    _masterLevel.value = masterPostFader.coerceIn(0f, 1f)
                }
            }
        }
    }

    private fun triggerNoteOnVelocity(channelId: Int, velocity: Int) {
        val count = activeNotesCount.getOrDefault(channelId, 0)
        activeNotesCount[channelId] = count + 1
        
        // Add velocity bump for impact visualization
        val bump = (velocity / 127f) * 0.6f
        val current = channelInternalLevels[channelId] ?: 0f
        channelInternalLevels[channelId] = kotlin.math.min(1.0f, current + bump)
    }

    private fun triggerNoteOff(channelId: Int) {
        val count = activeNotesCount.getOrDefault(channelId, 0)
        activeNotesCount[channelId] = kotlin.math.max(0, count - 1)
    }

    override fun onCleared() {
        super.onCleared()
        midiManager?.stop()
        deviceAudioManager?.release()
        audioEngine.destroy()
        scope.cancel()
    }
}