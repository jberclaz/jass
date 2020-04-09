import com.leflat.jass.client.ServerDisconnectedException;
import com.leflat.jass.common.ClientConnectionInfo;
import com.leflat.jass.common.ConnectionError;
import com.leflat.jass.common.IController;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MockController implements IController {
    private static int currentId = 0;
    private Lock lock;

    @Override
    public ClientConnectionInfo connect(String host, int requestGameId, String name) {
        System.out.println("Creating lock in thread " + Thread.currentThread());
        lock = new ReentrantLock();
        return new ClientConnectionInfo(currentId++, 1234, ConnectionError.CONNECTION_SUCCESSFUL);
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void sendRawMessage(String message) {

    }

    @Override
    public void sendMessage(List<String> message) {

    }

    @Override
    public String receiveRawMessage() throws ServerDisconnectedException {
        return null;
    }

    @Override
    public void disconnect() {
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    @Override
    public void run() {
    }
}
