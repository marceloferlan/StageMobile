# 🗺️ Mapa de Componentes Críticos - StageMobile

Este índice centraliza a localização dos componentes fundamentais do projeto para garantir manutenção rápida e precisão arquitetural.

## 🎛️ Rack de Efeitos & DSP
| Componente | Arquivo | Função / Âncora |
| :--- | :--- | :--- |
| **Rack de Efeitos (Dialog)** | `app/src/main/java/com/marceloferlan/stagemobile/ui/components/DSPEffectsRackDialog.kt` | `fun DSPEffectsRackDialog` |
| **Unidade do Rack (Card)** | `app/src/main/java/com/marceloferlan/stagemobile/ui/components/DSPEffectsRackDialog.kt` | `fun EffectRackUnit` |
| **Knob Circular DSP** | `app/src/main/java/com/marceloferlan/stagemobile/ui/components/DSPCircularKnob.kt` | `fun DSPCircularKnob` |
| **Meter do Compressor** | `app/src/main/java/com/marceloferlan/stagemobile/ui/components/DSPCompressorMeter.kt` | `fun DSPCompressorMeter` |

## 🎚️ Mixer & Canais
| Componente | Arquivo | Função / Âncora |
| :--- | :--- | :--- |
| **Mixer Screen (Principal)** | `app/src/main/java/com/marceloferlan/stagemobile/ui/screens/MixerScreen.kt` | `fun MixerScreen` |
| **Canal de Instrumento** | `app/src/main/java/com/marceloferlan/stagemobile/ui/mixer/InstrumentChannelStrip.kt` | `fun InstrumentChannelStrip` |
| **Canal Master** | `app/src/main/java/com/marceloferlan/stagemobile/ui/mixer/InstrumentChannelStrip.kt` | `isMaster = true` |
| **Toolbar do Mixer** | `app/src/main/java/com/marceloferlan/stagemobile/ui/components/MixerScreenInfoPanel.kt` | `fun MixerScreenInfoPanel` |
| **Painel de Informações** | `app/src/main/java/com/marceloferlan/stagemobile/ui/components/MixerScreenInfoPanel.kt` | `fun MixerScreenInfoPanel` |

## 🎹 Gerenciamento de Sons (SF2)
| Componente | Arquivo | Função / Âncora |
| :--- | :--- | :--- |
| **Seletor de SoundFont** | `app/src/main/java/com/marceloferlan/stagemobile/ui/components/SoundFontSelectorDialog.kt` | `fun SoundFontSelectorDialog` |
| **Seletor de Presets SF2** | `app/src/main/java/com/marceloferlan/stagemobile/ui/components/SF2PresetSelectorDialog.kt` | `fun SF2PresetSelectorDialog` |
| **Manutenção de SoundFonts** | `app/src/main/java/com/marceloferlan/stagemobile/ui/screens/SoundFontMaintenanceScreen.kt` | `fun SoundFontMaintenanceScreen` |

## ⚙️ Configurações & Menus
| Componente | Arquivo | Função / Âncora |
| :--- | :--- | :--- |
| **Configurações Globais** | `app/src/main/java/com/marceloferlan/stagemobile/ui/screens/SystemGlobalSettings.kt` | `fun SystemGlobalSettings` |
| **Gerenciamento de Sets** | `app/src/main/java/com/marceloferlan/stagemobile/ui/screens/SetsScreen.kt` | `fun SetsScreen` |
| **Drum Pads** | `app/src/main/java/com/marceloferlan/stagemobile/ui/screens/DrumpadsScreen.kt` | `fun DrumpadsScreen` |
| **Continuous Pads** | `app/src/main/java/com/marceloferlan/stagemobile/ui/screens/ContinuousPadsScreen.kt` | `fun ContinuousPadsScreen` |

## 🖼️ Overlays & Modais (MainActivity)
As modais imersivas são gerenciadas no bloco de UI da `MainActivity.kt`:
- **Caminho:** `app/src/main/java/com/marceloferlan/stagemobile/MainActivity.kt`
- **Âncora:** Dentro do `Scaffold` -> `ModalBottomSheet` ou `AnimatedVisibility` para overlays.
