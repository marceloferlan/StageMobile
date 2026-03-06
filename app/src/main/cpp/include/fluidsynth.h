/* FluidSynth - A Software Synthesizer
 * Master include header for FluidSynth API
 */

#ifndef _FLUIDSYNTH_H
#define _FLUIDSYNTH_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {
#endif

/* FluidSynth API visibility macro */
#ifndef FLUIDSYNTH_API
#define FLUIDSYNTH_API
#endif

/* FluidSynth deprecated function marker */
#ifndef FLUID_DEPRECATED
#define FLUID_DEPRECATED
#endif

#ifdef __cplusplus
}
#endif

#include "fluidsynth/types.h"
#include "fluidsynth/settings.h"
#include "fluidsynth/synth.h"
#include "fluidsynth/shell.h"
#include "fluidsynth/sfont.h"
#include "fluidsynth/audio.h"
#include "fluidsynth/event.h"
#include "fluidsynth/midi.h"
#include "fluidsynth/seq.h"
#include "fluidsynth/seqbind.h"
#include "fluidsynth/log.h"
#include "fluidsynth/misc.h"
#include "fluidsynth/mod.h"
#include "fluidsynth/gen.h"
#include "fluidsynth/voice.h"
#include "fluidsynth/version.h"

#endif /* _FLUIDSYNTH_H */
