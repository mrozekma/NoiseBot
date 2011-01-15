# Suck on it ant

SOURCES := $(shell find src -name '*.java')
OBJECTS := $(subst .java,.class,$(SOURCES))
OBJECTS := $(subst src/,bin/,$(OBJECTS))

CLASSPATH := bin:src:lib/*

all: $(OBJECTS)

run: $(OBJECTS)
	java -ea -Xms32m -Xmx512m main.NoiseBot

clean:
	rm -rf bin

$(OBJECTS): $(SOURCES)
	mkdir -p bin
	javac -d bin $+
