#include <stdint.h>

#include <iostream>

#include <java/lang/Class.hpp>
#include <java/lang/ClassLoader.hpp>

#include <Array.hpp>
#include <java/lang/String.hpp>

using namespace java::lang;

void lock(Object *) { }

void unlock(Object *) { }

String *java::lang::operator "" _j(const char16_t * p, size_t n)
{
    char16_tArray *x = new char16_tArray(p, n);
	String *s = new String();
	s->value = x;
	s->count = n;
    return s->intern(); 
}

Class *class_(const char16_t *s, int n)
{
    return Class::forName(operator "" _j(s, n), false, ClassLoader::getCallerClassLoader());
}

void unimplemented_(const char16_t *name) {
	std::wcerr << "call to unimplemented: ";
	// Not quite right but good enough ;)
	while(*name) std::wcerr << static_cast<wchar_t>(*(name++));
	std::wcerr << std::endl;
}

