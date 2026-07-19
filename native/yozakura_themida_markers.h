#pragma once

#if defined(YOZAKURA_THEMIDA_MARKERS)
#include <ThemidaSDK.h>
#define YOZAKURA_AUTH_VM_START VM_FISH_WHITE_START
#define YOZAKURA_AUTH_VM_END VM_FISH_WHITE_END
#else
#define YOZAKURA_AUTH_VM_START do { } while (0);
#define YOZAKURA_AUTH_VM_END do { } while (0);
#endif
