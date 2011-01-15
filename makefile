# Suck on it ant

SOURCES := $(shell find src -name '*.java')
OBJECTS := $(subst .java,.class,$(SOURCES))
OBJECTS := $(subst src/,bin/,$(OBJECTS))

CLASSPATH := bin:src:lib/*

all: $(OBJECTS)

run: $(OBJECTS)
	ulimit -v 2048000; \
	java -cp $(CLASSPATH) -ea -Xms32m -Xmx512m main.NoiseBot

clean:
	rm -rf bin

$(OBJECTS): $(SOURCES)
	ulimit -v 2048000; \
	mkdir -p bin;      \
	javac -cp $(CLASSPATH) -d bin $+
