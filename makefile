# Suck on it ant.  Suck it hard.

PASS    := $(shell echo $$RANDOM)
SOURCES := $(shell find src -name '*.java')
OBJECTS := $(subst .java,.class,$(SOURCES))
OBJECTS := $(subst src/,bin/,$(OBJECTS))

CLASSPATH := bin:src:lib/*

all: $(OBJECTS)

run: $(OBJECTS)
	ulimit -v 4096000; \
	java -cp $(CLASSPATH) -ea -Xms64m -Xmx512m main.NoiseBot

test: $(OBJECTS)
	ulimit -v 4096000; \
	java -cp $(CLASSPATH) -ea -Xms64m -Xmx512m main.NoiseBot \
		cmdline irc.lug.rose-hulman.edu 6667 "rh$(USER)bot" "pass$(PASS)" "#rh$(USER)"

clean:
	rm -rf bin

$(OBJECTS): $(SOURCES)
	ulimit -v 4096000; \
	mkdir -p bin;      \
	javac -cp $(CLASSPATH) -d bin $+
