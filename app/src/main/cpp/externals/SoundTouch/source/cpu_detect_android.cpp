#include "cpu_detect.h"

uint detectCPUextensions(void)
{
    // Retorna 0 para indicar que não há extensões x86 (MMX/SSE) disponíveis no ARM.
    // O SoundTouch usará a implementação genérica em C.
    return 0;
}

void disableExtensions(uint wDisableMask)
{
    // Nada a fazer no Android/ARM por enquanto.
}
