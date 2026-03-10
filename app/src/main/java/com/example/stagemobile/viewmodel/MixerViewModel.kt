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
import kotlinx.coroutines.flow.combine
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
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

    private val _channels = MutableStateFlow<List<InstrumentChannel>>(emptyList())
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
    private val channelLastNoteTime = ConcurrentHashMap<Int, Long>()
    private var isVuPollingSuspended = false

    private var nextId = 7

    init {
        // Initial set of channels (7 default)
        val initialChannels = (1..7).map { i ->
            InstrumentChannel(i - 1, "Instrumento ${i.toString().padStart(2, '0')}", program = 0)
        }
        _channels.value = initialChannels
        startVuMeterUpdate()
    }

    // --- Audio Engine ---
    
    fun initSettings(context: Context, isTablet: Boolean = false) {
        if (settingsRepo == null) {
            val repo = SettingsRepository(context)
            settingsRepo = repo
            _bufferSize.value = repo.bufferSize
            _sampleRate.value = repo.sampleRate
            _midiChannel.value = repo.midiChannel
            _isMasterVisible.value = repo.showMaster
        }

        // Add 8th channel if on Tablet and we are at default 7 channels
        if (isTablet && _channels.value.size == 7) {
            val eighthChannel = InstrumentChannel(7, "Instrumento 08", program = 0)
            _channels.value = _channels.value + eighthChannel
            nextId = 8 // Ensure next manual Add Channel (+ CH) starts at 9 (id 8)
        }
    }

    fun loadSoundFontForChannel(context: Context, channelId: Int, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            isVuPollingSuspended = true
            try {
                Log.d(TAG, "Starting SF2 load sequence for channel $channelId. URI: $uri")
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open URI: $uri")

                // Get the real filename from ContentResolver
                val sf2FileName = getDisplayName(context, uri)
                    ?: uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.substringAfterLast(':')
                    ?: "soundfont_ch$channelId.sf2"
                val outputFile = File(context.filesDir, "sf2_ch${channelId}_$sf2FileName")

                // OPTIMISTIC UPDATE: Update UI name immediately before heavy loading
                _channels.value = _channels.value.map {
                    if (it.id == channelId) it.copy(soundFont = "Carregando $sf2FileName...") else it
                }

                inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val sfId = audioEngine.loadSoundFont(outputFile.absolutePath)
                if (sfId >= 0) {
                    val loadBank = 0
                    val loadProgram = 0

                    // FINAL UPDATE: Set the real sfId and final name
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
            } finally {
                isVuPollingSuspended = false
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

    fun initMidi(context: Context, isTablet: Boolean = false) {
        initSettings(context, isTablet)
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
            try {
                Log.i(TAG, "Initializing FluidSynth (SR=${_sampleRate.value}, Buf=${_bufferSize.value}, Dev=$deviceId)")
                audioEngine.init(_sampleRate.value, _bufferSize.value, deviceId)
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: AudioEngine.init failed: ${e.message}", e)
            }
            
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
        
        // Track available devices and update Status Bar indicators
        scope.launch {
            combine(
                midiManager?.availableDevices ?: MutableStateFlow(emptyList()),
                _activeMidiDevices
            ) { available, active ->
                Pair(available, active)
            }.collect { (available, active) ->
                _availableMidiDevices.value = available
                
                // Only count as connected if the device is physically present AND enabled by the user
                val activeConnected = available.filter { active.contains(it.name) }
                
                _midiDeviceConnected.value = activeConnected.isNotEmpty()
                _midiDeviceName.value = when {
                    activeConnected.isEmpty() -> "Nenhum MIDI"
                    activeConnected.size == 1 -> activeConnected.first().name
                    else -> "[${activeConnected.size}] Controladores"
                }
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
        scope.launch(Dispatchers.IO) {
            isVuPollingSuspended = true
            try {
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
                
                // Trigger GC
                System.gc()
                Log.i(TAG, "SF2 removed for channel $channelId. RAM cleanup triggered.")
            } finally {
                isVuPollingSuspended = false
            }
        }
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

    /**
     * Converts a dB value to a linear gain multiplier.
     * Formula: 10^(dB / 20)
     * 0dB = 1.0 (Unity Gain)
     */
    private fun dbToGain(db: Float): Float {
        if (db <= -60f) return 0f
        return 10f.pow(db / 20f)
    }

    /**
     * Converts a linear gain multiplier back to dB.
     */
    private fun gainToDb(gain: Float): Float {
        if (gain <= 0.0001f) return -60f
        return 20f * kotlin.math.log10(gain)
    }

    /**
     * Maps a dB value to a visual VU level (0.0 to 1.2).
     * 0.9 = 0dB
     * 0.7 = -12dB
     * 0.5 = -24dB
     * 0.1 = -48dB
     * 0.0 = -54dB or less
     */
    private fun dbToLevel(db: Float): Float {
        val level = (db + 54f) / 60f
        return level.coerceIn(0f, 1.2f)
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
                if (isVuPollingSuspended) continue
                
                val levels = FloatArray(16)
                audioEngine.getChannelLevels(levels)

                _channels.value = _channels.value.map { channel ->
                    val chId = channel.id
                    val nativeLevel = if (chId in 0..15) levels[chId] else 0f
                    val lastNoteTime = channelLastNoteTime[chId] ?: 0L
                    val now = System.currentTimeMillis()
                    val ageMs = now - lastNoteTime
                    
                    // Apply deep decay over 5s
                    val ageFactor = if (ageMs < 5000L) {
                        1.0f - (ageMs.toFloat() / 5000f)
                    } else 0f
                    
                    val dynamicLevel = nativeLevel * ageFactor
                    val currentInternal = channelInternalLevels[chId] ?: 0f
                    
                    // Ballistics
                    val attackRate = 0.7f
                    val releaseRate = 0.15f
                    
                    val newInternal = if (dynamicLevel > currentInternal) {
                        currentInternal + attackRate * (dynamicLevel - currentInternal)
                    } else {
                        currentInternal + releaseRate * (dynamicLevel - currentInternal)
                    }
                    
                    channelInternalLevels[chId] = newInternal
                    
                    // Post-fader visual application using logarithmic mapping
                    val channelDb = faderToDb(channel.volume)
                    val channelGain = dbToGain(channelDb)
                    
                    // Final signal energy in dB
                    val finalGain = newInternal * channelGain
                    val currentDb = gainToDb(finalGain)
                    val displayLevel = dbToLevel(currentDb)
                    
                    if (kotlin.math.abs(displayLevel - channel.level) > 0.005f) {
                        channel.copy(level = displayLevel)
                    } else {
                        channel
                    }
                }

                // Calculate Master VU level (Peak accumulation of all non-muted channels)
                // Calculate Master VU level (Peak accumulation of energy from all non-muted channels)
                var peakEnergyAccumulator = 0f
                _channels.value.forEach { ch ->
                    if (!ch.isMuted) {
                        val chId = ch.id
                        val nativeLinear = channelInternalLevels[chId] ?: 0f
                        val channelDb = faderToDb(ch.volume)
                        val channelGain = dbToGain(channelDb)
                        peakEnergyAccumulator += nativeLinear * channelGain
                    }
                }
                
                // Calculate Master VU level using actual mapped Level
                val masterDb = faderToDb(_masterVolume.value)
                val masterGain = dbToGain(masterDb)
                
                // Final Master signal energy
                val totalMasterEnergy = peakEnergyAccumulator * masterGain
                val masterCurrentDb = gainToDb(totalMasterEnergy / 1.5f) // Correcting for summation headroom
                val masterDisplayLevel = dbToLevel(masterCurrentDb)
                
                if (kotlin.math.abs(_masterLevel.value - masterDisplayLevel) > 0.005f) {
                    _masterLevel.value = masterDisplayLevel
                }
            }
        }
    }

    private fun triggerNoteOnVelocity(channelId: Int, velocity: Int) {
        channelLastNoteTime[channelId] = System.currentTimeMillis()
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