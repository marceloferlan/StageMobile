# Superpowered Licensing — Meeting Briefing

*Prepared for video call with Superpowered Sales Team*

---

## 1. About the Project

**StageMobile** is a professional audio synthesis app for Android, designed for **live stage performance** by musicians. It turns Android tablets and phones into a multi-timbral SoundFont (SF2) synthesizer with a full DSP effects rack, controlled via USB MIDI keyboards.

**Key specs:**
- Platform: Android (arm64-v8a only, minSdk 26 / Android 8.0)
- Audio engine: FluidSynth (SF2 wavetable synthesis) + custom DSP chain (STK, SoundTouch)
- 16 simultaneous MIDI channels, 80-voice polyphony
- Real-time DSP: per-channel effects rack (EQ, compressor, reverb, delay, chorus, limiter) + master bus processing
- UI: Jetpack Compose (Material 3), optimized for tablets (Samsung Galaxy Tab S9 FE) and phones (Galaxy S24 Ultra)
- Distribution: Google Play Store

**Target audience:** Professional and semi-professional musicians who use tablets as sound modules on stage, connected to USB MIDI controllers and USB audio interfaces (e.g., PreSonus AudioBox USB 96).

---

## 2. How We Use Superpowered

We use **Superpowered exclusively for USB Audio output** — specifically `SuperpoweredAndroidUSB` to bypass Android's class-compliant USB audio driver (ALSA/HAL) and access the USB audio interface directly via its file descriptor.

**What we use:**
- `Superpowered::AndroidUSB::onConnect()` — take control of USB audio interface fd
- `Superpowered::AndroidUSBAudio::easyIO()` — configure and start USB audio streaming (48kHz, 24-bit, stereo, 256 frames)
- `Superpowered::DynamicInitialize()` — SDK initialization
- `Superpowered::CPU::setSustainedPerformanceMode()` — CPU performance hint

**What we do NOT use:**
- Superpowered audio player / decoder
- Superpowered effects (we have our own DSP chain via STK)
- Superpowered spatialization
- Superpowered networking / cryptographics
- Superpowered HLS streaming
- Any Superpowered audio processing features

In summary: we use ~5% of the SDK surface area — only the USB audio I/O layer.

---

## 3. Technical Architecture

We solved a significant C++ ABI compatibility challenge to integrate Superpowered alongside Google Oboe:

**The problem:** Superpowered's static library (`.a`) requires `c++_static`, while our main audio engine uses Google Oboe which requires `c++_shared`. Linking both into the same `.so` causes SIGSEGV/ANR due to STL vtable conflicts.

**Our solution — Isolated Gradle Module + Pure C Bridge:**
- Superpowered lives in a **separate Gradle module** (`:superpowered-usb`) compiled with `-DANDROID_STL=c++_static`
- This produces `libspbridge.so` (isolated, self-contained C++ runtime)
- Our main audio engine (`libsynthmodule.so`, `c++_shared`) communicates with `libspbridge.so` via `dlopen` + `dlsym` using a **pure C function pointer interface** (`extern "C"`)
- No C++ types, STL containers, or vtables cross the library boundary
- Audio data is exchanged via a shared lock-free ring buffer

**Audio path:**
```
FluidSynth → DSP Chain → Ring Buffer → Superpowered USB callback → Hardware
                                    ↘ Oboe callback (fallback) → Internal speaker
```

When Superpowered owns the USB fd, Oboe's callback outputs silence. When Superpowered is inactive (no USB), Oboe handles output normally. The switch is runtime-dynamic, per audio frame.

---

## 4. Business Model

**The app has two audio output modes, selectable by the user in Settings:**

| Mode | Driver | Cost to user | Status |
|------|--------|-------------|--------|
| **"Android Nativo"** | Google Oboe (AAudio/OpenSLES) | **Free** (included in base app) | Default |
| **"Otimizado (USB)"** | Superpowered USB | **Paid add-on** (In-App Purchase) | Gated by IAP |

- The base app ships with Oboe and works completely without Superpowered
- Users who need professional USB audio (lower latency, less jitter on Samsung Exynos devices) can purchase the "Optimized USB Driver" add-on
- The add-on is a **one-time non-consumable purchase** via Google Play Billing
- Purchase is validated server-side via Firebase Cloud Functions → Firebase Auth Custom Claims
- The `libspbridge.so` binary is included in the APK for all users (technical requirement — Google Play doesn't support conditional native library delivery), but only activated after purchase verification

---

## 5. Questions for Superpowered

### Licensing Model

1. **Which tier fits our use case?** We believe WHITE LABEL is the right fit since we're publishing on Google Play with unlimited installs. Can you confirm?

2. **Add-on pricing model:** Since Superpowered is sold as an optional paid feature (not included in the base app experience), does the annual fee scale with total app installs, or only with add-on activations? This distinction significantly affects our unit economics.

3. **Partial SDK usage:** We only use `AndroidUSBAudio` (USB I/O). We don't use any Superpowered audio processing, effects, players, or decoders. Is there a reduced-scope license or pricing consideration for USB-only usage?

4. **Revenue share / royalties:** The WHITE LABEL tier mentions "No Royalty, No Rev-Share." Can you confirm this applies regardless of how many IAP purchases we process for the add-on?

### Technical

5. **Production license key:** We're currently using `"ExampleLicenseKey-WillExpire-OnNextUpdate"`. When does this key expire, and how do we obtain a production key?

6. **SDK version and updates:** We're using the static libraries currently distributed (`libSuperpoweredAndroidarm64-v8a.a`). How are SDK updates delivered? Is there a versioning/changelog we should follow?

7. **c++_static isolation:** Our architecture uses a separate Gradle module with `c++_static` to avoid ABI conflicts with Oboe (`c++_shared`). Is this a supported/recommended configuration, or are there known issues we should be aware of?

8. **Android version compatibility:** We target minSdk 26 (Android 8.0). Are there known limitations or device-specific issues with `AndroidUSBAudio` on this range?

### Business / Legal

9. **Binary redistribution:** The `libspbridge.so` (which statically links Superpowered) is included in every APK, even for free-tier users who never activate the add-on. The SDK code is never executed unless the user purchases and activates. Is this distribution model acceptable under the license?

10. **License key protection:** How should we protect the license key in a released APK? Is there obfuscation guidance or a server-side activation model?

11. **Annual renewal:** What happens if the annual license is not renewed? Does the SDK stop functioning (kill switch), or does it continue working with the last activated key?

12. **Competitor clause:** Are there restrictions on using Superpowered alongside other audio SDKs (we use Google Oboe as the fallback driver)?

---

## 6. Contact & Demo

**Developer:** Marcelo Ferlan (marcioferlan@gmail.com)
**App:** StageMobile (com.marceloferlan.stagemobile)
**Platform:** Android (Google Play)
**Current stage:** Pre-release (feature-complete, in private testing)

We can provide a live demo of the USB audio switching during the call if helpful.
