import com.leflat.jass.common.IServerNetwork;
import com.leflat.jass.server.PlayerLeftExpection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

class MockServerNetwork implements IServerNetwork {
    public List<List<String>> sendParameters = new ArrayList<>();
    private Queue<String[]> answers = new LinkedBlockingQueue<>();

    public MockServerNetwork() {

    }

    public MockServerNetwork(String[] answer) {
        addAnswer(answer);
    }

    public void addAnswer(String[] message) {
        answers.add(message);
    }

    public void clearParameters() {
        sendParameters.clear();
    }

    @Override
    public void setPlayerId(int id) {

    }

    @Override
    public String receiveRawMessage() throws PlayerLeftExpection {
        return null;
    }

    @Override
    public void sendMessage(String... message) {
        sendParameters.add(Arrays.asList(message));
    }

    @Override
    public String[] receiveMessage() {
        if (answers.isEmpty()) {
            return new String[]{};
        }
        return answers.remove();
    }
}
