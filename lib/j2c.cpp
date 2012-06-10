#include <stdint.h>

#include <java.lang.Boolean.h>
#include <java.lang.Byte.h>
#include <java.lang.Character.h>
#include <java.lang.Double.h>
#include <java.lang.Float.h>
#include <java.lang.Integer.h>
#include <java.lang.Long.h>
#include <java.lang.Short.h>

#include <java.lang.Class.h>
#include <java.lang.ClassLoader.h>

#include <Array.h>
#include <java.lang.String.h>

using namespace java::lang;

void lock(Object *) { }

void unlock(Object *) { }

String *java::lang::operator "" _j(const char16_t * p, size_t n)
{
    char16_tArray *x = new char16_tArray(p, n);
	String *s = new String();
	s->value_ = x;
	s->count_ = n;
    return s->intern(); 
}

Class *class_(const char16_t *s, int n)
{
    return Class::forName(operator "" _j(s, n), false, ClassLoader::getCallerClassLoader());
}

static inline String *toString(Object *o) 
{
    return o ? o->toString() : u"null"_j;
}

String *join(String *lhs, String *rhs)
{ 
    char16_tArray *a = new char16_tArray(lhs->length() + rhs->length());
    for(auto i = 0; i < lhs->length(); ++i) {
        (*a)[i] = lhs->charAt(i);
    }

    for(auto i = 0; i < rhs->length(); ++i) {
        (*a)[i + lhs->length()] = rhs->charAt(i);
    }
    
    return new java::lang::String(a);
}

String *join(String *lhs, Object *rhs) { return join(rhs, toString(lhs)); }
String *join(Object *lhs, String *rhs) { return join(toString(lhs), rhs); }

String *join(String *lhs, bool rhs) { return join(lhs, Boolean::toString(rhs)); }
String *join(bool lhs, String *rhs) { return join(Boolean::toString(lhs), rhs); }

String *join(String *lhs, int8_t rhs) { return join(lhs, Byte::toString(rhs)); }
String *join(int8_t lhs, String *rhs) { return join(Byte::toString(lhs), rhs); }

String *join(String *lhs, char16_t rhs) { return join(lhs, Character::toString(rhs)); }
String *join(char16_t lhs, String *rhs) { return join(Character::toString(lhs), rhs); }

String *join(String *lhs, double rhs) { return join(lhs, Double::toString(rhs)); }
String *join(double lhs, String *rhs) { return join(Double::toString(lhs), rhs); }

String *join(String *lhs, float rhs) { return join(lhs, Float::toString(rhs)); }
String *join(float lhs, String *rhs) { return join(Float::toString(lhs), rhs); }

String *join(String *lhs, int32_t rhs) { return join(lhs, Integer::toString(rhs)); }
String *join(int32_t lhs, String *rhs) { return join(Integer::toString(lhs), rhs); }

String *join(String *lhs, int64_t rhs) { return join(lhs, Long::toString(rhs)); }
String *join(int64_t lhs, String *rhs) { return join(Long::toString(lhs), rhs); }

String *join(String *lhs, int16_t rhs) { return join(lhs, Short::toString(rhs)); }
String *join(int16_t lhs, String *rhs) { return join(Short::toString(lhs), rhs); }

