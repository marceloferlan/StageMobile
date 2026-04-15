/**
 * sp_bridge.h — API C pura para o Superpowered USB Audio Bridge
 * 
 * Esta API é usada para comunicação cross-library entre:
 *   - libspbridge.so (c++_static, contém Superpowered)
 *   - libsynthmodule.so (c++_shared, contém Oboe/FluidSynth/DSP)
 * 
 * A separação é necessária porque Superpowered e Oboe exigem STLs incompatíveis.
 */

#ifndef SP_BRIDGE_H
#define SP_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Callback de renderização de áudio.
 * Chamado pelo Superpowered USB callback quando precisa de áudio.
 * 
 * @param audioIO      Buffer interleaved float (output)
 * @param numFrames    Número de frames
 * @param numChannels  Número de canais de saída
 * @return true se áudio foi renderizado, false para silêncio
 */
typedef bool (*SpRenderCallback)(float* audioIO, int numFrames, int numChannels);

/**
 * Registra o callback de renderização do motor de áudio.
 * Deve ser chamado pelo synthmodule após carregar a lib.
 */
void sp_register_render_callback(SpRenderCallback callback);

/**
 * Verifica se o Superpowered USB está ativo.
 */
int sp_is_active(void);

/**
 * Retorna o sample rate atual da interface USB.
 */
int sp_get_sample_rate(void);

#ifdef __cplusplus
}
#endif

#endif // SP_BRIDGE_H
