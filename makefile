# Suck on it ant.  Suck it hard.

PASS    := $(shell echo $$RANDOM)
SOURCES := $(shell find src -name '*.java')
OBJECTS := $(subst .java,.class,$(SOURCES))
OBJECTS := $(subst src/,bin/,$(OBJECTS))

CLASSPATH := bin:src:lib/*

all: $(OBJECTS)

run: $(OBJECTS)
	java -cp $(CLASSPATH) -ea -Xms64m -Xmx512m main.NoiseBot

test: $(OBJECTS)
	java -cp $(CLASSPATH) -ea -Xms64m -Xmx512m main.NoiseBot test

clean:
	rm -rf bin

$(OBJECTS): $(SOURCES)
	mkdir -p bin;      \
	javac -cp $(CLASSPATH) -d bin $+
