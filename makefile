# Suck on it ant.  Suck it hard.

PASS    := $(shell echo $$RANDOM)
SOURCES := $(shell find src -name '*.java')
OBJECTS := $(subst .java,.class,$(SOURCES))
OBJECTS := $(subst src/,bin/,$(OBJECTS))

CLASSPATH := bin:src:lib/*

all: $(OBJECTS)

run: $(OBJECTS)
	rtn=2; while [[ $$rtn -eq 2 ]]; do \
		java -cp $(CLASSPATH) -ea -Xms64m -Xmx512m main.NoiseBot $(conn); \
		rtn=$$?; \
	done

test: conn=test
test: run

clean:
	rm -rf bin

$(OBJECTS): $(SOURCES)
	mkdir -p bin;      \
	javac -cp $(CLASSPATH) -d bin $+
