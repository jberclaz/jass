import com.leflat.jass.common.IJassUi;
import com.leflat.jass.common.IJassUiFactory;
import com.leflat.jass.common.IRemotePlayer;

public class MockUiFactory implements IJassUiFactory {
    @Override
    public IJassUi getUi(IRemotePlayer remotePlayer) {
        return new MockUi(remotePlayer);
    }
}
