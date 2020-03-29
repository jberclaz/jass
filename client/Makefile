SOURCES = AbsoluteConstraints.java  CanvasPlayer.java    DialogConnect.java \
	AbsoluteLayout.java       CanvasTop.java       DialogInfo.java      \
	CanvasBorder.java         ClientFrame.java                          \
	DialogPartnerChoice.java  DialogNewPart.java   ClientListener.java  \
	DialogTeamChoice.java     CanvasCenter.java    ClientNetwork.java   \
	FlatJassClientSystem.java CanvasLastPlie.java  DialogAtout.java     \
        JassCanvas.java

JAVA    = java
JAVAC   = javac

all: $(SOURCES:.java=.class)

%.class: %.java
	$(JAVAC) $<

run: $(SOURCES:.java=.class)
	$(JAVA) FlatJassClientSystem

clean:
	$(RM) *.class *~
