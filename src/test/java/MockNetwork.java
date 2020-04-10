import com.leflat.jass.common.INetwork;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

class MockNetwork implements INetwork {
    public List<List<String>> sendParameters = new ArrayList<>();
    private Queue<String[]> answers = new LinkedBlockingQueue<>();

    public MockNetwork() {

    }

    public MockNetwork(String[] answer) {
        addAnswer(answer);
    }

    public void addAnswer(String[] message) {
        answers.add(message);
    }

    public void clearParameters() {
        sendParameters.clear();
    }

    @Override
    public int sendMessage(String message) {
        sendParameters.add(Collections.singletonList(message));
        return 0;
    }

    @Override
    public void setPlayerId(int id) {

    }

    @Override
    public int sendMessage(List<String> message) {
        sendParameters.add(message);
        return 0;
    }

    @Override
    public String[] receiveMessage() {
        if (answers.isEmpty()) {
            return new String[]{};
        }
        return answers.remove();
    }
}
