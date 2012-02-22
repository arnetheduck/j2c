#include <stdint.h>

namespace java
{
    namespace lang
    {
        class String;
        class Object;
    }
}

void lock(java::lang::Object *) { }

void unlock(java::lang::Object *) { }

java::lang::String *lit(const wchar_t *) { return 0; }

java::lang::String *join(java::lang::String *lhs, java::lang::String *rhs) { return 0; }
java::lang::String *join(java::lang::String *lhs, java::lang::Object *rhs) { return 0; }
java::lang::String *join(java::lang::Object *lhs, java::lang::String *rhs) { return 0; }
java::lang::String *join(java::lang::String *lhs, bool rhs) { return 0; }
java::lang::String *join(bool lhs, java::lang::String *rhs) { return 0; }
java::lang::String *join(java::lang::String *lhs, int8_t rhs) { return 0; }
java::lang::String *join(int8_t lhs, java::lang::String *rhs) { return 0; }
java::lang::String *join(java::lang::String *lhs, wchar_t rhs) { return 0; }
java::lang::String *join(wchar_t lhs, java::lang::String *rhs) { return 0; }
java::lang::String *join(java::lang::String *lhs, double rhs) { return 0; }
java::lang::String *join(double lhs, java::lang::String *rhs) { return 0; }
java::lang::String *join(java::lang::String *lhs, float rhs) { return 0; }
java::lang::String *join(float lhs, java::lang::String *rhs) { return 0; }
java::lang::String *join(java::lang::String *lhs, int32_t rhs) { return 0; }
java::lang::String *join(int32_t lhs, java::lang::String *rhs) { return 0; }
java::lang::String *join(java::lang::String *lhs, int64_t rhs) { return 0; }
java::lang::String *join(int64_t lhs, java::lang::String *rhs) { return 0; }
java::lang::String *join(java::lang::String *lhs, int16_t rhs) { return 0; }
java::lang::String *join(int16_t lhs, java::lang::String *rhs) { return 0; }

