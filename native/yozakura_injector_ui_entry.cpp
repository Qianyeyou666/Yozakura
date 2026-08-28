#include "yozakura_injector_ui_app.h"

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int) {
    return yozakura::injector::ui::runInjectorApplication(instance);
}
