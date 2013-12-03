package mcserver;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.JButton;
import javax.swing.JFrame;

public class MCServer implements PlaylistListener {

    public static final int DISCOVERY_PORT = 49155;
    public static final int VOTING_PORT = 50032;
    private Thread discoveryThread = null;
    private Thread votingThread = null;
    private Thread votingHandler = null;
    private static final Logger logger = Logger.getLogger(MCServer.class.getName());
    private Voting voting;

    static {
        try {
            File file = new File("logs");
            file.mkdir();
            FileHandler handler = new FileHandler("logs/" + MCServer.class.getSimpleName() + ".log");
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Disabling logging to file", ex);
        } catch (SecurityException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public static void main(String[] args) throws SocketException, IOException, InterruptedException {
        final MCServer server = new MCServer();

        server.start();

        final JFrame frame = new JFrame("Server status");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JButton button = new JButton("Shutdown server");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                server.shutdown();
                frame.dispose();
            }
        });
        frame.add(button);
        frame.pack();
        frame.setVisible(true);

    }

    public MCServer() {
        discoveryThread = new Thread(new DiscoveryService());
        voting = new Voting(4);
    }

    //Starts the discovery thread and begins voting
    public void start() {
        discoveryThread.start();
        try {
            //For testing on a single machine, client needs time to receive discovery
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        withinThreshold();
    }

    //Kills all the child threads. Order is important here.
    public void shutdown() {
        logger.log(Level.INFO, "Server shutting down");
        if (votingHandler != null) {
            votingHandler.interrupt();
            try {
                votingHandler.join();
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        voting.shutdown();
        if (votingThread != null) {
            votingThread.interrupt();
        }
        discoveryThread.interrupt();
    }

    //Triggers a new vote
    @Override
    public void withinThreshold() {
        if (votingHandler != null) {
            votingHandler.interrupt();
        }
        votingHandler = new Thread(new VotingHandler());
        votingHandler.start();
    }

    public class VotingHandler implements Runnable {
        /*Broadcasts the message to trigger client voting, runs the Voting code to start a new playlist.
         * Only a thread so the Playlist manager thread doesn't have to start a new instance of itself through withinThreshold
         */

        @Override
        public void run() {
            //Open for voting
            logger.log(Level.INFO, "Beginning voting");
            voting.beginVoting();
            VotingReceiver votingService = new VotingReceiver();
            if (votingThread != null && !votingThread.isInterrupted()) {
                votingThread.interrupt();
            }
            votingThread = new Thread(votingService);
            votingThread.start();
            try {
                /*If client/server are on the same machine, broadcast may arrive at client
                 * before server opens receive socket. This is not relevant on separate machines
                 * but it avoids an extra voting cycle on single machine tests.
                 */
                Thread.sleep(200);
                //Broadcast start of voting period
                try (DatagramSocket socket = new DatagramSocket()) {
                    InetSocketAddress address = new InetSocketAddress("255.255.255.255", VOTING_PORT);
                    byte[] message = new byte[0];
                    socket.setBroadcast(true);
                    DatagramPacket packet = new DatagramPacket(message, message.length);
                    packet.setSocketAddress(address);
                    socket.send(packet);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, null, e);
                }
                Thread.sleep(10000); //Run voting for 10 seconds
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, "Voting shutting down");
            }
            votingThread.interrupt();
            voting.endVoting(MCServer.this);
        }
    }

    private class VotingReceiver implements Runnable {
        /*
         * Receives votes from clients, passes them on to Voting
         * Notice that this has to happen over TCP instead of UDP to make voting reliable
         */
        public void run() {
            try (ServerSocket serverSocket = new ServerSocket()) {
                InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), VOTING_PORT);
                serverSocket.bind(address);
                serverSocket.setSoTimeout(200);
                while (!Thread.currentThread().isInterrupted()) {
                    //TODO: May need to thread this further if client submissions get larger
                    try (Socket socket = serverSocket.accept()) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String s = null;
                        while ((s = reader.readLine()) != null) {
                            voting.addVote(s);
                            logger.log(Level.FINE, "Received vote for " + s);
                        }
                    } catch (SocketTimeoutException e) {
                        logger.log(Level.FINE, "No votes received in 200ms interval");
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    private class DiscoveryService implements Runnable {

        //Broadcasts a packet containing server address to the LAN every second
        public void run() {
            try (DatagramSocket socket = new DatagramSocket()) {
                InetSocketAddress address = new InetSocketAddress("255.255.255.255", DISCOVERY_PORT);
                byte[] message = new byte[0];
                socket.setBroadcast(true);
                DatagramPacket packet = new DatagramPacket(message, message.length);
                packet.setSocketAddress(address);
                while (true) {
                    socket.send(packet);
                    Thread.sleep(1000);
                }
            } catch (IOException e) {
                //Port is in use, we don't know our own address or we could not send the broadcast for some reason. Either way, discovery won't work.
                logger.log(Level.SEVERE, null, e);
            } catch (InterruptedException e) {
                logger.log(Level.INFO, "Discovery service shutting down");
            }
        }
    }
}
