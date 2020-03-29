SOURCES = FlatJassServerSystem.java  ServerNetwork.java \
	  ClientLeftException.java

JAVA    = java
JAVAC   = javac

all: $(SOURCES:.java=.class)

%.class: %.java
	$(JAVAC) $<

run: $(SOURCES:.java=.class)
	$(JAVA) FlatJassServerSystem

clean:
	$(RM) *.class *~
