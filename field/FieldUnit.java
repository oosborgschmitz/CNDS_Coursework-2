package field;
/*
 * Updated on Feb 2025
 */
import centralserver.ICentralServer;
import common.MessageInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

 /* You can add/change/delete class attributes if you wish.
  *
  * You can also add other methods and change the implementation of the methods
  * provided, as long as you DO NOT CHANGE the interface.
  */

public class FieldUnit implements IFieldUnit {
    private ICentralServer central_server;
    private List<MessageInfo> receivedMessages;
    private List<Float> movingAverages;

    /* Note: Could you discuss in one line of comment you think can be
     * an appropriate size for buffsize? (used to init DatagramPacket?)
     */
    private static final int buffsize = 2048;
    private int timeout = 50000;

    public FieldUnit () {
        /* TODO: Initialise data structures */
        receivedMessages = new ArrayList<>();
        movingAverages = new ArrayList<>();
    }

    @Override
    public void addMessage (MessageInfo msg) {
      /* TODO: Save received message in receivedMessages */
        receivedMessages.add(msg);
        System.out.printf("[Field Unit] Message %d out of %d received. Value = %f%n", 
            msg.getMessageNum(), msg.getTotalMessages(), msg.getMessage());
    }

    @Override
    public void sMovingAverage (int k) {
        List<Float> newAverages = new ArrayList<>();
        
        for (int i = 0; i < receivedMessages.size(); i++) {
            if (i < k) {
                // For i < 7, just use the measurement itself
                newAverages.add(receivedMessages.get(i).getMessage());
            } else {
                // For i >= 7, compute the moving average using previous k points
                float sum = 0;
                for (int j = i - k + 1; j <= i; j++) {
                    sum += receivedMessages.get(j).getMessage();
                }
                newAverages.add(sum / k);
            }
        }
        
        movingAverages = newAverages;
    }

    @Override
    public void receiveMeasures(int port, int timeout) throws SocketException {
        this.timeout = timeout;
        DatagramSocket socket = null;
        long startTime = -1;
        int expectedTotal = -1;

        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(timeout);
            System.out.printf("[Field Unit] Listening on port: %d%n", port);
        } catch (SocketException e) {
            System.err.println("Error creating socket: " + e.getMessage());
            throw e;
        }

        boolean listen = true;

        while (listen) {
            byte[] receiveBuffer = new byte[buffsize];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);

            try {
                socket.receive(receivePacket);
                String received = new String(receivePacket.getData(), 0, receivePacket.getLength());
                MessageInfo msg = new MessageInfo(received);

                if (startTime == -1) {
                    startTime = System.currentTimeMillis();
                }

                if (expectedTotal == -1) {
                    expectedTotal = msg.getTotalMessages();
                }

                addMessage(msg);

                if (receivedMessages.size() >= expectedTotal) {
                    listen = false;
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout waiting for messages");
                listen = false;
            } catch (Exception e) {
                System.err.println("Error receiving message: " + e.getMessage());
            }
        }

        if (socket != null) {
            socket.close();
        }

        if (startTime != -1) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            System.out.printf("Time to receive all messages: %d ms%n", duration);
        }
    }

    public static void main (String[] args) throws SocketException {
        if (args.length < 2) {
            System.out.println("Usage: ./fieldunit.sh <UDP rcv port> <RMI server HostName/IPAddress>");
            return;
        }
        
        /* TODO: Parse arguments */
        int port = Integer.parseInt(args[0]);
        String rmiAddress = args[1];

        /* TODO: Construct Field Unit Object */
        FieldUnit fieldUnit = new FieldUnit();

        /* TODO: Call initRMI on the Field Unit Object */
        fieldUnit.initRMI(rmiAddress);

        while (true) {
            try {
                /* TODO: Wait for incoming transmission */
                fieldUnit.receiveMeasures(port, 50000);

                /* TODO: Compute Averages - call sMovingAverage()
                    on Field Unit object */
                fieldUnit.sMovingAverage(7);

                /* TODO: Send data to the Central Server via RMI */
                fieldUnit.sendAverages();

                /* TODO: Compute and print stats */
                fieldUnit.printStats();

            } catch (Exception e) {
                System.err.println("Error in main loop: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    @Override
    public void initRMI (String address) {
        if (System.getProperty("java.version").startsWith("1.")) {
            System.setSecurityManager(new SecurityManager());
        }

        try {
            // Set the RMI hostname to the local machine's IP
            String localHost = java.net.InetAddress.getLocalHost().getHostAddress();
            System.setProperty("java.rmi.server.hostname", localHost);
            
            Registry registry = LocateRegistry.getRegistry(address, 1099);
            central_server = (ICentralServer) registry.lookup("CentralServer");
        } catch (RemoteException | NotBoundException | java.net.UnknownHostException e) {
            System.err.println("[Field Unit] Error connecting to RMI server: " + e.getMessage());
            System.err.println("[Field Unit] Make sure the Central Server is running on " + address);
            System.exit(1);
        }
    }

    @Override
    public void sendAverages () {
        if (movingAverages.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < movingAverages.size(); i++) {
            try {
                MessageInfo msg = new MessageInfo(movingAverages.size(), i + 1, movingAverages.get(i));
                central_server.receiveMsg(msg);
            } catch (RemoteException e) {
                System.err.println("Error sending average to central server: " + e.getMessage());
            }
        }
    }

    @Override
    public void printStats () {
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

        receivedMessages.clear();
    }
}
