#include <stdint.h>

#include <iostream>

#include <java/lang/Class.hpp>

#include <Array.hpp>
#include <java/lang/String.hpp>

using namespace java::lang;

void lock(Object*) { }

void unlock(Object*) { }

String *java::lang::operator "" _j(const char16_t* p, size_t n) {
  auto x = new char16_tArray(p, n);
  auto s = new String(x, true);
  return s->intern();
}

Class *class_(const char16_t *s, int n) {
  return Class::forName(operator "" _j(s, n));
}

void unimplemented_(const char16_t *name) {
  std::wcerr << "call to unimplemented: ";
  // Not quite right but good enough ;)
  while(*name) std::wcerr << static_cast<wchar_t>(*(name++));
  std::wcerr << std::endl;
}

void init_jvm() {
  // This will be called by the generated main file before running any java code
  // Use it to initialize system properties and other stuff the JVM should provide
}

java::lang::StringArray* make_args(int args, char** argv) {
  // Helper that should convert strings passed to app into
  // StringArray passed to java main
  return nullptr;
}
