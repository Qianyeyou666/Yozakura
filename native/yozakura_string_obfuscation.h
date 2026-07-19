#pragma once

#include <cstddef>
#include <string>

namespace yozakura {
namespace protection {

template <std::size_t Size, unsigned char Key>
class XorLiteral {
public:
    constexpr explicit XorLiteral(const char (&value)[Size]) : bytes_{} {
        for (std::size_t index = 0; index < Size; ++index) {
            bytes_[index] = static_cast<unsigned char>(value[index]) ^ Key;
        }
    }

    __declspec(noinline) std::string reveal() const {
        volatile unsigned char runtimeKey = Key;
        std::string value(Size - 1, '\0');
        for (std::size_t index = 0; index + 1 < Size; ++index) {
            value[index] = static_cast<char>(bytes_[index] ^ runtimeKey);
        }
        return value;
    }

private:
    unsigned char bytes_[Size];
};

} // namespace protection
} // namespace yozakura

#define YOZAKURA_PROTECTED_STRING(value)                                              \
    []() -> std::string {                                                             \
        constexpr unsigned char key = static_cast<unsigned char>(                     \
            (((__LINE__ * 17u) + (__COUNTER__ * 29u) + 0x5Bu) & 0xFFu) | 1u);        \
        constexpr ::yozakura::protection::XorLiteral<sizeof(value), key> literal(value); \
        return literal.reveal();                                                      \
    }()
