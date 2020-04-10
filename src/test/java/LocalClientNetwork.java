import com.leflat.jass.client.ClientNetwork;
import com.leflat.jass.client.ServerDisconnectedException;
import com.leflat.jass.common.ClientConnectionInfo;
import com.leflat.jass.common.IClientNetwork;

import java.io.*;
import java.util.Collections;
import java.util.List;

public class LocalClientNetwork extends ClientNetwork {

    public LocalClientNetwork(PipedInputStream input, PipedOutputStream output) throws ServerDisconnectedException {
        os = new PrintWriter(output, false);
        is = new BufferedReader(new InputStreamReader(input));
        playerId = Integer.parseInt(receiveRawMessage());
        sendMessage(Collections.singletonList("Berte"));
    }


    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void disconnect() {
        os.close();
        try {
            is.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}