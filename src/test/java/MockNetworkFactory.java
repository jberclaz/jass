import com.leflat.jass.common.IClientNetwork;
import com.leflat.jass.common.IClientNetworkFactory;

public class MockNetworkFactory implements IClientNetworkFactory {
    private static int nbrPlayers = 0;

    @Override
    public IClientNetwork getClientNetwork() {
        return new MockClientNetwork(nbrPlayers++);
    }
}
