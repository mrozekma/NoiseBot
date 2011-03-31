# Suck on it ant

PASS    := $(shell echo $$RANDOM)
SOURCES := $(shell find src -name '*.java')
OBJECTS := $(subst .java,.class,$(SOURCES))
OBJECTS := $(subst src/,bin/,$(OBJECTS))

CLASSPATH := bin:src:lib/*

all: $(OBJECTS)

run: $(OBJECTS)
	ulimit -v 2048000; \
	java -cp $(CLASSPATH) -ea -Xms32m -Xmx512m main.NoiseBot

test: $(OBJECTS)
	ulimit -v 2048000; \
	java -cp $(CLASSPATH) -ea -Xms32m -Xmx512m main.NoiseBot \
		cmdline irc.lug.rose-hulman.edu 6667 "rh$(USER)bot" "pass$(PASS)" "#rh$(USER)"

clean:
	rm -rf bin

$(OBJECTS): $(SOURCES)
	ulimit -v 2048000; \
	mkdir -p bin;      \
	javac -cp $(CLASSPATH) -d bin $+
