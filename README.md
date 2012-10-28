Introduction
--
J2C will convert Java code into hopefully compilable C++ code. It works on
source level, translating Java source code constructs into their rough
equivalents in C++ . The output will be reasonably valid C++ code that looks a
lot like its Java counterpart and hopefully works mostly the same.

Download
--
The most recent version of the project is available as source code. You can get it
either from Eclipse labs (https://code.google.com/a/eclipselabs.org/p/j2c/) (main 
site) or github (https://github.com/arnetheduck/j2c) (backup). 

From time to time, a release may appear at the Eclipse labs site - see
https://code.google.com/a/eclipselabs.org/p/j2c/downloads/list .

Status
--
This project is an idea I've been wanting to try out written down in code.
Think of it as a paper napkin with some notes on, but in this case, the notes
compile and sometimes spit out working stuff. In other words, no guarantees 
and no quality control label.

That said, j2c successfully converts most of OpenJDK 6 and SWT 3.7 to C++ 
that compiles and passes a quick ocular inspection. Most *language* features 
of Java 1.6 are covered (i e you'll still need a JDK and runtime). 

With a few patches and implementations of native methods in the converted 
OpenJDK code, the included Hello test prints it's message. A more complete 
example would need a more complete runtime, either by implementing the native 
and JVM parts of a class library or by implementing the stubs that are 
generated for missing dependencies.

This is the first time I write an Eclipse plugin, so be nice.

Running
--
J2C comes in the form of an Eclipse plugin. If you downloaded the jar, copy
it to $ECLIPSE_HOME/dropins.

If you downloaded the source code you'll have run the plugin by opening the 
project in Eclipse and starting a new Eclipse test instance by using the run
button in the plugin.xml overview.

Once you have the plugin running, set up your Java code as a Java
Project. Eclipse must be able to compile your code for J2C do to its work!

Once the Java Project is set up (with all dependencies etc), you can run J2C by
right-clicking the project (or a class/package) in the 'Project Explorer' or
'Package Explorer' view and choosing the 'Translate to C++' option. You will 
need to create a folder for the conversion output - the plugin will tell you 
where.

Testing
--
The test project contains a few cases which should be handled correctly by the
translator (by correctly, I mean that they compile with g++ 4.7). You'll find
a CDT project in ctest that builds using the generated Makefile after running
the plugin on the test project.

Output
--
For each Java class, j2c will output a header file and its implementation.
Inner classes end up in separate .h/.cpp pairs. Native method stubs will be
put in a separate file for your editing pleasure.

Classes for which there is no source will have a header written as well as 
a stub file with empty implementations. Throughout, the heap will be used 
to allocate class instances but no attempt is made to collect garbage - 
I recommend Boehm's garbage collector for that.

What's missing (that I can think of right now)
--
 * Reflection 
 * Anything involving byte code (class loading, dynamic code generation, etc)

Helping out
--
Patches and forks are most welcome, as is testing, but please don't report 
issues unless you also attach a simple test case.

= Final words =
Send me a note if you manage (or not) to do something useful with this 
converter!

Licensing
--
The project is licensed under the Eclipse Public License 1.0.

Thanks
--
No animals were hurt while writing this code, but the Nightwatchman
might have sore fingers and throat from all that playing...

Have fun,
Jacek Sieka (arnetheduck using google mail point com)

