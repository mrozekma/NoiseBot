# Suck on it ant.  Suck it hard.

SHELL = /bin/bash
SOURCES := $(shell find src -name '*.java')
OBJECTS := $(subst .java,.class,$(SOURCES))
OBJECTS := $(subst src/,bin/,$(OBJECTS))

CLASSPATH := bin:src:lib/*

.PHONY: all
all: $(OBJECTS)

# Adapted from http://stackoverflow.com/a/15637871/309308
.PHONY: jdk-version-check
jdk-version-check:
	@echo -e "javac 1.8\n$(shell javac -version 2>&1)" | sort -ct. -k1,1n -k2,2n -k3,3n

.PHONY: run
run: $(OBJECTS)
	rtn=2; while [[ $$rtn -eq 2 ]]; do \
		java -cp $(CLASSPATH) -ea -Xms64m -Xmx512m main.NoiseBot $(conn); \
		rtn=$$?; \
	done

.PHONY: test
test: conn=test
test: run

.PHONY: slack
slack: conn=slack
slack: run

.PHONY: clean
clean:
	rm -rf bin

$(OBJECTS): $(SOURCES) | jdk-version-check
	mkdir -p bin; \
	javac -cp $(CLASSPATH) -d bin $+

.PHONY: check-syntax
check-syntax:
	javac -cp $(CLASSPATH) -Xlint:-serial ${CHK_SOURCES}
