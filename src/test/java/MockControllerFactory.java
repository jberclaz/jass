import com.leflat.jass.common.IController;
import com.leflat.jass.common.IControllerFactory;
import com.leflat.jass.common.IPlayer;

public class MockControllerFactory implements IControllerFactory {
    @Override
    public IController getController(IPlayer player) {
        return new MockController();
    }
}
