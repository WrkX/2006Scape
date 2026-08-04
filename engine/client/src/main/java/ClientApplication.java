import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Desktop entry point for launching the canvas-based client shell.
 */
final class ClientApplication {

	private ClientApplication() {
	}

	static void start(Game game) throws UnknownHostException {
		Game.nodeID = 10;
		Game.portOff = 0;
		Game.setHighMem();
		Game.isMembers = true;
		Signlink.storeid = 32;
		Signlink.startpriv(InetAddress.getLocalHost());
		game.startClient(503, 765);
	}

}
