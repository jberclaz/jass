import com.leflat.jass.common.ClientConnectionInfo;
import com.leflat.jass.common.ConnectionError;
import com.leflat.jass.common.IClientNetwork;

import java.util.List;

public class MockClientNetwork implements IClientNetwork {
    private int playerId;
    public MockClientNetwork(int id) {
        playerId = id;
    }

    @Override
    public ClientConnectionInfo connect(String host, int requestedGameId, String name) {
        return new ClientConnectionInfo(playerId, 1234, ConnectionError.CONNECTION_SUCCESSFUL);
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void disconnect() {

    }

    @Override
    public void sendMessage(List<String> message) {

    }

    @Override
    public String receiveRawMessage() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "100";
    }
}
