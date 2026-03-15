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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.example.stagemobile.utils.UiUtils
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.log10
import kotlin.random.Random
import com.example.stagemobile.domain.model.Sf2Preset
import com.example.stagemobile.midi.MidiLearnMapping
import com.example.stagemobile.midi.MidiLearnTarget
import com.example.stagemobile.midi.MidiLearnTargetInfo

class MixerViewModel : ViewModel() {

    companion object {
        private const val TAG = "MixerViewModel"
        const val MASTER_CHANNEL_ID = -1
        const val GLOBAL_CHANNEL_ID = -2
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

    private val _availableAudioDevices = MutableStateFlow<List<AudioDeviceState>>(emptyList())
    val availableAudioDevices: StateFlow<List<AudioDeviceState>> = _availableAudioDevices

    private val _selectedAudioDeviceId = MutableStateFlow(-1)
    val selectedAudioDeviceId: StateFlow<Int> = _selectedAudioDeviceId

    // Derived StateFlow to ensure consistency between LCD and Settings
    val selectedAudioDeviceName: StateFlow<String> = combine(
        _availableAudioDevices,
        _selectedAudioDeviceId
    ) { devices, selectedId ->
        if (devices.isEmpty()) {
            "NENHUMA SAÍDA IDENTIFICADA"
        } else {
            devices.find { it.id == selectedId }?.name ?: "Buscando Interface..."
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), "Buscando Interface...")

    private val _sf2Loaded = MutableStateFlow(false)
    val sf2Loaded: StateFlow<Boolean> = _sf2Loaded

    private val _sf2Name = MutableStateFlow("")
    val sf2Name: StateFlow<String> = _sf2Name

    private val _ramUsageMb = MutableStateFlow(0)
    val ramUsageMb: StateFlow<Int> = _ramUsageMb

    private val _cpuUsagePercent = MutableStateFlow(0f)
    val cpuUsagePercent: StateFlow<Float> = _cpuUsagePercent

    // --- Settings States ---
    private val _bufferSize = MutableStateFlow(256)
    val bufferSize: StateFlow<Int> = _bufferSize

    private val _sampleRate = MutableStateFlow(48000)
    val sampleRate: StateFlow<Int> = _sampleRate

    private val _interpolationMethod = MutableStateFlow(4) // 4th Order default
    val interpolationMethod: StateFlow<Int> = _interpolationMethod

    private val _maxPolyphony = MutableStateFlow(64)
    val maxPolyphony: StateFlow<Int> = _maxPolyphony

    private val _velocityCurve = MutableStateFlow(0) // 0 = Linear (global)
    val velocityCurve: StateFlow<Int> = _velocityCurve

    private val _midiChannel = MutableStateFlow(0) // 0 = All
    val midiChannel: StateFlow<Int> = _midiChannel

    private val _isSustainInverted = MutableStateFlow(false)
    val isSustainInverted: StateFlow<Boolean> = _isSustainInverted.asStateFlow()

    private val _isMasterLimiterEnabled = MutableStateFlow(false)
    val isMasterLimiterEnabled: StateFlow<Boolean> = _isMasterLimiterEnabled.asStateFlow()

    // --- Global Performance States ---
    private val _globalOctaveShift = MutableStateFlow(0)
    val globalOctaveShift: StateFlow<Int> = _globalOctaveShift.asStateFlow()

    private val _globalTransposeShift = MutableStateFlow(0)
    val globalTransposeShift: StateFlow<Int> = _globalTransposeShift.asStateFlow()

    // --- Master States ---
    private val _isMasterVisible = MutableStateFlow(false)
    val isMasterVisible: StateFlow<Boolean> = _isMasterVisible

    private val _masterVolume = MutableStateFlow(0.8f) // Default 80%
    val masterVolume: StateFlow<Float> = _masterVolume

    private val _channelLevels = MutableStateFlow(FloatArray(16))
    val channelLevels: StateFlow<FloatArray> = _channelLevels.asStateFlow()

    // Tracks the raw values from C++ to detect "New Energy" (Impulses)
    private val lastNativeLevels = FloatArray(16)

    private val _masterLevel = MutableStateFlow(0f)
    val masterLevel: StateFlow<Float> = _masterLevel

    private val activeNotesCount = ConcurrentHashMap<Int, Int>()
    private val channelInternalLevels = ConcurrentHashMap<Int, Float>()
    private val channelLastNoteTime = ConcurrentHashMap<Int, Long>()
    private var isPeakPollingSuspended = false
    private var logClearJob: Job? = null

    private val _lastSystemEvent = MutableStateFlow("")
    val lastSystemEvent: StateFlow<String> = _lastSystemEvent.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // --- SF2 Preset Selector State ---
    data class PendingPresetSelection(
        val channelId: Int,
        val sfId: Int,
        val sf2Name: String,
        val presets: List<Sf2Preset>,
        val isInitialLoad: Boolean
    )
    private val _pendingPresetSelection = MutableStateFlow<PendingPresetSelection?>(null)
    val pendingPresetSelection: StateFlow<PendingPresetSelection?> = _pendingPresetSelection.asStateFlow()

    // Cache: Maps SF2 filename → sfId already loaded in FluidSynth
    private val loadedSf2Cache = ConcurrentHashMap<String, Int>()

    // --- MIDI Learn State ---
    private val _isMidiLearnActive = MutableStateFlow(false)
    val isMidiLearnActive: StateFlow<Boolean> = _isMidiLearnActive.asStateFlow()

    private val _midiLearnTarget = MutableStateFlow<MidiLearnTargetInfo?>(null)
    val midiLearnTarget: StateFlow<MidiLearnTargetInfo?> = _midiLearnTarget.asStateFlow()

    private val _midiLearnMappings = MutableStateFlow<List<MidiLearnMapping>>(emptyList())
    val midiLearnMappings: StateFlow<List<MidiLearnMapping>> = _midiLearnMappings.asStateFlow()

    private val _midiLearnFeedback = MutableStateFlow<String?>(null)
    val midiLearnFeedback: StateFlow<String?> = _midiLearnFeedback.asStateFlow()

    private var feedbackJob: Job? = null

    // --- Fast Path Cache for Latency Zero ---
    private var armedChannelsCache: List<InstrumentChannel> = emptyList()

    private var nextId = 7

    init {
        // Initial set of channels (7 default)
        val initialChannels = (1..7).map { i ->
            InstrumentChannel(i - 1, "Nenhum SF2", program = 0)
        }
        _channels.value = initialChannels
        rebuildArmedChannelsCache()
        startPeakMeterUpdate()
    }

    // --- MIDI Learn Functions ---

    fun toggleMidiLearn() {
        val newState = !_isMidiLearnActive.value
        _isMidiLearnActive.value = newState
        if (!newState) {
            _midiLearnTarget.value = null
            _pendingUnmap.value = null
        }
        Log.i(TAG, "MIDI Learn ${if (newState) "ACTIVATED" else "DEACTIVATED"}")
    }

    fun selectLearnTarget(target: MidiLearnTarget, channelId: Int) {
        if (!_isMidiLearnActive.value) return
        _midiLearnTarget.value = MidiLearnTargetInfo(target, channelId)
        val label = when (target) {
            MidiLearnTarget.FADER -> "Fader"
            MidiLearnTarget.ARM -> "ARM"
            MidiLearnTarget.OCTAVE_UP -> "Oct+"
            MidiLearnTarget.OCTAVE_DOWN -> "Oct-"
            MidiLearnTarget.TRANSPOSE_UP -> "Trn+"
            MidiLearnTarget.TRANSPOSE_DOWN -> "Trn-"
        }
        val chLabel = (channelId + 1).toString().padStart(2, '0')
        Log.i(TAG, "MIDI Learn target: $label CH $chLabel — waiting for CC...")
    }

    private fun completeMidiLearn(deviceName: String, ccNumber: Int, midiChannel: Int) {
        val target = _midiLearnTarget.value ?: return
        val newMapping = MidiLearnMapping(
            target = target.target,
            channelId = target.channelId,
            ccNumber = ccNumber,
            midiChannel = midiChannel,
            deviceName = deviceName
        )

        // Add new mapping (allow multiple CCs per control), but prevent exact duplicate
        val alreadyExists = _midiLearnMappings.value.any {
            it.target == newMapping.target && it.channelId == newMapping.channelId &&
            it.ccNumber == newMapping.ccNumber && it.midiChannel == newMapping.midiChannel
        }
        if (alreadyExists) {
            Log.w(TAG, "MIDI Learn: mapping already exists, skipping")
            _midiLearnTarget.value = null
            return
        }

        val updated = _midiLearnMappings.value + newMapping
        _midiLearnMappings.value = updated
        settingsRepo?.saveMidiMappings(updated)

        // Feedback toast (3 seconds)
        val label = when (target.target) {
            MidiLearnTarget.FADER -> "Fader"
            MidiLearnTarget.ARM -> "ARM"
            MidiLearnTarget.OCTAVE_UP -> "Oct+"
            MidiLearnTarget.OCTAVE_DOWN -> "Oct-"
            MidiLearnTarget.TRANSPOSE_UP -> "Trn+"
            MidiLearnTarget.TRANSPOSE_DOWN -> "Trn-"
        }
        val chLabel = when (target.channelId) {
            MASTER_CHANNEL_ID -> "MASTER"
            GLOBAL_CHANNEL_ID -> "GLOBAL"
            else -> "CH ${(target.channelId + 1).toString().padStart(2, '0')}"
        }
        val msg = "CC $ccNumber → $label $chLabel"
        _midiLearnFeedback.value = msg
        Log.i(TAG, "MIDI Learn complete: $msg")

        feedbackJob?.cancel()
        feedbackJob = scope.launch {
            delay(3000)
            _midiLearnFeedback.value = null
        }

        _midiLearnTarget.value = null
    }

    // --- MIDI Unmap ---

    data class PendingUnmap(
        val target: MidiLearnTarget,
        val channelId: Int,
        val mappings: List<MidiLearnMapping>
    )
    private val _pendingUnmap = MutableStateFlow<PendingUnmap?>(null)
    val pendingUnmap: StateFlow<PendingUnmap?> = _pendingUnmap.asStateFlow()

    fun requestUnmap(target: MidiLearnTarget, channelId: Int) {
        val mappings = _midiLearnMappings.value.filter { it.target == target && it.channelId == channelId }
        if (mappings.isEmpty()) return
        _pendingUnmap.value = PendingUnmap(target, channelId, mappings)
    }

    fun confirmUnmap(mapping: MidiLearnMapping) {
        val updated = _midiLearnMappings.value.filter {
            !(it.target == mapping.target && it.channelId == mapping.channelId &&
              it.ccNumber == mapping.ccNumber && it.midiChannel == mapping.midiChannel)
        }
        _midiLearnMappings.value = updated
        settingsRepo?.saveMidiMappings(updated)
        _pendingUnmap.value = null
        Log.i(TAG, "MIDI mapping removed: CC ${mapping.ccNumber} from ${mapping.target.name} CH ${mapping.channelId}")
    }

    fun unmapAll(target: MidiLearnTarget, channelId: Int) {
        val updated = _midiLearnMappings.value.filter { !(it.target == target && it.channelId == channelId) }
        _midiLearnMappings.value = updated
        settingsRepo?.saveMidiMappings(updated)
        _pendingUnmap.value = null
        Log.i(TAG, "All MIDI mappings cleared for ${target.name} CH $channelId")
    }

    fun dismissUnmap() {
        _pendingUnmap.value = null
    }

    fun getMappingsForControl(target: MidiLearnTarget, channelId: Int): List<MidiLearnMapping> {
        return _midiLearnMappings.value.filter { it.target == target && it.channelId == channelId }
    }

    // --- Audio Engine ---
    
    fun initSettings(context: Context) {
        val isTablet = UiUtils.isTablet(context)
        if (settingsRepo == null) {
            val repo = SettingsRepository(context)
            settingsRepo = repo
            _bufferSize.value = repo.bufferSize
            _sampleRate.value = repo.sampleRate
            _midiChannel.value = repo.midiChannel
            _isMasterVisible.value = repo.showMaster
            _interpolationMethod.value = repo.interpolationMethod
            _maxPolyphony.value = repo.maxPolyphony
            _velocityCurve.value = repo.velocityCurve
            _isSustainInverted.value = repo.isSustainInverted
            _isMasterLimiterEnabled.value = repo.masterLimiterEnabled
            // Load saved MIDI Learn mappings
            _midiLearnMappings.value = repo.loadMidiMappings()
            Log.i(TAG, "Loaded ${_midiLearnMappings.value.size} MIDI Learn mappings")
        }

        // Apply saved engine settings after init
        audioEngine.setInterpolation(_interpolationMethod.value)
        audioEngine.setPolyphony(_maxPolyphony.value)
        audioEngine.setMasterLimiter(_isMasterLimiterEnabled.value)

        // Add 8th channel if on Tablet and we are at default 7 channels
        if (isTablet && _channels.value.size == 7) {
            val eighthChannel = InstrumentChannel(7, "Instrumento 08", program = 0)
            _channels.value = _channels.value + eighthChannel
            nextId = 8 // Ensure next manual Add Channel (+ CH) starts at 9 (id 8)
        }
        
        _isReady.value = true
        startPeakMeterUpdate()
        Log.i(TAG, "System initialization COMPLETE (isReady=true)")
    }

    fun loadSoundFontForChannel(context: Context, channelId: Int, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            isPeakPollingSuspended = true
            try {
                Log.d(TAG, "Starting SF2 load sequence for channel $channelId. URI: $uri")

                // Get the real filename from ContentResolver
                val sf2FileName = getDisplayName(context, uri)
                    ?: uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.substringAfterLast(':')
                    ?: "soundfont_ch$channelId.sf2"

                // Check SF2 Cache: Is this SF2 already loaded?
                val cachedSfId = loadedSf2Cache[sf2FileName]

                val sfId: Int
                if (cachedSfId != null && cachedSfId >= 0) {
                    // Cache HIT — Skip copy and load
                    Log.i(TAG, "SF2 Cache HIT for '$sf2FileName' → sfId=$cachedSfId")
                    sfId = cachedSfId
                    _channels.value = _channels.value.map {
                        if (it.id == channelId) it.copy(soundFont = sf2FileName, sfId = sfId) else it
                    }
                } else {
                    // Cache MISS — Full load sequence
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open URI: $uri")

                    val outputFile = File(context.filesDir, "sf2_ch${channelId}_$sf2FileName")

                    _channels.value = _channels.value.map {
                        if (it.id == channelId) it.copy(soundFont = "Carregando $sf2FileName...") else it
                    }

                    inputStream.use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    sfId = audioEngine.loadSoundFont(outputFile.absolutePath)
                    if (sfId < 0) {
                        Log.e(TAG, "Failed to load SF2 for channel $channelId")
                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(soundFont = null) else it
                    })
                        return@launch
                    }

                    // Register in cache
                    loadedSf2Cache[sf2FileName] = sfId
                    Log.i(TAG, "SF2 Cache MISS for '$sf2FileName' → loaded as sfId=$sfId (cached)")

                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(soundFont = "Aquecendo $sf2FileName...") else it
                    })
                    (audioEngine as? FluidSynthEngine)?.warmUpChannel(channelId)
                }

                // List presets from the loaded SoundFont
                val presets = audioEngine.getPresets(sfId)
                Log.i(TAG, "SF2 '$sf2FileName' has ${presets.size} presets")

                if (presets.size <= 1) {
                    // Single preset or empty → auto-select
                    val bank = presets.firstOrNull()?.bank ?: 0
                    val program = presets.firstOrNull()?.program ?: 0
                    val presetName = presets.firstOrNull()?.name
                    val displayName = if (presetName != null) "$sf2FileName [$presetName]" else sf2FileName

                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(
                            soundFont = displayName,
                            sfId = sfId,
                            bank = bank,
                            program = program
                        ) else it
                    })
                    audioEngine.programSelect(channelId, sfId, bank, program)
                    _sf2Loaded.value = true
                    _sf2Name.value = sf2FileName
                    Log.i(TAG, "SF2 auto-selected: ch=$channelId, sfId=$sfId, bank=$bank, prog=$program")
                } else {
                    // Multiple presets → Open selector dialog
                    _channels.value = _channels.value.map {
                        if (it.id == channelId) it.copy(soundFont = sf2FileName, sfId = sfId) else it
                    }
                    _sf2Loaded.value = true
                    _sf2Name.value = sf2FileName

                    // Emit pending selection to trigger UI Dialog
                    _pendingPresetSelection.value = PendingPresetSelection(
                        channelId = channelId,
                        sfId = sfId,
                        sf2Name = sf2FileName,
                        presets = presets,
                        isInitialLoad = true
                    )
                    Log.i(TAG, "SF2 multi-preset: emitting ${presets.size} presets for dialog (ch=$channelId)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading SF2 for channel $channelId: ${e.message}", e)
            } finally {
                isPeakPollingSuspended = false
            }
        }
    }

    /**
     * Called from the PresetSelectorDialog when the user picks a preset.
     */
    fun selectPresetForChannel(channelId: Int, bank: Int, program: Int, presetName: String) {
        val channel = _channels.value.find { it.id == channelId } ?: return
        val sfId = channel.sfId
        if (sfId < 0) return

        val sf2BaseName = channel.soundFont?.substringBefore(" [") ?: "SoundFont"
        val displayName = "$sf2BaseName [$presetName]"

        updateChannels(_channels.value.map {
            if (it.id == channelId) it.copy(
                soundFont = displayName,
                bank = bank,
                program = program
            ) else it
        })

        audioEngine.programSelect(channelId, sfId, bank, program)
        _pendingPresetSelection.value = null
        Log.i(TAG, "Preset selected: ch=$channelId, sfId=$sfId, bank=$bank, prog=$program, name=$presetName")
    }

    /**
     * Dismiss the preset selector dialog without changes.
     * If it was an initial load (not a change), we clean up the channel.
     */
    fun dismissPresetSelector() {
        val pending = _pendingPresetSelection.value
        if (pending != null && pending.isInitialLoad) {
            Log.i(TAG, "Canceling initial load for channel ${pending.channelId}. Cleaning up.")
            removeSoundFont(pending.channelId)
        }
        _pendingPresetSelection.value = null
    }

    /**
     * Reopen the preset selector for a channel that already has an SF2 loaded.
     */
    fun changePresetForChannel(channelId: Int) {
        val channel = _channels.value.find { it.id == channelId } ?: return
        val sfId = channel.sfId
        if (sfId < 0) return

        scope.launch(Dispatchers.IO) {
            val presets = audioEngine.getPresets(sfId)
            if (presets.size > 1) {
                val sf2Name = channel.soundFont?.substringBefore(" [") ?: "SoundFont"
                _pendingPresetSelection.value = PendingPresetSelection(
                    channelId = channelId,
                    sfId = sfId,
                    sf2Name = sf2Name,
                    presets = presets,
                    isInitialLoad = false
                )
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
                kotlinx.coroutines.delay(3000) // 3s — info não precisa ser real-time
            }
        }
    }

    // --- MIDI ---

    fun initMidi(context: Context) {
        val isTablet = UiUtils.isTablet(context)
        initSettings(context)
        _activeMidiDevices.value = settingsRepo?.activeMidiDevices ?: emptySet()

        if (deviceAudioManager == null) {
            deviceAudioManager = com.example.stagemobile.audio.DeviceAudioManager(context)
            val savedName = settingsRepo?.selectedAudioDeviceName
            
            scope.launch {
                deviceAudioManager!!.availableAudioDevices.collect { devices ->
                    if (devices.isEmpty()) return@collect
                    
                    val oldDevices = _availableAudioDevices.value
                    _availableAudioDevices.value = devices
                    
                    // Detect removal for notification
                    val removed = oldDevices.filter { old -> devices.none { it.id == old.id } }
                    removed.forEach { 
                        updateSystemEvent("Saída de áudio desconectada: ${it.name}")
                        Log.w(TAG, "Audio device disconnected: ${it.name}")
                    }

                    // Reactive ID Discovery
                    val targetName = savedName ?: devices.find { it.name.startsWith("Interna") }?.name
                    
                    if (targetName != null) {
                        val matchingDevice = devices.find { it.name == targetName }
                        if (matchingDevice != null) {
                            if (matchingDevice.id != _selectedAudioDeviceId.value) {
                                Log.i(TAG, "Syncing dynamic Audio Device ID for '$targetName': ${matchingDevice.id}")
                                _selectedAudioDeviceId.value = matchingDevice.id
                                
                                // Direct reinit if engine already exists, otherwise the main init will pick it up
                                if (audioEngine !is DummyAudioEngine) {
                                    reinitAudioEngine(context)
                                }
                            }
                        } else {
                            val fallbackInternal = devices.find { it.name.startsWith("Interna") }
                            if (fallbackInternal != null && fallbackInternal.id != _selectedAudioDeviceId.value) {
                                _selectedAudioDeviceId.value = fallbackInternal.id
                                if (audioEngine !is DummyAudioEngine) reinitAudioEngine(context)
                            }
                        }
                    } else {
                        // Absolute fallback: pick first available device if no matching found
                        val firstId = devices.first().id
                        if (firstId != _selectedAudioDeviceId.value) {
                            _selectedAudioDeviceId.value = firstId
                            if (audioEngine !is DummyAudioEngine) reinitAudioEngine(context)
                        }
                    }
                }
            }
        }

        // Override with Real audio engine if full context is provided
        if (audioEngine is DummyAudioEngine) {
            audioEngine = FluidSynthEngine()
            val deviceId = _selectedAudioDeviceId.value
            try {
                Log.i(TAG, "Initializing FluidSynth Engine (SR=${_sampleRate.value}, Buf=${_bufferSize.value}, Dev=$deviceId)")
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
                    val cached = armedChannelsCache
                    for (i in cached.indices) {
                        val ch = cached[i]
                        if (key in ch.minNote..ch.maxNote && (ch.midiChannel == -1 || ch.midiChannel == channel)) {
                            // Filter by deviceName
                            if (ch.midiDeviceName == null || ch.midiDeviceName == deviceName) {
                                val finalShift = (ch.octaveShift + _globalOctaveShift.value) * 12 + 
                                               ch.transposeShift + _globalTransposeShift.value
                                val transposedKey = (key + finalShift).coerceIn(0, 127)
                                audioEngine.noteOn(ch.id, transposedKey, applyVelocityCurve(velocity, ch.velocityCurve))
                                triggerNoteOnVelocity(ch.id, velocity)
                            }
                        }
                    }
                }
            },
            onNoteOff = { deviceName, channel, key ->
                val activeDevices = settingsRepo?.activeMidiDevices ?: emptySet()
                if (!activeDevices.contains(deviceName)) return@MidiConnectionManager

                if (_midiChannel.value == 0 || _midiChannel.value - 1 == channel) {
                    val cached = armedChannelsCache
                    for (i in cached.indices) {
                        val ch = cached[i]
                        if (ch.midiChannel == -1 || ch.midiChannel == channel) {
                            if (ch.midiDeviceName == null || ch.midiDeviceName == deviceName) {
                                val finalShift = (ch.octaveShift + _globalOctaveShift.value) * 12 + 
                                               ch.transposeShift + _globalTransposeShift.value
                                val transposedKey = (key + finalShift).coerceIn(0, 127)
                                audioEngine.noteOff(ch.id, transposedKey)
                                triggerNoteOff(ch.id)
                            }
                        }
                    }
                }
            },
            onControlChange = { deviceName, channel, controller, value ->
                val activeDevices = settingsRepo?.activeMidiDevices ?: emptySet()
                if (!activeDevices.contains(deviceName)) return@MidiConnectionManager

                // --- MIDI Learn Intercept ---
                if (_isMidiLearnActive.value && _midiLearnTarget.value != null) {
                    val target = _midiLearnTarget.value!!
                    
                    // SECURITY: If it's an instrument channel (not master), check if it's OMNI or linked to this device
                    val canLearn = if (target.channelId == MASTER_CHANNEL_ID) {
                        true
                    } else {
                        val ch = _channels.value.find { it.id == target.channelId }
                        ch?.midiDeviceName == null || ch.midiDeviceName == deviceName
                    }
                    
                    if (canLearn) {
                        completeMidiLearn(deviceName, controller, channel)
                    } else {
                        Log.w(TAG, "MIDI Learn REJECTED: Channel ${target.channelId + 1} is locked to another device")
                    }
                    return@MidiConnectionManager
                }

                // --- Apply learned mappings (supports multiple CCs per control) ---
                val matchingMappings = _midiLearnMappings.value.filter { 
                    it.ccNumber == controller && 
                    it.midiChannel == channel &&
                    (it.deviceName == null || it.deviceName == deviceName)
                }
                if (matchingMappings.isNotEmpty()) {
                    matchingMappings.forEach { mapping ->
                        when (mapping.target) {
                            MidiLearnTarget.FADER -> {
                                val volume = value / 127f
                                if (mapping.channelId == MASTER_CHANNEL_ID) {
                                    updateMasterVolume(volume)
                                } else {
                                    updateVolume(mapping.channelId, volume)
                                }
                            }
                            MidiLearnTarget.ARM -> {
                                val shouldArm = value > 64
                                val ch = _channels.value.find { it.id == mapping.channelId }
                                if (ch != null && ch.isArmed != shouldArm) {
                                    toggleArm(mapping.channelId)
                                }
                            }
                            MidiLearnTarget.OCTAVE_UP -> {
                                if (value > 64) {
                                    if (mapping.channelId == GLOBAL_CHANNEL_ID) updateGlobalOctaveShift(1)
                                    else updateOctaveShift(mapping.channelId, 1)
                                }
                            }
                            MidiLearnTarget.OCTAVE_DOWN -> {
                                if (value > 64) {
                                    if (mapping.channelId == GLOBAL_CHANNEL_ID) updateGlobalOctaveShift(-1)
                                    else updateOctaveShift(mapping.channelId, -1)
                                }
                            }
                            MidiLearnTarget.TRANSPOSE_UP -> {
                                if (value > 64) {
                                    if (mapping.channelId == GLOBAL_CHANNEL_ID) updateGlobalTransposeShift(1)
                                    else updateTransposeShift(mapping.channelId, 1)
                                }
                            }
                            MidiLearnTarget.TRANSPOSE_DOWN -> {
                                if (value > 64) {
                                    if (mapping.channelId == GLOBAL_CHANNEL_ID) updateGlobalTransposeShift(-1)
                                    else updateTransposeShift(mapping.channelId, -1)
                                }
                            }
                        }
                    }
                    return@MidiConnectionManager
                }

                // --- Default CC handling ---
                if (_midiChannel.value == 0 || _midiChannel.value - 1 == channel) {
                    if (controller == 7) {
                        val volume = value / 127f
                        updateVolume(channel, volume)
                    }

                    // Route standard CCs to armed channels
                    val armedIds = getArmedChannelIds()
                    armedIds.forEach { chId ->
                        val ch = _channels.value.find { it.id == chId }
                        if (ch != null && (ch.midiChannel == -1 || ch.midiChannel == channel)) {
                            if (ch.midiDeviceName == null || ch.midiDeviceName == deviceName) {
                                var sendValue = value
                                val shouldSend = when (controller) {
                                    64 -> {
                                        if (_isSustainInverted.value) sendValue = 127 - value
                                        ch.sustainEnabled
                                    }
                                    1 -> ch.modulationEnabled
                                    11 -> ch.expressionEnabled
                                    4 -> ch.footControllerEnabled
                                    7 -> false // Previne conflito com o fader do mixer na engine
                                    else -> true
                                }
                                
                                if (shouldSend) {
                                    audioEngine.controlChange(chId, controller, sendValue)
                                }
                            }
                        }
                    }
                }
            },
            onPitchBend = { deviceName, channel, value ->
                val activeDevices = settingsRepo?.activeMidiDevices ?: emptySet()
                if (!activeDevices.contains(deviceName)) return@MidiConnectionManager

                if (_midiChannel.value == 0 || _midiChannel.value - 1 == channel) {
                    val armedIds = getArmedChannelIds()
                    armedIds.forEach { chId ->
                        val ch = _channels.value.find { it.id == chId } ?: return@forEach
                        if (ch.midiChannel == -1 || ch.midiChannel == channel) {
                            if (ch.midiDeviceName == null || ch.midiDeviceName == deviceName) {
                                if (ch.pitchBendEnabled) {
                                    // Pitch bend is global to the voice/channel in FluidSynth, 
                                    // so we don't apply transposition to the pitch bend value itself
                                    audioEngine.pitchBend(chId, value)
                                }
                            }
                        }
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
                _activeMidiDevices,
                _availableAudioDevices
            ) { available, active, audioDevices ->
                // Filter out MIDI devices that are actually Audio Interfaces
                // Heuristic: If MIDI name is found within an Audio device name, it's likely the same hardware
                val filtered = available.filter { midi ->
                    val isAudioInterface = audioDevices.any { audio ->
                        audio.name.contains(midi.name, ignoreCase = true) || 
                        midi.name.contains(audio.name.replace("Externa (", "").replace(")", ""), ignoreCase = true)
                    }
                    !isAudioInterface
                }
                Triple(filtered, active, available) 
            }.collect { (filtered, active, allAvailable) ->
                val oldMidiDevices = _availableMidiDevices.value
                _availableMidiDevices.value = filtered
                
                // Detect removed MIDI devices for notification
                val removedMidi = oldMidiDevices.filter { old -> filtered.none { it.id == old.id } }
                removedMidi.forEach { 
                    updateSystemEvent("Controlador MIDI desconectado: ${it.name}")
                    Log.w(TAG, "MIDI device disconnected: ${it.name}")
                }

                // Only count as connected if the device is physically present AND enabled by the user
                val activeConnected = filtered.filter { active.contains(it.name) }
                
                _midiDeviceConnected.value = activeConnected.isNotEmpty()
                _midiDeviceName.value = when {
                    activeConnected.isEmpty() -> "Nenhum MIDI"
                    activeConnected.size == 1 -> activeConnected.first().name
                    else -> "[${activeConnected.size}] Controladores"
                }

                // AUTO-ACTIVATION: If exactly 1 valid MIDI device is found and none are active
                if (filtered.size == 1 && active.isEmpty()) {
                    val singleDevice = filtered.first().name
                    Log.i(TAG, "Auto-activating single MIDI controller: $singleDevice")
                    toggleActiveMidiDevice(singleDevice, true)
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
        rebuildArmedChannelsCache() // Update cache as device lock might change arm status logic
    }

    private fun rebuildArmedChannelsCache() {
        armedChannelsCache = _channels.value.filter { it.isArmed && it.sfId >= 0 }
    }

    private fun getArmedChannelIds(): List<Int> {
        return armedChannelsCache.map { it.id }
    }
    
    // Internal helper to update channels and rebuild cache
    private fun updateChannels(newList: List<InstrumentChannel>) {
        _channels.value = newList
        rebuildArmedChannelsCache()
    }

    // --- Virtual Keyboard ---

    // --- Virtual Keyboard & MIDI Fast Path ---

    fun noteOn(midiNote: Int) {
        val cached = armedChannelsCache
        for (i in cached.indices) {
            val ch = cached[i]
            if (midiNote in ch.minNote..ch.maxNote) {
                val finalShift = (ch.octaveShift + _globalOctaveShift.value) * 12 + 
                               ch.transposeShift + _globalTransposeShift.value
                val transposedKey = (midiNote + finalShift).coerceIn(0, 127)
                audioEngine.noteOn(ch.id, transposedKey, 100)
                triggerNoteOnVelocity(ch.id, 100)
            }
        }
    }

    fun noteOff(midiNote: Int) {
        val cached = armedChannelsCache
        for (i in cached.indices) {
            val ch = cached[i]
            val finalShift = (ch.octaveShift + _globalOctaveShift.value) * 12 + 
                           ch.transposeShift + _globalTransposeShift.value
            val transposedKey = (midiNote + finalShift).coerceIn(0, 127)
            audioEngine.noteOff(ch.id, transposedKey)
            triggerNoteOff(ch.id)
        }
    }

    // --- Channel Management ---

    fun addChannel(name: String) {
        if (_channels.value.size >= 16) return
        val newChannel = InstrumentChannel(id = nextId++, name = name)
        updateChannels(_channels.value + newChannel)
    }


    fun updateVolume(channelId: Int, newVolume: Float) {
        updateChannels(_channels.value.map {
            if (it.id == channelId) it.copy(volume = newVolume) else it
        })
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
        updateChannels(_channels.value.map {
            if (it.id == channelId) it.copy(isMuted = !it.isMuted) else it
        })
    }

    fun toggleSolo(channelId: Int) {
        updateChannels(_channels.value.map {
            if (it.id == channelId) it.copy(isSolo = !it.isSolo) else it
        })
    }

    private fun updateChannel(channelId: Int, update: (com.example.stagemobile.domain.model.InstrumentChannel) -> com.example.stagemobile.domain.model.InstrumentChannel) {
        updateChannels(_channels.value.map {
            if (it.id == channelId) update(it) else it
        })
    }

    fun toggleArm(channelId: Int) {
        val ch = _channels.value.find { it.id == channelId } ?: return
        val wasArmed = ch.isArmed
        updateChannel(channelId) { it.copy(isArmed = !it.isArmed) }
        
        // Envia obrigatoriamente Note Off e Sustain Off quando o canal é desarmado
        if (wasArmed) {
            audioEngine.controlChange(channelId, 123, 0) // All notes off
            audioEngine.controlChange(channelId, 64, 0) // Sustain off
            activeNotesCount[channelId] = 0 // Reseta contagem visual de notas
        }
    }

    fun panic() {
        Log.i(TAG, "Executing Global Panic")
        audioEngine.panic()
        activeNotesCount.clear()
        _channels.value.forEach { 
            channelInternalLevels[it.id] = 0f 
        }
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

    fun updateChannelVelocityCurve(channelId: Int, curve: Int) {
        updateChannel(channelId) { it.copy(velocityCurve = curve) }
    }

    fun updateOctaveShift(channelId: Int, delta: Int) {
        if (channelId == GLOBAL_CHANNEL_ID) {
            updateGlobalOctaveShift(delta)
            return
        }
        updateChannel(channelId) {
            val newShift = (it.octaveShift + delta).coerceIn(-4, 4)
            it.copy(octaveShift = newShift)
        }
    }

    fun updateTransposeShift(channelId: Int, delta: Int) {
        if (channelId == GLOBAL_CHANNEL_ID) {
            updateGlobalTransposeShift(delta)
            return
        }
        updateChannel(channelId) {
            val newShift = (it.transposeShift + delta).coerceIn(-12, 12)
            it.copy(transposeShift = newShift)
        }
    }

    fun updateGlobalOctaveShift(delta: Int) {
        val newVal = (_globalOctaveShift.value + delta).coerceIn(-4, 4)
        _globalOctaveShift.value = newVal
        Log.i(TAG, "Global Octave Shift: $newVal")
    }

    fun updateGlobalTransposeShift(delta: Int) {
        val newVal = (_globalTransposeShift.value + delta).coerceIn(-12, 12)
        _globalTransposeShift.value = newVal
        Log.i(TAG, "Global Transpose Shift: $newVal")
    }

    fun updateChannelMidiFilter(channelId: Int, filterType: String, isEnabled: Boolean) {
        updateChannel(channelId) { 
            when (filterType) {
                "sustainEnabled" -> it.copy(sustainEnabled = isEnabled)
                "modulationEnabled" -> it.copy(modulationEnabled = isEnabled)
                "expressionEnabled" -> it.copy(expressionEnabled = isEnabled)
                "pitchBendEnabled" -> it.copy(pitchBendEnabled = isEnabled)
                "footControllerEnabled" -> it.copy(footControllerEnabled = isEnabled)
                else -> it
            }
        }
    }

    fun updateSustainInversion(inverted: Boolean) {
        _isSustainInverted.value = inverted
        settingsRepo?.isSustainInverted = inverted
        Log.i(TAG, "Global Sustain Pedal Inversion set to: $inverted")
    }

    fun removeSoundFont(channelId: Int) {
        scope.launch(Dispatchers.IO) {
            performSoundFontRemoval(channelId)
        }
    }

    private fun performSoundFontRemoval(channelId: Int) {
        isPeakPollingSuspended = true
        try {
            // Find sfId and name before resetting state
            val ch = _channels.value.find { it.id == channelId }
            val sfIdToUnload = ch?.sfId ?: -1
            val soundFontFullName = ch?.soundFont

            // All notes off on this channel to stop any lingering sound
            for (key in 0..127) {
                audioEngine.noteOff(channelId, key)
            }
            activeNotesCount[channelId] = 0
            channelInternalLevels[channelId] = 0f

            // REFERENCE COUNTING: Only unload from engine if NO other channel is using this sfId
            if (sfIdToUnload >= 0) {
                // Check other users EXCLUDING the current channel
                val otherUsers = _channels.value.any { it.id != channelId && it.sfId == sfIdToUnload }
                if (!otherUsers) {
                    Log.i(TAG, "Unloading last instance of sfId $sfIdToUnload from engine and cache")
                    audioEngine.unloadSoundFont(sfIdToUnload)
                    
                    // Clear from cache to allow clean re-load
                    val baseName = soundFontFullName?.substringBefore(" [")
                    if (baseName != null) {
                        loadedSf2Cache.remove(baseName)
                        Log.d(TAG, "Removed '$baseName' from SF2 cache")
                    }
                } else {
                    Log.d(TAG, "sfId $sfIdToUnload is still in use by other channels, keeping in memory/cache")
                }
            }
        } finally {
            // Reset channel state in UI
            updateChannels(_channels.value.map {
                if (it.id == channelId) it.copy(
                    soundFont = null,
                    sfId = -1,
                    isArmed = false,
                    program = 0,
                    bank = 0
                ) else it
            })

            // Trigger GC to free memory
            System.gc()
            Log.i(TAG, "SF2 removed for channel $channelId. RAM cleanup triggered.")

            isPeakPollingSuspended = false
        }
    }

    fun removeChannel(channelId: Int) {
        scope.launch(Dispatchers.IO) {
            // 1. Cleanup resources (Notes off, SF2 unload if not shared)
            performSoundFontRemoval(channelId)
            
            // 2. Remove from StateFlow
            _channels.value = _channels.value.filter { it.id != channelId }
            
            // 3. Cleanup associated UI/state maps
            activeNotesCount.remove(channelId)
            channelInternalLevels.remove(channelId)
            
            // 4. Trigger GC for RAM cleanup
            System.gc()
            Log.i(TAG, "Channel $channelId fully removed. RAM cleanup triggered.")
        }
    }

    fun updateChannelColor(channelId: Int, color: Long?) {
        updateChannel(channelId) { it.copy(color = color) }
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

    fun updateInterpolation(method: Int) {
        _interpolationMethod.value = method
        settingsRepo?.interpolationMethod = method
        audioEngine.setInterpolation(method)
        Log.i(TAG, "Interpolation updated to $method")
    }

    fun updatePolyphony(maxVoices: Int) {
        _maxPolyphony.value = maxVoices
        settingsRepo?.maxPolyphony = maxVoices
        audioEngine.setPolyphony(maxVoices)
        Log.i(TAG, "Polyphony updated to $maxVoices")
    }

    fun updateMasterLimiter(enabled: Boolean) {
        _isMasterLimiterEnabled.value = enabled
        settingsRepo?.masterLimiterEnabled = enabled
        audioEngine.setMasterLimiter(enabled)
        Log.i(TAG, "Master Limiter set to: $enabled")
    }

    fun updateVelocityCurve(curve: Int) {
        _velocityCurve.value = curve
        settingsRepo?.velocityCurve = curve
        Log.i(TAG, "Global velocity curve updated to $curve")
    }

    /**
     * Applies a velocity curve transformation.
     * @param velocity Raw MIDI velocity (0-127)
     * @param channelCurve Per-channel curve (-1 = use global)
     * @return Transformed velocity (1-127)
     */
    fun applyVelocityCurve(velocity: Int, channelCurve: Int = -1): Int {
        val curveType = if (channelCurve >= 0) channelCurve else _velocityCurve.value
        val normalized = velocity / 127.0
        val mapped = when (curveType) {
            0 -> normalized                                  // Linear
            1 -> Math.pow(normalized, 0.75)                 // Semi-Suave [NOVO]
            2 -> Math.sqrt(normalized)                       // Suave
            3 -> Math.cbrt(normalized)                        // Extra Suave
            4 -> Math.pow(normalized, 1.5)                  // Semi-Rígida [NOVO]
            5 -> normalized * normalized                     // Rígida
            6 -> {                                           // Logarítmica (Expressiva) [NOVO]
                if (normalized <= 0) 0.0
                else log10(1.0 + 9.0 * normalized)
            }
            7 -> {                                           // Curva-S (Sigmoid)
                val x = (normalized - 0.5) * 12.0
                1.0 / (1.0 + Math.exp(-x))
            }
            else -> normalized
        }
        return (mapped * 127).toInt().coerceIn(1, 127)
    }

    fun updateAudioDevice(context: Context, deviceId: Int) {
        _selectedAudioDeviceId.value = deviceId
        
        // Save by NAME for persistence, not ID
        val deviceName = _availableAudioDevices.value.find { it.id == deviceId }?.name
        settingsRepo?.selectedAudioDeviceName = if (deviceId == -1) null else deviceName
        
        reinitAudioEngine(context)
    }

    fun updateSystemEvent(msg: String) {
        _lastSystemEvent.value = msg
        Log.i(TAG, "System Event: $msg")
        
        // Auto-clear log after 5 seconds
        logClearJob?.cancel()
        logClearJob = scope.launch {
            delay(5000)
            _lastSystemEvent.value = ""
        }
    }

    fun exitSystem() {
        Log.i(TAG, "=== SYSTEM SHUTDOWN INITIATED ===")
        try {
            // 1. Unload all SoundFonts
            _channels.value.forEach { ch ->
                if (ch.sfId >= 0) {
                    audioEngine.unloadSoundFont(ch.sfId)
                }
            }
            
            // 2. Destroy Audio Engine (Oboe/AAudio)
            audioEngine.destroy()
            
            // 3. Stop MIDI Manager
            midiManager?.stop()
            
            // 4. Release Device Audio Manager
            deviceAudioManager?.release()
            
            // 5. Cancel all Coroutines
            scope.cancel()
            
            Log.i(TAG, "=== Resources released, ready for process death ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown: ${e.message}")
        }
    }

    private fun reinitAudioEngine(context: Context) {
        if (audioEngine is DummyAudioEngine) return
        
        scope.launch(Dispatchers.IO) {
            isPeakPollingSuspended = true
            try {
                Log.i(TAG, "Reinitializing Audio Engine (Buffer=${_bufferSize.value}, SR=${_sampleRate.value})")
                
                audioEngine.destroy()
                val deviceId = _selectedAudioDeviceId.value
                audioEngine.init(_sampleRate.value, _bufferSize.value, deviceId)
                
                // Re-apply engine globals
                audioEngine.setInterpolation(_interpolationMethod.value)
                audioEngine.setPolyphony(_maxPolyphony.value)
                audioEngine.setMasterLimiter(_isMasterLimiterEnabled.value)
                
                // Reload SF2s silently for all configured channels
                _channels.value.forEach { ch ->
                    if (ch.sfId >= 0 && ch.soundFont != null) {
                        // CRITICAL: Extract base name (remove preset suffix [ ... ])
                        val baseName = ch.soundFont!!.substringBefore(" [")
                        val restoredFile = File(context.filesDir, "sf2_ch${ch.id}_$baseName")
                        
                        Log.d(TAG, "Restoring channel ${ch.id}: looking for $restoredFile")
                        
                        if (restoredFile.exists()) {
                            val newSfId = audioEngine.loadSoundFont(restoredFile.absolutePath)
                            if (newSfId >= 0) {
                                (audioEngine as? FluidSynthEngine)?.warmUpChannel(ch.id)
                                audioEngine.programSelect(ch.id, newSfId, ch.bank, ch.program)
                                
                                // Update internal state with new sfId
                                _channels.value = _channels.value.map {
                                    if (it.id == ch.id) it.copy(sfId = newSfId) else it
                                }
                                Log.i(TAG, "Channel ${ch.id} restored successfully (sfId=$newSfId)")
                            } else {
                                Log.e(TAG, "Failed to reload soundfont for channel ${ch.id}")
                            }
                        } else {
                            Log.w(TAG, "Restoration failed: file not found for channel ${ch.id} ($baseName)")
                            // Reset sfId if file is gone to avoid ghost states
                            _channels.value = _channels.value.map {
                                if (it.id == ch.id) it.copy(sfId = -1) else it
                            }
                        }
                    }
                }
                
                updateSystemEvent("Motor Reiniciado (${_bufferSize.value} samples)")
                Log.i(TAG, "Audio Engine re-initialization COMPLETE")
            } catch (e: Exception) {
                Log.e(TAG, "Error during Audio Engine re-initialization: ${e.message}", e)
                updateSystemEvent("Erro ao reiniciar motor de áudio")
            } finally {
                isPeakPollingSuspended = false
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
     * Maps a dB value to a visual Peak level (0.0 to 1.2).
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

    private fun startPeakMeterUpdate() {
        scope.launch {
            while (true) {
                delay(32) // ~30 FPS for smoother animation
                if (isPeakPollingSuspended) continue
                
                val levels = FloatArray(16)
                audioEngine.getChannelLevels(levels)

                // Early-exit: if silence and already zeroed, avoid recomposition
                val hasAnyNativeSignal = levels.any { it > 0.0001f }
                val hasAnyInternalSignal = channelInternalLevels.values.any { it > 0.001f }
                if (!hasAnyNativeSignal && !hasAnyInternalSignal) {
                    if (_masterLevel.value > 0f) {
                        _masterLevel.value = 0f
                        _channelLevels.value = FloatArray(16)
                        channelInternalLevels.clear()
                    }
                    continue
                }

                val currentLevels = _channelLevels.value.copyOf()
                var anyLevelChanged = false

                _channels.value.forEachIndexed { index, channel ->
                    val chId = channel.id
                    val nativeLevel = if (chId in 0..15) levels[chId] else 0f
                    
                    // PEAK METER LOGIC:
                    // Instant Attack and Logarithmic Release for "Analog" feel.
                    val currentInternal = channelInternalLevels[chId] ?: 0f
                    val decayFactor = 0.85f // Multiplicative decay
                    val minVisibleLevel = 0.001f
                    
                    val newInternal = if (nativeLevel > currentInternal) {
                        nativeLevel // Instant attack
                    } else {
                        val decayed = currentInternal * decayFactor
                        if (decayed < minVisibleLevel) 0f else decayed
                    }
                    
                    lastNativeLevels[chId] = nativeLevel
                    if (newInternal > 0f) {
                        channelInternalLevels[chId] = newInternal
                    } else {
                        channelInternalLevels.remove(chId)
                    }
                    
                    val channelDb = faderToDb(channel.volume)
                    val channelGain = dbToGain(channelDb)
                    
                    val finalGain = newInternal * channelGain
                    val currentDb = gainToDb(finalGain)
                    val displayLevel = dbToLevel(currentDb)
                    
                    if (chId in 0..15) {
                        // Use a smaller threshold or explicit zero check to avoid "freezing"
                        val diff = kotlin.math.abs(displayLevel - currentLevels[chId])
                        if (displayLevel == 0f && currentLevels[chId] > 0f || diff > 0.001f) {
                            currentLevels[chId] = displayLevel
                            anyLevelChanged = true
                        }
                    }
                }

                if (anyLevelChanged) {
                    _channelLevels.value = currentLevels
                }

                // Master Peak level accumulation
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
                
                val masterDb = faderToDb(_masterVolume.value)
                val masterGain = dbToGain(masterDb)
                
                val totalMasterEnergy = peakEnergyAccumulator * masterGain
                val masterCurrentDb = gainToDb(totalMasterEnergy)
                val masterDisplayLevel = dbToLevel(masterCurrentDb)
                
                // Consistency check to avoid master freezing
                val masterDiff = kotlin.math.abs(_masterLevel.value - masterDisplayLevel)
                if (masterDisplayLevel == 0f && _masterLevel.value > 0f || masterDiff > 0.001f) {
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