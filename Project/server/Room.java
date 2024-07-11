package Project.server;
import java.util.concurrent.ConcurrentHashMap;

import Project.common.LoggerUtil;
import Project.common.Payload;
import Project.common.RollPayload;



public class Room implements AutoCloseable{
    private String name;// unique name of the Room
    private static final String BOLD_SYMBOL = "**";
    private static final String ITALIC_SYMBOL = "_";
    private static final String UNDERLINE_SYMBOL = "__";
    private static final String COLOR_START_SYMBOL = "#";
    private static final String COLOR_END_SYMBOL = "#";
    protected volatile boolean isRunning = false;
    private ConcurrentHashMap<Long, ServerThread> clientsInRoom = new ConcurrentHashMap<Long, ServerThread>();

    public final static String LOBBY = "lobby";

    private void info(String message) {
        LoggerUtil.INSTANCE.info(String.format("Room[%s]: %s", name, message));
    }

    public Room(String name) {
        this.name = name;
        isRunning = true;
        info("created");
    }

    public String getName() {
        return this.name;
    }

    protected synchronized void addClient(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        if (clientsInRoom.containsKey(client.getClientId())) {
            info("Attempting to add a client that already exists in the room");
            return;
        }
        clientsInRoom.put(client.getClientId(), client);
        client.setCurrentRoom(this);

        // notify clients of someone joining
        sendRoomStatus(client.getClientId(), client.getClientName(), true);
        // sync room state to joiner
        syncRoomList(client);

        info(String.format("%s[%s] joined the Room[%s]", client.getClientName(), client.getClientId(), getName()));

    }

    protected synchronized void removedClient(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        // notify remaining clients of someone leaving
        // happen before removal so leaving client gets the data
        sendRoomStatus(client.getClientId(), client.getClientName(), false);
        clientsInRoom.remove(client.getClientId());
        LoggerUtil.INSTANCE.fine("Clients remaining in Room: " + clientsInRoom.size());

        info(String.format("%s[%s] left the room", client.getClientName(), client.getClientId(), getName()));

        autoCleanup();

    }

    //st278 and 07/08/2024
    protected void handleRoll(ServerThread sender, RollPayload payload) {
        LoggerUtil.INSTANCE.info("Room handling roll command: " + payload);
        int result;
        String message;
    
        if (payload.getRollType().equals("single")) {
            result = (int) (Math.random() * payload.getMax()) + 1;
            message = String.format("%s rolled %d and got %d", payload.getClientId(), payload.getMax(), result);
        } else {
            result = 0;
            for (int i = 0; i < payload.getNumDice(); i++) {
                result += (int) (Math.random() * payload.getSides()) + 1;
            }
            message = String.format("%s rolled %dd%d and got %d", payload.getClientId(), payload.getNumDice(), payload.getSides(), result);
        }
    
        LoggerUtil.INSTANCE.info("Sending roll result: " + message);
        sendMessage(sender, message);
    }
    
    protected void handleFlip(ServerThread sender, Payload payload) {
        LoggerUtil.INSTANCE.info("Room handling flip command: " + payload);
        String result = Math.random() < 0.5 ? "heads" : "tails";
        String message = String.format("%s flipped a coin and got %s", payload.getClientId(), result);
        LoggerUtil.INSTANCE.info("Sending flip result: " + message);
        sendMessage(sender, message);
    }
    /**
     * Takes a ServerThread and removes them from the Server
     * Adding the synchronized keyword ensures that only one thread can execute
     * these methods at a time,
     * preventing concurrent modification issues and ensuring thread safety
     * 
     * @param client
     */
    protected synchronized void disconnect(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        long id = client.getClientId();
        sendDisconnect(client);
        client.disconnect();
        // removedClient(client); // <-- use this just for normal room leaving
        clientsInRoom.remove(client.getClientId());
        LoggerUtil.INSTANCE.fine("Clients remaining in Room: " + clientsInRoom.size());
        
        // Improved logging with user data
        info(String.format("%s[%s] disconnected", client.getClientName(), id));
        autoCleanup();
    }

    protected synchronized void disconnectAll() {
        info("Disconnect All triggered");
        if (!isRunning) {
            return;
        }
        clientsInRoom.values().removeIf(client -> {
            disconnect(client);
            return true;
        });
        info("Disconnect All finished");
        autoCleanup();
    }

    /**
     * Attempts to close the room to free up resources if it's empty
     */
    private void autoCleanup() {
        if (!Room.LOBBY.equalsIgnoreCase(name) && clientsInRoom.isEmpty()) {
            close();
        }
    }

    public void close() {
        // attempt to gracefully close and migrate clients
        if (!clientsInRoom.isEmpty()) {
            sendMessage(null, "Room is shutting down, migrating to lobby");
            info(String.format("migrating %s clients", name, clientsInRoom.size()));
            clientsInRoom.values().removeIf(client -> {
                Server.INSTANCE.joinRoom(Room.LOBBY, client);
                return true;
            });
        }
        Server.INSTANCE.removeRoom(this);
        isRunning = false;
        clientsInRoom.clear();
        info(String.format("closed", name));
    }

    // send/sync data to client(s)

    /**
     * Sends to all clients details of a disconnect client
     * @param client
     */
    protected synchronized void sendDisconnect(ServerThread client) {
        info(String.format("sending disconnect status to %s recipients", clientsInRoom.size()));
        clientsInRoom.values().removeIf(clientInRoom -> {
            boolean failedToSend = !clientInRoom.sendDisconnect(client.getClientId(), client.getClientName());
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }

    /**
     * Syncs info of existing users in room with the client
     * 
     * @param client
     */
    protected synchronized void syncRoomList(ServerThread client) {

        clientsInRoom.values().forEach(clientInRoom -> {
            if (clientInRoom.getClientId() != client.getClientId()) {
                client.sendClientSync(clientInRoom.getClientId(), clientInRoom.getClientName());
            }
        });
    }

    /**
     * Syncs room status of one client to all connected clients
     * 
     * @param clientId
     * @param clientName
     * @param isConnect
     */
    protected synchronized void sendRoomStatus(long clientId, String clientName, boolean isConnect) {
        info(String.format("sending room status to %s recipients", clientsInRoom.size()));
        clientsInRoom.values().removeIf(client -> {
            boolean failedToSend = !client.sendRoomAction(clientId, clientName, getName(), isConnect);
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }

    /**
     * Sends a basic String message from the sender to all connectedClients
     * Internally calls processCommand and evaluates as necessary.
     * Note: Clients that fail to receive a message get removed from
     * connectedClients.
     * Adding the synchronized keyword ensures that only one thread can execute
     * these methods at a time,
     * preventing concurrent modification issues and ensuring thread safety
     * 
     * @param message
     * @param sender  ServerThread (client) sending the message or null if it's a
     *                server-generated message
     */
    protected synchronized void sendMessage(ServerThread sender, String message) {
        if (!isRunning) {
            return;
        }
        String formattedMessage = processTextFormatting(message);
        long senderId = sender == null ? ServerThread.DEFAULT_CLIENT_ID : sender.getClientId();
        info(String.format("sending message to %s recipients: %s", clientsInRoom.size(), formattedMessage));
        clientsInRoom.values().removeIf(client -> {
            boolean failedToSend = !client.sendMessage(senderId, formattedMessage);
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }
    // end send data to client(s)

    // receive data from ServerThread
    
    protected void handleCreateRoom(ServerThread sender, String room) {
        if (Server.INSTANCE.createRoom(room)) {
            Server.INSTANCE.joinRoom(room, sender);
        } else {
            sender.sendMessage(String.format("Room %s already exists", room));
        }
    }

    protected void handleJoinRoom(ServerThread sender, String room) {
        if (!Server.INSTANCE.joinRoom(room, sender)) {
            sender.sendMessage(String.format("Room %s doesn't exist", room));
        }
    }

    protected void handleListRooms(ServerThread sender, String roomQuery){
        sender.sendRooms(Server.INSTANCE.listRooms(roomQuery));
    }

    protected void clientDisconnect(ServerThread sender) {
        disconnect(sender);
    }

    // st278 and 07/08/2024
    private String processTextFormatting(String message) {
        StringBuilder processedMessage = new StringBuilder();
        boolean isBold = false;
        boolean isItalic = false;
        boolean isUnderline = false;
        String currentColor = null;
    
        for (int i = 0; i < message.length(); i++) {
            if (message.startsWith(BOLD_SYMBOL, i)) {
                processedMessage.append(isBold ? "</b>" : "<b>");
                isBold = !isBold;
                i += BOLD_SYMBOL.length() - 1;
            } else if (message.startsWith(ITALIC_SYMBOL, i)) {
                processedMessage.append(isItalic ? "</i>" : "<i>");
                isItalic = !isItalic;
                i += ITALIC_SYMBOL.length() - 1;
            } else if (message.startsWith(UNDERLINE_SYMBOL, i)) {
                processedMessage.append(isUnderline ? "</u>" : "<u>");
                isUnderline = !isUnderline;
                i += UNDERLINE_SYMBOL.length() - 1;
            } else if (message.startsWith(COLOR_START_SYMBOL, i)) {
                int colorEnd = message.indexOf(COLOR_END_SYMBOL, i + 1);
                if (colorEnd != -1) {
                    String color = message.substring(i + 1, colorEnd);
                    if (currentColor != null) {
                        processedMessage.append("</").append(currentColor).append(">");
                    }
                    currentColor = color;
                    processedMessage.append("<").append(color).append(">");
                    i = colorEnd;
                } else {
                    processedMessage.append(message.charAt(i));
                }
            } else {
                processedMessage.append(message.charAt(i));
            }
        }
    
        // Close any open tags
        if (isBold) processedMessage.append("</b>");
        if (isItalic) processedMessage.append("</i>");
        if (isUnderline) processedMessage.append("</u>");
        if (currentColor != null) processedMessage.append("</").append(currentColor).append(">");
    
        return processedMessage.toString();
    }
    // end receive data from ServerThread
}