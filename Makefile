UBERJAR    := target/harvest_sync-0.0.1-standalone.jar
SRC_FILES  := $(shell find src resources -type f)
TARGET_JAR := ~/.scripts/harvest_sync.jar

default: install

repl:
	clj -A:nrepl -e '(require (quote cider-nrepl.main)) (cider-nrepl.main/init ["refactor-nrepl.middleware/wrap-refactor", "cider.nrepl/cider-middleware"])'

uberjar: $(UBERJAR)

install: $(TARGET_JAR)

$(UBERJAR): $(SRC_FILES)
	clj -A:uberjar

$(TARGET_JAR): $(UBERJAR)
	cp $^ $@
