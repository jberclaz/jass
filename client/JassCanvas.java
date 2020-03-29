import java.awt.*;

public class JassCanvas extends Canvas{
    int     mode;   // 0 : rien, 1 : jouer
    String  name;
    boolean atout;

    public void setMode(int mode) {
	this.mode = mode;
    }

    public void setAtout(boolean atout) {
	this.atout = atout;
    }

    public void setName(String name) {
	this.name = name;
    }

    public int getMode() {
	return mode;
    }

}
