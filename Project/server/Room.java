package Project.Server;

import Project.Common.LoggerUtil;
import Project.Common.RollPayload;
import Project.Common.TextFX;
import java.util.concurrent.ConcurrentHashMap;
// */
public class Room implements AutoCloseable{
    private String name;// unique name of the Room
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
        if (!isRunning) { // block action if Room isn't running
            return;
        }

        // Note: any desired changes to the message must be done before this section
        String formattedMessage = processTextFormatting(message);

        long senderId = sender == null ? ServerThread.DEFAULT_CLIENT_ID : sender.getClientId();

        info(String.format("sending message to %s recipients: %s", clientsInRoom.size(), formattedMessage));
        clientsInRoom.values().removeIf(client -> {
            
            // st278 and 07/24/24
            if (client.isUserMuted(sender.getClientName())) {
                LoggerUtil.INSTANCE.info("Message from " + sender.getClientName() + " skipped for " + client.getClientName() + " due to being muted");
                return false; 
            }
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

    // end receive data from ServerThread








    protected void handleRoll(ServerThread sender, RollPayload payload) {
        int result;
        String formattedMessage;
    
        if (payload.isSimpleRoll()) {
            result = (int) (Math.random() * payload.getSides()) + 1;
            formattedMessage = String.format("%s rolled %d and got %d", sender.getClientName(), payload.getSides(), result);
        } else {
            int total = 0;
            for (int i = 0; i < payload.getQuantity(); i++) {
                total += (int) (Math.random() * payload.getSides()) + 1;
            }
            formattedMessage = String.format("%s rolled %dd%d and got %d", sender.getClientName(), payload.getQuantity(), payload.getSides(), total);
        }
        // st278 and 07/24/24
        String message = String.format("ROLL: %s", formattedMessage);
        sendMessage(sender, message);
    }

    protected void handleFlip(ServerThread sender) {
        boolean isHeads = Math.random() < 0.5;
        String result = isHeads ? "heads" : "tails";
        String message = String.format("FLIP: %s flipped a coin and got %s", sender.getClientName(), result);
        sendMessage(sender, message);
    }














    //st278 and 07/24/24
    private String processTextFormatting(String message) {
        if (message.startsWith("ROLL:") || message.startsWith("FLIP:")) {
            // Don't apply text formatting to roll and flip results
            return message;
        }
    
        // Bold: **text**
        message = message.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
    
        // Italic: *text*
        message = message.replaceAll("\\*(.*?)\\*", "<i>$1</i>");
    
        // Underline: __text__
        message = message.replaceAll("_(.*?)_", "<u>$1</u>");
    
        // Colors
        message = message.replaceAll("#r(.*?)r#", "<font color='red'>$1</font>");
        message = message.replaceAll("#g(.*?)g#", "<font color='green'>$1</font>");
        message = message.replaceAll("#b(.*?)b#", "<font color='blue'>$1</font>");
    
        // Hex color: #[0-9A-Fa-f]{6}(.*?)#
        message = message.replaceAll("#([0-9A-Fa-f]{6})(.*?)#", "<font color='#$1'>$2</font>");
    
        return message;
    }


















// st278 and 07/24/24
public void sendPrivateMessage(ServerThread sender, long targetId, String message) {
    ServerThread target = clientsInRoom.get(targetId);
    if (target != null) {
        String formattedMessage = String.format("[Private] %s: %s", sender.getClientName(), message);
        
        sender.sendMessage(sender.getClientId(), formattedMessage);
        
        if (!target.isUserMuted(sender.getClientName())) {
            target.sendMessage(sender.getClientId(), formattedMessage);
        } else {
            LoggerUtil.INSTANCE.info(String.format("Private message from %s to %s was skipped due to mute", 
                                     sender.getClientName(), target.getClientName()));
        }
    } else {
        sender.sendMessage(ServerThread.DEFAULT_CLIENT_ID, "Error: User not found in this room.");
    }
}






    //st278 and 07/24/24
    protected void handleMute(ServerThread sender, long targetId) {
        ServerThread target = clientsInRoom.get(targetId);
        if (target != null) {
            if (sender.addMutedUser(target.getClientName())) {
                sender.sendMessage("You have muted " + target.getClientName());
            } else {
                sender.sendMessage(target.getClientName() + " is already muted");
            }
        } else {
            sender.sendMessage("User not found in this room.");
        }
    }

    protected void handleUnmute(ServerThread sender, long targetId) {
        ServerThread target = clientsInRoom.get(targetId);
        if (target != null) {
            if (sender.removeMutedUser(target.getClientName())) {
                sender.sendMessage("You have unmuted " + target.getClientName());
            } else {
                sender.sendMessage(target.getClientName() + " was not muted");
            }
        } else {
            sender.sendMessage("User not found in this room.");
        }
    }








}
