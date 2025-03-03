package centralserver;

import common.*;

/*
 * Updated on Feb 2025
 */
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

 /* You can add/change/delete class attributes if you think it would be
  * appropriate.
  *
  * You can also add helper methods and change the implementation of those
  * provided if you think it would be appropriate, as long as you DO NOT
  * CHANGE the provided interface.
  */

/* TODO extend appropriate classes and implement the appropriate interfaces */
public class CentralServer extends UnicastRemoteObject implements ICentralServer {
    private List<MessageInfo> receivedMessages;
    private int expectedTotal;
    private long startTime;

    protected CentralServer () throws RemoteException {
        super();
        receivedMessages = new ArrayList<>();
        expectedTotal = -1;
        startTime = -1;
    }

    public static void main (String[] args) throws RemoteException, java.net.UnknownHostException {
        if (System.getProperty("java.version").startsWith("1.")) {
            System.setSecurityManager(new SecurityManager());
        }

        try {
            // Set the RMI hostname to the machine's IP
            String hostname = java.net.InetAddress.getLocalHost().getHostAddress();
            System.setProperty("java.rmi.server.hostname", hostname);

            Registry registry = null;
            try {
                registry = LocateRegistry.createRegistry(1099);
            } catch (RemoteException e) {
                registry = LocateRegistry.getRegistry(1099);
            }

            CentralServer cs = new CentralServer();
            registry.rebind("CentralServer", cs);
            System.out.println("Central Server Ready");
            cs.startTime = System.currentTimeMillis(); // Start timing when server is ready
            
            while (true) {
                Thread.sleep(1000);
            }
        } catch (RemoteException e) {
            System.err.println("Failed to bind Central Server to registry: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            System.err.println("Server interrupted: " + e.getMessage());
            System.exit(1);
        }
    }

    @Override
    public void receiveMsg (MessageInfo msg) {
        if (receivedMessages.isEmpty()) {
            receivedMessages.clear();
            startTime = System.currentTimeMillis();
        }

        System.out.println("[Central Server] Received message " + (msg.getMessageNum()) + " out of " +
                msg.getTotalMessages() + ". Measure = " + msg.getMessage());

        receivedMessages.add(msg);

        if (receivedMessages.size() >= msg.getTotalMessages()) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            System.out.printf("Time to receive all messages: %d ms%n", duration);
            printStats();
        }
    }

    public void printStats() {
        int expectedTotal = receivedMessages.size() > 0 ? receivedMessages.get(0).getTotalMessages() : 0;
        List<Integer> receivedSeqNums = receivedMessages.stream()
            .map(MessageInfo::getMessageNum)
            .collect(Collectors.toList());
        List<Integer> missingSeqNums = new ArrayList<>();
        
        for (int i = 1; i <= expectedTotal; i++) {
            if (!receivedSeqNums.contains(i)) {
                missingSeqNums.add(i);
            }
        }

        System.out.printf("Total Missing Messages = %d out of %d%n", 
            missingSeqNums.size(), expectedTotal);
        if (!missingSeqNums.isEmpty()) {
            System.out.println("Missing message sequence numbers: " + missingSeqNums);
        }

        receivedMessages.clear();
    }
}
