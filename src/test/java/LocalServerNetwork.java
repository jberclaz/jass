import com.leflat.jass.server.ServerNetwork;

import java.io.*;

public class LocalServerNetwork extends ServerNetwork {
    public LocalServerNetwork(PipedInputStream input, PipedOutputStream output, int playerId) {
        var isr = new InputStreamReader(input);
        is = new BufferedReader(isr);
        os = new PrintWriter(new BufferedOutputStream(output), false);
        this.playerId = playerId;
    }
}
