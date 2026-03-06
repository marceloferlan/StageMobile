# StageMobile 🎧🎹

O **StageMobile** é um módulo de som (Sound Module) profissional para Android, projetado para performances ao vivo. Ele utiliza o motor **FluidSynth** com backend **Oboe (C++/AAudio)** para garantir a menor latência possível e estabilidade absoluta.

## ✨ Principais Funcionalidades

- **Motor FluidSynth de Baixa Latência**: Renderização de áudio via Oboe (FastAudio).
- **Suporte Multi-Canal (16 canais MIDI)**: Carregue diferentes SoundFonts (.sf2) em cada canal.
- **Roteamento MIDI Avançado**: 
  - Suporte a múltiplos controladores MIDI USB simultâneos.
  - Filtro individual por canal (vincule um teclado específico a um instrumento).
  - Ativação seletiva de hardware no menu de configurações.
- **Gerenciamento de Áudio USB**: Roteamento manual ou automático para interfaces de áudio externas (ex: Presonus AudioBox).
- **Mixer Profissional**: Controle de volume individual, Meters em tempo real, ARM (disparo de notas), Mute e Solo.
- **Monitor de Recursos**: Acompanhamento em tempo real de CPU e RAM para garantir que o show não pare.

## 🛠️ Tecnologias Utilizadas

- **Kotlin + Jetpack Compose**: Interface moderna e reativa.
- **C++ / JNI**: Integração nativa com FluidSynth e drivers de áudio de baixa latência.
- **Android MIDI API**: Comunicação USB robusta.

## 🚀 Como Rodar

1. Clone o repositório.
2. Abra no **Android Studio (Ladybug ou superior)**.
3. Certifique-se de ter o NDK instalado para compilar a camada nativa.
4. Conecte seu controlador MIDI via cabo OTG ou HUB USB.
5. Carregue seus arquivos `.sf2` prediletos e comece a tocar!

---
*Desenvolvido com foco em músicos que buscam performance e confiabilidade no palco.*
