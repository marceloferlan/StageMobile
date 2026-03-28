package com.marceloferlan.stagemobile.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.marceloferlan.stagemobile.audio.engine.AudioEngine
import com.marceloferlan.stagemobile.audio.engine.DummyAudioEngine
import com.marceloferlan.stagemobile.audio.engine.FluidSynthEngine
import com.marceloferlan.stagemobile.audio.AudioDeviceState
import com.marceloferlan.stagemobile.domain.model.InstrumentChannel
import com.marceloferlan.stagemobile.utils.SystemResourceMonitor
import com.marceloferlan.stagemobile.midi.MidiConnectionManager
import com.marceloferlan.stagemobile.data.SettingsRepository
import com.marceloferlan.stagemobile.utils.UiUtils
import com.marceloferlan.stagemobile.domain.model.SoundFontMetadata
import com.marceloferlan.stagemobile.domain.model.Sf2Preset
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.provider.OpenableColumns
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.log10
import kotlin.random.Random
import com.marceloferlan.stagemobile.midi.MidiLearnMapping
import com.marceloferlan.stagemobile.midi.MidiLearnTarget
import com.marceloferlan.stagemobile.midi.MidiLearnTargetInfo
import com.marceloferlan.stagemobile.domain.model.DSPEffectInstance
import com.marceloferlan.stagemobile.domain.model.DSPEffectType
import com.marceloferlan.stagemobile.domain.model.DSPParamType
import java.util.UUID

class MixerViewModel : ViewModel() {
    /**
     * UI focused model to represent a SoundFont and its local availability status.
     */
    data class SoundFontListItem(
        val metadata: SoundFontMetadata,
        val isLocal: Boolean
    )


    companion object {
        private const val TAG = "MixerViewModel"
        const val MASTER_CHANNEL_ID = -1
        const val GLOBAL_CHANNEL_ID = -2
    }

    private var _audioEngine: AudioEngine = DummyAudioEngine()
    val audioEngine: AudioEngine get() = _audioEngine
    val fluidEngine: FluidSynthEngine? get() = _audioEngine as? FluidSynthEngine
    private var midiManager: MidiConnectionManager? = null
    private var deviceAudioManager: com.marceloferlan.stagemobile.audio.DeviceAudioManager? = null
    private var settingsRepo: SettingsRepository? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var appContext: Context? = null

    // --- State Flows ---

    private val _channels = MutableStateFlow<List<InstrumentChannel>>(emptyList())
    val channels: StateFlow<List<InstrumentChannel>> = _channels

    private val _midiDeviceConnected = MutableStateFlow(false)
    val midiDeviceConnected: StateFlow<Boolean> = _midiDeviceConnected

    private val _midiDeviceName = MutableStateFlow("")
    val midiDeviceName: StateFlow<String> = _midiDeviceName

    private val _availableMidiDevices = MutableStateFlow<List<com.marceloferlan.stagemobile.midi.MidiDeviceState>>(emptyList())
    val availableMidiDevices: StateFlow<List<com.marceloferlan.stagemobile.midi.MidiDeviceState>> = _availableMidiDevices

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

    private val _isDspMasterBypass = MutableStateFlow(false)
    val isDspMasterBypass: StateFlow<Boolean> = _isDspMasterBypass.asStateFlow()

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

    private val _channelLevels = Array(16) { MutableStateFlow(0f) }
    val channelLevels = _channelLevels.map { it.asStateFlow() }

    // Tracks the raw values from C++ to detect "New Energy" (Impulses)
    private val lastNativeLevels = FloatArray(16)

    private val _masterLevel = MutableStateFlow(0f)
    val masterLevel: StateFlow<Float> = _masterLevel

    private val activeNotesCount = ConcurrentHashMap<Int, Int>()
    private val channelInternalLevels = ConcurrentHashMap<Int, Float>()
    private val channelLastNoteTime = ConcurrentHashMap<Int, Long>()
    private var isPeakPollingSuspended = false
    private var logClearJob: Job? = null

    private val _lastSystemEvent = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val lastSystemEvent: SharedFlow<String> = _lastSystemEvent.asSharedFlow()
    
    private var isReinitializing = false // Proteção contra reentrância no motor de áudio

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // --- Set Stage States ---
    private val _activeSetStageName = MutableStateFlow<String?>(null)
    val activeSetStageName: StateFlow<String?> = _activeSetStageName.asStateFlow()

    private val _activeSetStageId = MutableStateFlow<String?>(null)
    val activeSetStageId: StateFlow<String?> = _activeSetStageId.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()
    
    private val _currentViewingBank = MutableStateFlow(1)
    val currentViewingBank: StateFlow<Int> = _currentViewingBank.asStateFlow()

    // Tracking active location
    private var activeBankId: Int? = null
    private var activeSlotId: Int? = null

    var setStageRepo: com.marceloferlan.stagemobile.data.SetStageRepository? = null
    var soundFontRepo: com.marceloferlan.stagemobile.data.SoundFontRepository? = null

    private val _availableSoundFonts = MutableStateFlow<List<SoundFontMetadata>>(emptyList())
    // UI list item identifying local presence
    val availableSoundFonts = _availableSoundFonts.map { list ->
        list.map { metadata ->
            val isLocal = soundFontRepo?.exists(metadata.fileName) ?: false
            SoundFontListItem(metadata, isLocal)
        }
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

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

    // Cache: Maps SF2 filename -> sfId already loaded in FluidSynth
    private val loadedSf2Cache = mutableMapOf<String, Int>() // Cache SoundFont Name -> sfId
    private val sf2UriMap = mutableMapOf<String, Uri>() // Cache SoundFont Name -> Original Uri
    // nextId removed, using slot-based IDs (0-15)

    // --- MIDI Learn State ---
    private val _isMidiLearnActive = MutableStateFlow(false)
    val isMidiLearnActive: StateFlow<Boolean> = _isMidiLearnActive.asStateFlow()

    private val _midiLearnTarget = MutableStateFlow<MidiLearnTargetInfo?>(null)
    val midiLearnTarget: StateFlow<MidiLearnTargetInfo?> = _midiLearnTarget.asStateFlow()

    private val _midiLearnMappings = MutableStateFlow<List<MidiLearnMapping>>(emptyList())
    val midiLearnMappings: StateFlow<List<MidiLearnMapping>> = _midiLearnMappings.asStateFlow()

    private val _midiLearnFeedback = MutableStateFlow<String?>(null)
    val midiLearnFeedback: StateFlow<String?> = _midiLearnFeedback.asStateFlow()

    private val _masterDspEffects = MutableStateFlow<List<DSPEffectInstance>>(createDefaultMasterEffects())
    val masterDspEffects: StateFlow<List<DSPEffectInstance>> = _masterDspEffects.asStateFlow()

    private var feedbackJob: Job? = null

    // --- Fast Path Cache for Latency Zero ---
    private var armedChannelsCache: List<InstrumentChannel> = emptyList()

    private var nextId = 7

    init {
        // Initial set of channels (7 default)
        val initialChannels = (1..7).map { i ->
            InstrumentChannel(
                id = i - 1, 
                name = "Nenhum SF2", 
                program = 0,
                dspEffects = createDefaultEffects()
            )
        }
        _channels.value = initialChannels
        rebuildArmedChannelsCache()
        startPeakMeterUpdate()
    }

    private fun createDefaultEffects(): List<DSPEffectInstance> {
        return listOf(
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.HPF, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.HPF)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.LPF, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.LPF)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.COMPRESSOR, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.COMPRESSOR)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.EQ_PARAMETRIC, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.EQ_PARAMETRIC)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.CHORUS, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.CHORUS)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.TREMOLO, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.TREMOLO)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.DELAY, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.DELAY)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.REVERB, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.REVERB)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.LIMITER, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.LIMITER)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.REVERB_SEND, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.REVERB_SEND))
        )
    }

    private fun createDefaultMasterEffects(): List<DSPEffectInstance> {
        return listOf(
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.EQ_PARAMETRIC, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.EQ_PARAMETRIC)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.COMPRESSOR, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.COMPRESSOR)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.DELAY, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.DELAY)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.REVERB, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.REVERB)),
            DSPEffectInstance(id = UUID.randomUUID().toString(), type = DSPEffectType.LIMITER, isEnabled = false, params = getDefaultParamsFor(DSPEffectType.LIMITER))
        )
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
            MidiLearnTarget.DSP_PARAM -> "DSP"
            MidiLearnTarget.TAP_DELAY -> "TAP"
            MidiLearnTarget.SET_STAGE_SLOT -> "Slot"
            MidiLearnTarget.NEXT_BANK -> "Bank+"
            MidiLearnTarget.PREVIOUS_BANK -> "Bank-"
            MidiLearnTarget.FAVORITE_SET -> "Fav"
        }
        val chLabel = (channelId + 1).toString().padStart(2, '0')
        Log.i(TAG, "MIDI Learn target: $label CH $chLabel — waiting for CC...")
    }

    fun selectDspLearnTarget(channelId: Int, effectId: String, paramId: Int) {
        if (!_isMidiLearnActive.value) return
        _midiLearnTarget.value = MidiLearnTargetInfo(MidiLearnTarget.DSP_PARAM, channelId, effectId, paramId)
        val chLabel = if (channelId == MASTER_CHANNEL_ID) "MASTER" else (channelId + 1).toString().padStart(2, '0')
        Log.i(TAG, "MIDI Learn target: DSP_PARAM ($effectId, Param $paramId) CH $chLabel — waiting for CC...")
    }

    private fun completeMidiLearn(deviceName: String, ccNumber: Int, midiChannel: Int) {
        val target = _midiLearnTarget.value ?: return
        val newMapping = MidiLearnMapping(
            target = target.target,
            channelId = target.channelId,
            ccNumber = ccNumber,
            midiChannel = midiChannel,
            deviceName = deviceName,
            effectId = target.effectId,
            paramId = target.paramId,
            slotIndex = target.slotIndex
        )

        // Add new mapping (allow multiple CCs per control), but prevent exact duplicate
        val alreadyExists = _midiLearnMappings.value.any {
            it.target == newMapping.target && it.channelId == newMapping.channelId &&
            it.ccNumber == newMapping.ccNumber && it.midiChannel == newMapping.midiChannel &&
            it.effectId == newMapping.effectId && it.paramId == newMapping.paramId
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
            MidiLearnTarget.DSP_PARAM -> "Param ${target.paramId}"
            MidiLearnTarget.TAP_DELAY -> "TAP"
            MidiLearnTarget.SET_STAGE_SLOT -> "Slot ${target.slotIndex}"
            MidiLearnTarget.NEXT_BANK -> "Bank+"
            MidiLearnTarget.PREVIOUS_BANK -> "Bank-"
            MidiLearnTarget.FAVORITE_SET -> "Fav Set"
        }
        val chLabel = when (target.channelId) {
            MASTER_CHANNEL_ID -> "MASTER"
            GLOBAL_CHANNEL_ID -> "GLOBAL"
            else -> "CH ${(target.channelId + 1).toString().padStart(2, '0')}"
        }
        val msg = "CC $ccNumber → $label $chLabel"
        _midiLearnFeedback.value = msg
        updateSystemEvent(msg)
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
        val mappings: List<MidiLearnMapping>,
        val slotIndex: Int? = null
    )
    private val _pendingUnmap = MutableStateFlow<PendingUnmap?>(null)
    val pendingUnmap: StateFlow<PendingUnmap?> = _pendingUnmap.asStateFlow()

    fun requestUnmap(target: MidiLearnTarget, channelId: Int, slotIndex: Int? = null) {
        val mappings = _midiLearnMappings.value.filter { 
            it.target == target && it.channelId == channelId && (slotIndex == null || it.slotIndex == slotIndex)
        }
        if (mappings.isEmpty()) return
        _pendingUnmap.value = PendingUnmap(target, channelId, mappings, slotIndex)
    }

    fun confirmUnmap(mapping: MidiLearnMapping) {
        val updated = _midiLearnMappings.value.filter {
            !(it.target == mapping.target && it.channelId == mapping.channelId &&
              it.ccNumber == mapping.ccNumber && it.midiChannel == mapping.midiChannel &&
              it.effectId == mapping.effectId && it.paramId == mapping.paramId)
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

    fun navBank(delta: Int) {
        val next = _currentViewingBank.value + delta
        if (next in 1..10) {
            _currentViewingBank.value = next
        }
    }

    fun updateViewingBank(bankId: Int) {
        if (bankId in 1..10) {
            _currentViewingBank.value = bankId
        }
    }

    private fun triggerMidiSetStageLoad(bankId: Int, slotId: Int) {
        appContext?.let { ctx ->
            loadSetStage(ctx, bankId, slotId)
        }
    }

    fun selectSetStageLearnTarget(slotIndex: Int) {
        if (!_isMidiLearnActive.value) return
        _midiLearnTarget.value = MidiLearnTargetInfo(
            target = MidiLearnTarget.SET_STAGE_SLOT,
            channelId = GLOBAL_CHANNEL_ID,
            slotIndex = slotIndex
        )
        Log.i(TAG, "MIDI Learn target: Set Stage Slot $slotIndex")
    }

    fun selectBankNavLearnTarget(isNext: Boolean) {
        if (!_isMidiLearnActive.value) return
        _midiLearnTarget.value = MidiLearnTargetInfo(
            target = if (isNext) MidiLearnTarget.NEXT_BANK else MidiLearnTarget.PREVIOUS_BANK,
            channelId = GLOBAL_CHANNEL_ID
        )
        Log.i(TAG, "MIDI Learn target: Bank ${if (isNext) "Next" else "Prev"}")
    }

    fun requestUnmapSetStage(slotIndex: Int) {
        requestUnmap(MidiLearnTarget.SET_STAGE_SLOT, GLOBAL_CHANNEL_ID, slotIndex)
    }

    fun requestUnmapBankNav(isNext: Boolean) {
        requestUnmap(if (isNext) MidiLearnTarget.NEXT_BANK else MidiLearnTarget.PREVIOUS_BANK, GLOBAL_CHANNEL_ID)
    }

    fun selectFavoriteSetLearnTarget() {
        if (!_isMidiLearnActive.value) return
        _midiLearnTarget.value = MidiLearnTargetInfo(
            target = MidiLearnTarget.FAVORITE_SET,
            channelId = GLOBAL_CHANNEL_ID
        )
        Log.i(TAG, "MIDI Learn target: Favorite Set")
    }

    fun requestUnmapFavoriteSet() {
        requestUnmap(MidiLearnTarget.FAVORITE_SET, GLOBAL_CHANNEL_ID)
    }

    fun getMappingsForControl(target: MidiLearnTarget, channelId: Int): List<MidiLearnMapping> {
        return _midiLearnMappings.value.filter { it.target == target && it.channelId == channelId }
    }

    // --- Audio Engine ---
    
    fun initSettings(context: Context) {
        val isTablet = UiUtils.isTablet(context)
        if (settingsRepo == null) {
            val repo = SettingsRepository(context)
            val setsRepo = com.marceloferlan.stagemobile.data.SetStageRepository(context)
            settingsRepo = repo
            setStageRepo = setsRepo
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
        _audioEngine.setInterpolation(_interpolationMethod.value)
        _audioEngine.setPolyphony(_maxPolyphony.value)
        _audioEngine.setMasterLimiter(_isMasterLimiterEnabled.value)

        // Add 8th channel if on Tablet and we are at default 7 channels
        if (isTablet && _channels.value.size == 7) {
            val eighthChannel = InstrumentChannel(7, "Nenhum SF2", program = 0)
            _channels.value = _channels.value + eighthChannel
            nextId = 8 // Ensure next manual Add Channel (+ CH) starts at 9 (id 8)
        }
        
        _isReady.value = true
        startPeakMeterUpdate()
        Log.i(TAG, "System initialization COMPLETE (isReady=true)")
    }

    fun isSoundFontInUse(fileName: String): Boolean {
        val repo = setStageRepo ?: return false
        for (bankId in 1..10) {
            val sets = repo.getSetStagesForBank(bankId)
            for (stage in sets.values) {
                if (stage.channels.any { it.soundFont == fileName }) return true
            }
        }
        return false
    }

    fun renameSoundFont(metadata: com.marceloferlan.stagemobile.domain.model.SoundFontMetadata, newName: String) {
        scope.launch(Dispatchers.IO) {
            val result = soundFontRepo?.renameSoundFont(metadata, newName)
            if (result?.isSuccess == true) {
                updateSystemEvent("SoundFont renomeado para: $newName")
            } else {
                updateSystemEvent("Erro ao renomear: ${result?.exceptionOrNull()?.message}")
            }
        }
    }

    fun openPresetSelector(channelId: Int, sfId: Int, sf2Name: String) {
        if (sfId < 0) return
        val presets = _audioEngine.getPresets(sfId)
        _pendingPresetSelection.value = PendingPresetSelection(
            channelId = channelId,
            sfId = sfId,
            sf2Name = sf2Name,
            presets = presets,
            isInitialLoad = false
        )
    }

    fun loadSoundFontFromInternal(channelId: Int, metadata: com.marceloferlan.stagemobile.domain.model.SoundFontMetadata) {
        val context = appContext ?: run {
            Log.e(TAG, "FAILED to load SF2: appContext is NULL")
            return
        }
        val filePath = soundFontRepo?.getFilePath(metadata.fileName) ?: run {
            Log.e(TAG, "FAILED to load SF2: soundFontRepo is NULL or cannot resolve path for ${metadata.fileName}. [Repository Status: ${if (soundFontRepo == null) "NULL" else "Active"}]")
            return
        }
        val sf2File = File(filePath)
        Log.i(TAG, "Checking physical file for SF2: $filePath")
        if (!sf2File.exists()) {
            val errorMsg = "Arquivo '${metadata.fileName}' não encontrado localmente. Importe-o novamente na tela de Manutenção."
            Log.e(TAG, "FAILED to load SF2: $errorMsg")
            updateSystemEvent(errorMsg)
            return
        }

        scope.launch(Dispatchers.IO) {
            isPeakPollingSuspended = true
            try {
                Log.i(TAG, ">>> STARTING SF2 LOAD: ${metadata.fileName} for Channel $channelId")

                val cachedSfId = loadedSf2Cache[metadata.fileName]
                val sfId: Int
                
                if (cachedSfId != null && cachedSfId >= 0) {
                    sfId = cachedSfId
                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(name = metadata.fileName, soundFont = metadata.fileName, sfId = sfId) else it
                    })
                } else {
                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(name = "Carregando...", soundFont = "Carregando ${metadata.fileName}...") else it
                    })
                    
                    Log.i(TAG, "JNI: Calling _audioEngine.loadSoundFont with path: ${sf2File.absolutePath}")
                    sfId = _audioEngine.loadSoundFont(sf2File.absolutePath)
                    Log.i(TAG, "JNI Result: loadSoundFont returned sfId=$sfId")

                    if (sfId < 0) {
                        Log.e(TAG, "JNI ERROR: _audioEngine.loadSoundFont failed (returned $sfId). Engine status: ${_audioEngine.isInitialized}")
                        updateChannels(_channels.value.map {
                            if (it.id == channelId) it.copy(soundFont = null) else it
                        })
                        return@launch
                    }
                    
                    loadedSf2Cache[metadata.fileName] = sfId
                    (audioEngine as? FluidSynthEngine)?.warmUpChannel(channelId)
                    
                    // CRITICAL FIX: Persist sfId to the channel state before dialog
                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(name = metadata.fileName, soundFont = metadata.fileName, sfId = sfId) else it
                    })
                }

                val presets = audioEngine.getPresets(sfId)
                
                if (presets.size <= 1) {
                    val defaultBank = presets.firstOrNull()?.bank ?: 0
                    val defaultProgram = presets.firstOrNull()?.program ?: 0
                    val presetName = presets.firstOrNull()?.name
                    val displayName = if (presetName != null) "${metadata.fileName} [$presetName]" else metadata.fileName

                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(
                            name = displayName,
                            soundFont = displayName,
                            sfId = sfId,
                            bank = defaultBank,
                            program = defaultProgram
                        ) else it
                    })
                    _audioEngine.programSelect(channelId, sfId, defaultBank, defaultProgram)
                    Log.i(TAG, "SF2 auto-selected: ch=$channelId, sfId=$sfId, bank=$defaultBank, prog=$defaultProgram")
                } else {
                    _pendingPresetSelection.value = PendingPresetSelection(
                        channelId = channelId,
                        sfId = sfId,
                        sf2Name = metadata.fileName,
                        presets = presets,
                        isInitialLoad = true
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "FATAL ERROR during SF2 load for ${metadata.fileName}: ${e.message}", e)
                updateChannels(_channels.value.map {
                    if (it.id == channelId) it.copy(soundFont = "Erro: ${e.message}") else it
                })
            } finally {
                isPeakPollingSuspended = false
            }
        }
    }

    fun loadSoundFontForChannel(context: Context, channelId: Int, uri: Uri, targetBank: Int? = null, targetProgram: Int? = null) {
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
                sf2UriMap[sf2FileName] = uri
                val cachedSfId = loadedSf2Cache[sf2FileName]

                val sfId: Int
                if (cachedSfId != null && cachedSfId >= 0) {
                    // Cache HIT — Skip copy and load
                    Log.i(TAG, "SF2 Cache HIT for '$sf2FileName' → sfId=$cachedSfId")
                    sfId = cachedSfId
                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(soundFont = sf2FileName, sfId = sfId) else it
                    })
                } else {
                    // Cache MISS — Full load sequence
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open URI: $uri")

                    val outputFile = File(context.filesDir, "sf2_ch${channelId}_$sf2FileName")

                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(soundFont = "Carregando $sf2FileName...") else it
                    })

                    inputStream.use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    sfId = _audioEngine.loadSoundFont(outputFile.absolutePath)
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

                val defaultBank = presets.firstOrNull()?.bank ?: 0
                val defaultProgram = presets.firstOrNull()?.program ?: 0

                if (presets.size <= 1) {
                    // Single preset or empty → auto-select
                    val presetName = presets.firstOrNull()?.name
                    val displayName = if (presetName != null) "$sf2FileName [$presetName]" else sf2FileName

                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(
                            soundFont = displayName,
                            sfId = sfId,
                            bank = defaultBank,
                            program = defaultProgram
                        ) else it
                    })
                    _audioEngine.programSelect(channelId, sfId, defaultBank, defaultProgram)
                    _sf2Loaded.value = true
                    _sf2Name.value = sf2FileName
                    Log.i(TAG, "SF2 auto-selected: ch=$channelId, sfId=$sfId, bank=$defaultBank, prog=$defaultProgram")
                } else {
                    // Multiple presets → Playabilty Fallback (Auto-select first preset instantly)
                    // Garantir que a engine tenha som antes de confirmar
                    _audioEngine.programSelect(channelId, sfId, defaultBank, defaultProgram)

                    // Multiple presets → Open selector dialog
                    updateChannels(_channels.value.map {
                        if (it.id == channelId) it.copy(
                            soundFont = sf2FileName, 
                            sfId = sfId,
                            bank = defaultBank,
                            program = defaultProgram
                        ) else it
                    })
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

                // --- RESTORE TARGET PRESET IF PROVIDED ---
                if (targetBank != null && targetProgram != null) {
                    val presets = audioEngine.getPresets(sfId)
                    val hasPreset = presets.any { it.bank == targetBank && it.program == targetProgram }
                    
                    if (hasPreset) {
                        _audioEngine.programSelect(channelId, sfId, targetBank, targetProgram)
                        val presetName = presets.find { it.bank == targetBank && it.program == targetProgram }?.name
                        val displayName = if (presetName != null) "$sf2FileName [$presetName]" else sf2FileName
                        
                        updateChannels(_channels.value.map {
                            if (it.id == channelId) it.copy(
                                sfId = sfId,
                                soundFont = displayName,
                                bank = targetBank,
                                program = targetProgram
                            ) else it
                        })
                        Log.i(TAG, "Restored preset $targetBank:$targetProgram for $sf2FileName on ch $channelId")
                    }
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
                name = displayName,
                soundFont = displayName,
                bank = bank,
                program = program
            ) else it
        })

        _audioEngine.programSelect(channelId, sfId, bank, program)
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
            val presets = _audioEngine.getPresets(sfId)
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
        appContext = context.applicationContext
        val isTablet = UiUtils.isTablet(context)
        initSettings(context)
        
        if (soundFontRepo == null) {
            Log.i(TAG, "Initializing SoundFontRepository...")
            soundFontRepo = com.marceloferlan.stagemobile.data.SoundFontRepository(context)
            scope.launch {
                soundFontRepo!!.getSoundFonts().collect {
                    Log.i(TAG, "Repository: Updated availableSoundFonts list (Size: ${it.size})")
                    _availableSoundFonts.value = it
                }
            }
        }
        
        _activeMidiDevices.value = settingsRepo?.activeMidiDevices ?: emptySet()

        if (deviceAudioManager == null) {
            deviceAudioManager = com.marceloferlan.stagemobile.audio.DeviceAudioManager(context)
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

                    // Reactive ID Discovery & Auto-Switch
                    val targetName = savedName ?: devices.maxByOrNull { device ->
                        // Prioridade simplificada: BT > USB > Interna
                        when {
                            device.name.startsWith("Bluetooth") -> 100
                            device.name.startsWith("Externa") -> 80
                            else -> 0
                        }
                    }?.name
                    
                    if (targetName != null) {
                        val matchingDevice = devices.find { it.name == targetName }
                        
                        // Auto-Switch logic: se um dispositivo novo e de maior prioridade apareceu, muda mesmo que o atual seja funcional
                        val currentPriority = oldDevices.find { it.id == _selectedAudioDeviceId.value }?.let { d ->
                            if (d.name.startsWith("Bluetooth")) 100 else if (d.name.startsWith("Externa")) 80 else 0
                        } ?: -1
                        
                        val newPriority = if (matchingDevice?.name?.startsWith("Bluetooth") == true) 100 
                                         else if (matchingDevice?.name?.startsWith("Externa") == true) 80 else 0
                        
                        if (matchingDevice != null) {
                            if (matchingDevice.id != _selectedAudioDeviceId.value || newPriority > currentPriority) {
                                Log.i(TAG, ">>> AUTO-SWITCH TRIGGERED: ${matchingDevice.name} (Priority $newPriority)")
                                _selectedAudioDeviceId.value = matchingDevice.id
                                
                                if (audioEngine !is DummyAudioEngine) {
                                    reinitAudioEngine(context)
                                }
                            } else {
                                Log.d(TAG, "Device check: remains on ${matchingDevice.name} (ID ${_selectedAudioDeviceId.value})")
                            }
                        }
                    } else if (devices.isNotEmpty()) {
                        // Absolute fallback: pick first available device if no matching found
                        val firstId = devices.first().id
                        if (firstId != _selectedAudioDeviceId.value) {
                            _selectedAudioDeviceId.value = firstId
                            if (audioEngine !is DummyAudioEngine) reinitAudioEngine(context)
                        }
                    }
                }
            }

        // Override with Real audio engine if full context is provided
        if (_audioEngine is DummyAudioEngine) {
            _audioEngine = FluidSynthEngine(context)
            val deviceId = _selectedAudioDeviceId.value
            try {
                Log.i(TAG, "Initializing FluidSynth Engine (SR=${_sampleRate.value}, Buf=${_bufferSize.value}, Dev=$deviceId)")
                _audioEngine.initialize(_sampleRate.value, _bufferSize.value, deviceId)
                
                // CRITICAL: Synchronize initial effects created in init { }
                syncEffectsToEngine()
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: AudioEngine.initialize failed: ${e.message}", e)
            }
            
            startResourceMonitor(context)
            
            // Monitor de Saúde do Stream (Auto-Recovery)
            scope.launch {
                while (isActive) {
                    kotlinx.coroutines.delay(1000) // Mais agressivo: 1 segundo
                    val engine = _audioEngine
                    if (engine is FluidSynthEngine) {
                        val isDead = engine.isStreamDead()
                        val notInit = !engine.isInitialized
                        if ((isDead || notInit) && !isReinitializing) {
                            Log.w(TAG, "!!! HEALTH MONITOR ALERT: isDead=$isDead, notInit=$notInit !!!")
                            updateSystemEvent("Recuperando conexão de áudio...")
                            if (isDead) engine.resetStreamDead()
                            reinitAudioEngine(context)
                        }
                    }
                }
            }
            }
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
                                _audioEngine.noteOn(ch.id, transposedKey, applyVelocityCurve(velocity, ch.velocityCurve))
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
                                _audioEngine.noteOff(ch.id, transposedKey)
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
                            MidiLearnTarget.TAP_DELAY -> {
                                if (value > 64) {
                                    tapGlobalDelayTime()
                                }
                            }
                            MidiLearnTarget.SET_STAGE_SLOT -> {
                                if (value > 64 && mapping.slotIndex != null) {
                                    // Trigger Set Stage loading. 
                                    // We need to trigger this on the main/view-model scope, 
                                    // and we need a context. We can try to use a dummy or a stored one.
                                    // Better: fire a SharedFlow event that the Screen listens to, 
                                    // or store the context from the last initMidi.
                                    // For now, let's look for a stored context or use the repo if it's already set up.
                                    // Since it's relative, we use the current viewing bank.
                                    triggerMidiSetStageLoad(_currentViewingBank.value, mapping.slotIndex)
                                }
                            }
                            MidiLearnTarget.NEXT_BANK -> {
                                if (value > 64) navBank(1)
                            }
                            MidiLearnTarget.PREVIOUS_BANK -> {
                                if (value > 64) navBank(-1)
                            }
                            MidiLearnTarget.FAVORITE_SET -> {
                                if (value > 64) {
                                    // By default, let's say it loads Bank 1, Slot 1 (id 0)
                                    // or whatever was the first set stage ever saved.
                                    // For now, let's load Bank 1, Slot 1.
                                    triggerMidiSetStageLoad(1, 1)
                                }
                            }
                            MidiLearnTarget.DSP_PARAM -> {
                                if (mapping.effectId != null && mapping.paramId != null) {
                                    val ch = if (mapping.channelId == MASTER_CHANNEL_ID) null else _channels.value.find { it.id == mapping.channelId }
                                    val effectsList = if (mapping.channelId == MASTER_CHANNEL_ID) _masterDspEffects.value else ch?.dspEffects
                                    val effect = effectsList?.find { it.id == mapping.effectId }
                                    if (effect != null) {
                                        val paramType = effect.type.params.getOrNull(mapping.paramId)
                                        if (paramType != null) {
                                            val range = getDspParamRange(paramType, effect.type)
                                            val scaledValue = range.start + (range.endInclusive - range.start) * (value / 127f)
                                            updateEffectParam(mapping.channelId, mapping.effectId, mapping.paramId, scaledValue)
                                        }
                                    }
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
                                    _audioEngine.controlChange(chId, controller, sendValue)
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
                _audioEngine.noteOn(ch.id, transposedKey, 100)
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
            _audioEngine.noteOff(ch.id, transposedKey)
            triggerNoteOff(ch.id)
        }
    }

    fun playTestNoteOn(channelId: Int, note: Int, velocity: Int) {
        val ch = _channels.value.find { it.id == channelId } ?: return
        if (ch.sfId < 0) return // Ignore if no SF2 is loaded
        val finalShift = (ch.octaveShift + _globalOctaveShift.value) * 12 + 
                       ch.transposeShift + _globalTransposeShift.value
        val transposedKey = (note + finalShift).coerceIn(0, 127)
        _audioEngine.noteOn(channelId, transposedKey, applyVelocityCurve(velocity, ch.velocityCurve))
    }

    fun playTestNoteOff(channelId: Int, note: Int) {
        val ch = _channels.value.find { it.id == channelId } ?: return
        if (ch.sfId < 0) return
        val finalShift = (ch.octaveShift + _globalOctaveShift.value) * 12 + 
                       ch.transposeShift + _globalTransposeShift.value
        val transposedKey = (note + finalShift).coerceIn(0, 127)
        _audioEngine.noteOff(channelId, transposedKey)
    }

    // --- Channel Management ---

    fun addChannel(name: String) {
        val currentChannels = _channels.value
        if (currentChannels.size >= 16) {
            updateSystemEvent("Limite de 16 canais atingido")
            return
        }
        
        // Find first available hardware slot (0-15)
        val usedIds = currentChannels.map { it.id }.toSet()
        val availableId = (0..15).firstOrNull { it !in usedIds }
        
        if (availableId == null) {
            updateSystemEvent("Não há slots de áudio disponíveis")
            return
        }

        val newChannel = InstrumentChannel(
            id = availableId, 
            name = name,
            dspEffects = createDefaultEffects()
        )
        updateChannels(currentChannels + newChannel)
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
        _audioEngine.setVolume(channelId, faderToDb(finalLinearVolume))
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

    private fun updateChannel(channelId: Int, update: (com.marceloferlan.stagemobile.domain.model.InstrumentChannel) -> com.marceloferlan.stagemobile.domain.model.InstrumentChannel) {
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

    // --- Modular DSP Effect Management ---

    fun updateEffectParam(channelId: Int, effectId: String, paramId: Int, value: Float) {
        if (channelId == MASTER_CHANNEL_ID) {
            val currentEffects = _masterDspEffects.value
            val effectIdx = currentEffects.indexOfFirst { it.id == effectId }
            if (effectIdx >= 0) {
                val updatedEffects = currentEffects.toMutableList()
                val updatedParams = updatedEffects[effectIdx].params.toMutableMap()
                updatedParams[paramId] = value
                updatedEffects[effectIdx] = updatedEffects[effectIdx].copy(params = updatedParams)
                _masterDspEffects.value = updatedEffects
                
                _audioEngine.setEffectParam(MASTER_CHANNEL_ID, effectIdx, paramId, value)
            }
            return
        }

        updateChannel(channelId) { ch ->
            val effectIdx = ch.dspEffects.indexOfFirst { it.id == effectId }
            if (effectIdx >= 0) {
                val updatedEffects = ch.dspEffects.toMutableList()
                val effect = updatedEffects[effectIdx]
                val updatedParams = effect.params.toMutableMap()
                updatedParams[paramId] = value
                updatedEffects[effectIdx] = effect.copy(params = updatedParams)
                
                // Sync with Native Engine
                _audioEngine.setEffectParam(ch.id, effectIdx, paramId, value)
                
                ch.copy(dspEffects = updatedEffects)
            } else ch
        }
    }

    fun toggleEffect(channelId: Int, effectId: String, enabled: Boolean) {
        if (channelId == MASTER_CHANNEL_ID) {
            val currentEffects = _masterDspEffects.value
            val effectIdx = currentEffects.indexOfFirst { it.id == effectId }
            if (effectIdx >= 0) {
                val updatedEffects = currentEffects.toMutableList()
                updatedEffects[effectIdx] = updatedEffects[effectIdx].copy(isEnabled = enabled)
                _masterDspEffects.value = updatedEffects
                
                _audioEngine.setEffectEnabled(MASTER_CHANNEL_ID, effectIdx, enabled)
            }
            return
        }

        updateChannel(channelId) { ch ->
            val effectIdx = ch.dspEffects.indexOfFirst { it.id == effectId }
            if (effectIdx >= 0) {
                val updatedEffects = ch.dspEffects.toMutableList()
                updatedEffects[effectIdx] = updatedEffects[effectIdx].copy(isEnabled = enabled)
                
                // Sync with Native Engine
                _audioEngine.setEffectEnabled(ch.id, effectIdx, enabled)
                
                ch.copy(dspEffects = updatedEffects)
            } else ch
        }
    }

    private fun getDefaultParamsFor(type: DSPEffectType): Map<Int, Float> {
        return when (type) {
            DSPEffectType.EQ_PARAMETRIC -> mapOf(0 to 0f, 1 to 200f, 2 to 0f, 3 to 1000f, 4 to 1.0f, 5 to 0f, 6 to 5000f, 7 to 0f)
            DSPEffectType.HPF, DSPEffectType.LPF -> mapOf(0 to 1000f, 1 to 0.707f)
            DSPEffectType.DELAY -> mapOf(0 to 500f, 1 to 0.3f, 2 to 0.4f)
            DSPEffectType.REVERB -> mapOf(0 to 0.6f, 1 to 0.5f, 2 to 0.3f)
            DSPEffectType.CHORUS -> mapOf(0 to 0.5f, 1 to 0.2f, 2 to 0.5f) // Calibrated default: Rate 0.5Hz, Depth 20%, Mix 50%
            DSPEffectType.TREMOLO -> mapOf(0 to 4.5f, 1 to 0.6f, 2 to 0.5f)
            DSPEffectType.COMPRESSOR -> mapOf(0 to 0f, 1 to 2f, 2 to 1f, 3 to 1f, 4 to 0f, 5 to 0f, 6 to 1.0f)
            DSPEffectType.LIMITER -> mapOf(0 to -0.1f, 1 to 20f)
            DSPEffectType.REVERB_SEND -> mapOf(0 to 0f)
        }
    }

    private val tapTimes = mutableMapOf<String, MutableList<Long>>()
    private val globalTapTimes = mutableListOf<Long>()

    fun tapGlobalDelayTime() {
        // Optimization: Exit early if no delay effects are active anywhere
        val allDelays = _channels.value.flatMap { it.dspEffects } + _masterDspEffects.value
        val hasActiveDelay = allDelays.any { it.type == DSPEffectType.DELAY && it.isEnabled }
        if (!hasActiveDelay) return

        val now = System.currentTimeMillis()
        globalTapTimes.add(now)
        if (globalTapTimes.size > 4) globalTapTimes.removeAt(0)
        
        if (globalTapTimes.size >= 2) {
            val intervals = globalTapTimes.zipWithNext { a, b -> b - a }
            val avgMs = intervals.average().toFloat().coerceIn(10f, 2000f)
            
            // Apply to all channels
            _channels.value.forEach { ch ->
                ch.dspEffects.filter { it.type == DSPEffectType.DELAY }.forEach { effect ->
                    val paramIndex = effect.type.params.indexOf(DSPParamType.DELAY_TIME)
                    if (paramIndex != -1) {
                        updateEffectParam(ch.id, effect.id, paramIndex, avgMs)
                    }
                }
            }
            
            // Apply to Master
            _masterDspEffects.value.filter { it.type == DSPEffectType.DELAY }.forEach { effect ->
                val paramIndex = effect.type.params.indexOf(DSPParamType.DELAY_TIME)
                if (paramIndex != -1) {
                    updateEffectParam(MASTER_CHANNEL_ID, effect.id, paramIndex, avgMs)
                }
            }
        }
    }

    fun tapDelayTime(channelId: Int, effectId: String) {
        val now = System.currentTimeMillis()
        val list = tapTimes.getOrPut(effectId) { mutableListOf() }
        list.add(now)
        if (list.size > 4) list.removeAt(0)
        
        if (list.size >= 2) {
            val intervals = list.zipWithNext { a, b -> b - a }
            val avgMs = intervals.average().toFloat()
            
            // Find delay time param index dynamically
            val effect = if (channelId == MASTER_CHANNEL_ID) {
                _masterDspEffects.value.find { it.id == effectId }
            } else {
                _channels.value.find { it.id == channelId }?.dspEffects?.find { it.id == effectId }
            }
            
            val paramIndex = effect?.type?.params?.indexOf(DSPParamType.DELAY_TIME) ?: 0
            updateEffectParam(channelId, effectId, paramIndex, avgMs.coerceIn(10f, 2000f))
        }
    }

    fun resetEffectParams(channelId: Int, effectId: String) {
        if (channelId == MASTER_CHANNEL_ID) {
            val currentEffects = _masterDspEffects.value
            val effectIdx = currentEffects.indexOfFirst { it.id == effectId }
            if (effectIdx >= 0) {
                val effect = currentEffects[effectIdx]
                val defaults = getDefaultParamsFor(effect.type)
                
                // Native Sync (Update native for each param in the defaults)
                defaults.forEach { (pid, v) ->
                    audioEngine.setEffectParam(MASTER_CHANNEL_ID, effectIdx, pid, v)
                }
                
                // State Update (Single Batch)
                val updatedEffects = currentEffects.toMutableList()
                updatedEffects[effectIdx] = effect.copy(params = defaults)
                _masterDspEffects.value = updatedEffects
            }
            return
        }

        updateChannel(channelId) { ch ->
            val effectIdx = ch.dspEffects.indexOfFirst { it.id == effectId }
            if (effectIdx >= 0) {
                val effect = ch.dspEffects[effectIdx]
                val defaults = getDefaultParamsFor(effect.type)
                
                // Native Sync
                defaults.forEach { (pid, v) ->
                    audioEngine.setEffectParam(ch.id, effectIdx, pid, v)
                }
                
                // State Update (Batch)
                val updatedEffects = ch.dspEffects.toMutableList()
                updatedEffects[effectIdx] = effect.copy(params = defaults)
                ch.copy(dspEffects = updatedEffects)
            } else ch
        }
    }

    fun addEffectToChannel(channelId: Int, type: DSPEffectType) {
        val defaultParams = getDefaultParamsFor(type)
        if (channelId == MASTER_CHANNEL_ID) {
            val newEffect = DSPEffectInstance(
                id = java.util.UUID.randomUUID().toString(),
                type = type,
                params = defaultParams
            )
            val updatedEffects = (_masterDspEffects.value + newEffect).sortedBy { it.type.priority }
            _masterDspEffects.value = updatedEffects
            
            audioEngine.addEffect(MASTER_CHANNEL_ID, type.id)
            
            // Find the new index of this specific effect after sorting
            val newIdx = updatedEffects.indexOfFirst { it.id == newEffect.id }
            
            // Push default params to native using the correct index
            defaultParams.forEach { (pid, v) -> 
                _audioEngine.setEffectParam(MASTER_CHANNEL_ID, newIdx, pid, v)
            }
            return
        }

        updateChannel(channelId) { ch ->
            val newEffect = DSPEffectInstance(
                id = java.util.UUID.randomUUID().toString(),
                type = type,
                params = defaultParams
            )
            val updatedEffects = (ch.dspEffects + newEffect).sortedBy { it.type.priority }
            
            // Sync with Native Engine
            _audioEngine.addEffect(ch.id, type.id)
            
            // Find the new index of this specific effect after sorting
            val newIdx = updatedEffects.indexOfFirst { it.id == newEffect.id }
            
            // Push default params to native using the correct index
            defaultParams.forEach { (pid, v) ->
                _audioEngine.setEffectParam(ch.id, newIdx, pid, v)
            }
            
            ch.copy(dspEffects = updatedEffects)
        }
    }

    fun removeEffectFromChannel(channelId: Int, effectId: String) {
        if (channelId == MASTER_CHANNEL_ID) {
            val currentEffects = _masterDspEffects.value
            val effectIdx = currentEffects.indexOfFirst { it.id == effectId }
            if (effectIdx >= 0) {
                val updatedEffects = currentEffects.toMutableList()
                updatedEffects.removeAt(effectIdx)
                _masterDspEffects.value = updatedEffects
                
                _audioEngine.removeEffect(MASTER_CHANNEL_ID, effectIdx)
            }
            return
        }

        updateChannel(channelId) { ch ->
            val effectIdx = ch.dspEffects.indexOfFirst { it.id == effectId }
            if (effectIdx >= 0) {
                val updatedEffects = ch.dspEffects.toMutableList()
                updatedEffects.removeAt(effectIdx)
                
                // Sync with Native Engine
                _audioEngine.removeEffect(ch.id, effectIdx)
                
                ch.copy(dspEffects = updatedEffects)
            } else ch
        }
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
                _audioEngine.noteOff(channelId, key)
            }
            activeNotesCount[channelId] = 0
            channelInternalLevels[channelId] = 0f

            // REFERENCE COUNTING: Only unload from engine if NO other channel is using this sfId
            if (sfIdToUnload >= 0) {
                // Check other users EXCLUDING the current channel
                val otherUsers = _channels.value.any { it.id != channelId && it.sfId == sfIdToUnload }
                if (!otherUsers) {
                    Log.i(TAG, "Unloading last instance of sfId $sfIdToUnload from engine and cache")
                    _audioEngine.unloadSoundFont(sfIdToUnload)
                    
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
                    name = "Nenhum SF2",
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
            
                    // Clean up DSP effects safely
            val channel = _channels.value.find { it.id == channelId }
            channel?.dspEffects?.forEachIndexed { index, effect ->
                if (effect.isEnabled) {
                    _audioEngine.setEffectEnabled(channelId, index, false)
                }
            }
            _audioEngine.clearEffects(channelId)
            
            updateChannels(_channels.value.filter { it.id != channelId })
            
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

    fun toggleDspMasterBypass() {
        val newState = !_isDspMasterBypass.value
        _isDspMasterBypass.value = newState
        audioEngine.setDspMasterBypass(newState)
        Log.i(TAG, "DSP Master Bypass set to: $newState")
        updateSystemEvent("DSP Bypass: ${if (newState) "LIGADO" else "DESLIGADO"}")
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
        _lastSystemEvent.tryEmit(msg)
        Log.i(TAG, "System Event: $msg")
    }

    fun exitSystem() {
        Log.i(TAG, "=== SYSTEM SHUTDOWN INITIATED ===")
        try {
            // 1. Unload all SoundFonts
            _channels.value.forEach { ch ->
                if (ch.sfId >= 0) {
                    _audioEngine.unloadSoundFont(ch.sfId)
                }
            }
            
            // 2. Destroy Audio Engine (Oboe/AAudio)
            _audioEngine.destroy()
            
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
        if (_audioEngine is DummyAudioEngine || isReinitializing) return
        
        isReinitializing = true
        scope.launch(Dispatchers.IO) {
            isPeakPollingSuspended = true
            try {
                // Delay de segurança maior para dispositivos Bluetooth/USB (liberação de hardware)
                kotlinx.coroutines.delay(1000)
                
                Log.i(TAG, ">>> REINIT AUDIO ENGINE START (Buffer=${_bufferSize.value}, SR=${_sampleRate.value})")
                
                _audioEngine.destroy()
                val deviceId = _selectedAudioDeviceId.value
                val primarySampleRate = _sampleRate.value
                
                _audioEngine.initialize(primarySampleRate, _bufferSize.value, deviceId)
                
                // Fallback de Sample Rate: se 48kHz falhou e não estamos no speaker interno, tenta 44k1
                if (!_audioEngine.isInitialized && primarySampleRate != 44100) {
                    Log.w(TAG, "Primary Sample Rate ($primarySampleRate) failed. Trying fallback 44100Hz...")
                    _audioEngine.initialize(44100, _bufferSize.value, deviceId)
                    if (_audioEngine.isInitialized) {
                        Log.i(TAG, "Fallback to 44100Hz SUCCESSFUL")
                        // Atualiza o StateFlow e o Repository para persistir a nova taxa funcional
                        _sampleRate.value = 44100
                        settingsRepo?.sampleRate = 44100
                    }
                }

                if (!_audioEngine.isInitialized) {
                    updateSystemEvent("Falha crítica ao abrir áudio no dispositivo selecionado.")
                    return@launch
                }
                
                // Re-apply engine globals
                _audioEngine.setInterpolation(_interpolationMethod.value)
                _audioEngine.setPolyphony(_maxPolyphony.value)
                _audioEngine.setMasterLimiter(_isMasterLimiterEnabled.value)
                
                // CRITICAL: Clear cache because the new synth instance has new sfIds
                loadedSf2Cache.clear()
                activeNotesCount.clear() // Clear UI ghost notes since engine was destroyed
                
                // Reload SF2s silently for all configured channels
                _channels.value.forEach { ch ->
                    if (ch.sfId >= 0 && ch.soundFont != null) {
                        // CRITICAL: Extract base name (remove preset suffix [ ... ])
                        val baseName = ch.soundFont!!.substringBefore(" [")
                        val restoredFile = File(context.filesDir, "sf2_ch${ch.id}_$baseName")
                        
                        Log.d(TAG, "Restoring channel ${ch.id}: looking for $restoredFile")
                        
                        if (restoredFile.exists()) {
                            val newSfId = _audioEngine.loadSoundFont(restoredFile.absolutePath)
                            if (newSfId >= 0) {
                                (_audioEngine as? FluidSynthEngine)?.warmUpChannel(ch.id)
                                _audioEngine.programSelect(ch.id, newSfId, ch.bank, ch.program)
                                
                                // Re-populate cache for future hits
                                loadedSf2Cache[baseName] = newSfId
                                
                                // Update internal state with new sfId
                                updateChannels(_channels.value.map {
                                    if (it.id == ch.id) it.copy(sfId = newSfId) else it
                                })
                                Log.i(TAG, "Channel ${ch.id} restored successfully (sfId=$newSfId)")
                            } else {
                                Log.e(TAG, "Failed to reload soundfont for channel ${ch.id}")
                            }
                        } else {
                            Log.w(TAG, "Restoration failed: file not found for channel ${ch.id} ($baseName)")
                            // Reset sfId if file is gone to avoid ghost states
                            updateChannels(_channels.value.map {
                                if (it.id == ch.id) it.copy(sfId = -1) else it
                            })
                        }
                    }
                }
                
                syncEffectsToEngine()
                
                updateSystemEvent("Motor Reiniciado (${_bufferSize.value} samples)")
                Log.i(TAG, "Audio Engine re-initialization COMPLETE")
            } catch (e: Exception) {
                Log.e(TAG, "Error during Audio Engine re-initialization: ${e.message}", e)
                updateSystemEvent("Erro ao reiniciar motor de áudio")
            } finally {
                isPeakPollingSuspended = false
                isReinitializing = false
            }
        }
    }

    // --- Utility ---

    private fun syncEffectsToEngine() {
        Log.i(TAG, "Syncing all DSP effects to engine...")
        
        // 1. Master Racks
        audioEngine.clearEffects(MASTER_CHANNEL_ID)
        _masterDspEffects.value.forEachIndexed { index, effect ->
            audioEngine.addEffect(MASTER_CHANNEL_ID, effect.type.id)
            audioEngine.setEffectEnabled(MASTER_CHANNEL_ID, index, effect.isEnabled)
            effect.params.forEach { (paramId, value) ->
                audioEngine.setEffectParam(MASTER_CHANNEL_ID, index, paramId, value)
            }
        }
        
        // 2. Channel Racks
        _channels.value.forEach { ch ->
            audioEngine.clearEffects(ch.id)
            ch.dspEffects.forEachIndexed { index, effect ->
                audioEngine.addEffect(ch.id, effect.type.id)
                audioEngine.setEffectEnabled(ch.id, index, effect.isEnabled)
                effect.params.forEach { (paramId, value) ->
                    audioEngine.setEffectParam(ch.id, index, paramId, value)
                }
            }
        }
    }

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
                _audioEngine.getChannelLevels(levels)

                // Early-exit: if silence and already zeroed, avoid recomposition
                val hasAnyNativeSignal = levels.any { it > 0.0001f }
                val hasAnyInternalSignal = channelInternalLevels.values.any { it > 0.001f }
                if (!hasAnyNativeSignal && !hasAnyInternalSignal) {
                    if (_masterLevel.value > 0f) {
                        _masterLevel.value = 0f
                        _channelLevels.forEach { it.value = 0f }
                        channelInternalLevels.clear()
                    }
                    continue
                }

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
                        val currentLev = _channelLevels[chId].value
                        val diff = kotlin.math.abs(displayLevel - currentLev)
                        if (displayLevel == 0f && currentLev > 0f || diff > 0.001f) {
                            _channelLevels[chId].value = displayLevel
                        }
                    }
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

    private fun getDspParamRange(param: DSPParamType, effectType: DSPEffectType): ClosedFloatingPointRange<Float> {
        return when (param) {
            DSPParamType.LOW_FREQ, DSPParamType.MID_FREQ, DSPParamType.HIGH_FREQ, DSPParamType.CUTOFF_FREQ -> 20f..20000f
            DSPParamType.LOW_GAIN, DSPParamType.MID_GAIN, DSPParamType.HIGH_GAIN, DSPParamType.OUTPUT_GAIN, DSPParamType.MAKEUP_GAIN -> -15f..15f
            DSPParamType.THRESHOLD -> if (effectType == DSPEffectType.LIMITER) -12f..0f else -40f..0f
            DSPParamType.MID_Q, DSPParamType.RESONANCE -> 0.5f..4.0f
            DSPParamType.DELAY_TIME -> 20f..1000f
            DSPParamType.DELAY_FEEDBACK -> 0f..0.75f
            DSPParamType.MOD_RATE -> if (effectType == DSPEffectType.CHORUS) 0.1f..3.0f else 0.1f..10.0f
            DSPParamType.ATTACK -> 1f..100f
            DSPParamType.RELEASE -> if (effectType == DSPEffectType.LIMITER) 1f..500f else 20f..1000f
            DSPParamType.RATIO -> 1f..10f
            DSPParamType.KNEE -> 0f..12f
            else -> 0f..1f
        }
    }

    // --- Set Stage Integration ---
    fun markUnsavedChanges() {
        if (!_hasUnsavedChanges.value) {
            _hasUnsavedChanges.value = true
        }
    }

    fun saveCurrentSetStage(name: String) {
        val bank = activeBankId ?: 1
        val slot = activeSlotId ?: 1
        saveCurrentSetStage(bank, slot, name)
    }

    fun saveCurrentSetStage(bankId: Int, slotId: Int, name: String) {
        val stage = com.marceloferlan.stagemobile.domain.model.SetStage(
            id = "${bankId}_${slotId}",
            bankId = bankId,
            slotId = slotId,
            name = name,
            channels = _channels.value,
            masterVolume = _masterVolume.value,
            globalOctaveShift = _globalOctaveShift.value,
            globalTransposeShift = _globalTransposeShift.value,
            isMasterLimiterEnabled = _isMasterLimiterEnabled.value,
            isDspMasterBypass = _isDspMasterBypass.value
        )
        setStageRepo?.saveSetStage(stage)
        activeBankId = bankId
        activeSlotId = slotId
        _activeSetStageName.value = name
        _activeSetStageId.value = "${bankId}_$slotId"
        _hasUnsavedChanges.value = false
        Log.i(TAG, "Set Stage salvo: $name (Banco $bankId, Slot $slotId)")
        updateSystemEvent("SET STAGE SALVO: $name")
    }

    fun saveAsNewSetStage(name: String) {
        val nextSlot = setStageRepo?.findNextAvailableSlot()
        if (nextSlot != null) {
            saveCurrentSetStage(nextSlot.first, nextSlot.second, name)
            updateSystemEvent("SET STAGE SALVO: $name")
        } else {
            Log.e(TAG, "Não foi possível encontrar um slot livre para salvar o Set Stage")
            updateSystemEvent("ERRO: MÁXIMO DE SLOTS ATINGIDO (150/150)")
        }
    }

    fun loadSetStage(context: Context, bankId: Int, slotId: Int) {
        scope.launch(Dispatchers.Default) {
            val stage = setStageRepo?.loadSetStage(bankId, slotId) ?: return@launch
            
            withContext(Dispatchers.Main) {
                activeBankId = bankId
                activeSlotId = slotId
                _activeSetStageName.value = stage.name
                _activeSetStageId.value = "${bankId}_$slotId"
                
                _masterVolume.value = stage.masterVolume
                _globalOctaveShift.value = stage.globalOctaveShift
                _globalTransposeShift.value = stage.globalTransposeShift
                _isMasterLimiterEnabled.value = stage.isMasterLimiterEnabled
                _isDspMasterBypass.value = stage.isDspMasterBypass
            }

            audioEngine.setMasterLimiter(stage.isMasterLimiterEnabled)
            updateMasterVolume(stage.masterVolume)

            stage.channels.forEach { ch ->
                val sf2Name = ch.soundFont
                if (sf2Name != null) {
                    val currentSfId = loadedSf2Cache[sf2Name]
                    if (currentSfId != null) {
                        val presets = audioEngine.getPresets(currentSfId)
                        val hasPreset = presets.any { it.bank == ch.bank && it.program == ch.program }
                        if (hasPreset) {
                            audioEngine.programSelect(ch.id, currentSfId, ch.bank, ch.program)
                        } else {
                            presets.firstOrNull()?.let { first ->
                                audioEngine.programSelect(ch.id, currentSfId, first.bank, first.program)
                            }
                        }
                    } else {
                        // Tenta carregar do repositório interno primeiro
                        val internalPath = soundFontRepo?.getFilePath(sf2Name)
                        val internalFile = internalPath?.let { File(it) }
                        
                        if (internalFile?.exists() == true) {
                            // Carregamento direto do arquivo interno (Zero Lag / Portável)
                            scope.launch(Dispatchers.IO) {
                                val sfId = _audioEngine.loadSoundFont(internalPath)
                                if (sfId >= 0) {
                                    loadedSf2Cache[sf2Name] = sfId
                                    audioEngine.programSelect(ch.id, sfId, ch.bank, ch.program)
                                }
                            }
                        } else {
                            // Fallback para URI legado
                            sf2UriMap[sf2Name]?.let { uri ->
                                loadSoundFontForChannel(context, ch.id, uri, targetBank = ch.bank, targetProgram = ch.program)
                            }
                        }
                    }
                }
                updateVolume(ch.id, ch.volume)
                ch.dspEffects.forEachIndexed { effectIdx, effect ->
                    audioEngine.setEffectEnabled(ch.id, effect.type.id, effect.isEnabled)
                    effect.params.forEach { (paramId, value) ->
                        audioEngine.setEffectParam(ch.id, effectIdx, paramId, value)
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                _channels.value = stage.channels
                _hasUnsavedChanges.value = false
                rebuildArmedChannelsCache()
                Log.i(TAG, "Set Stage carregado assincronamente: ${stage.name}")
                updateSystemEvent("SET STAGE ATIVADO: ${stage.name}")
            }
        }
    }

    fun deleteSetStage(bankId: Int, slotId: Int) {
        setStageRepo?.deleteSetStage(bankId, slotId)
    }

    override fun onCleared() {
        super.onCleared()
        midiManager?.stop()
        deviceAudioManager?.release()
        audioEngine.destroy()
        scope.cancel()
    }
}