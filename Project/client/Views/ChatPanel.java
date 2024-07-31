package Project.Client.Views;

import Project.Client.CardView;
import Project.Client.Client;
import Project.Client.Interfaces.ICardControls;
import Project.Common.LoggerUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;



/**
 * ChatPanel represents the main chat interface where messages can be sent and
 * received.
 */
//st278 and 07/22/24
public class ChatPanel extends JPanel {
    private JPanel chatArea = null;
    private UserListPanel userListPanel;
    private final float CHAT_SPLIT_PERCENT = 0.7f;


    private JButton exportButton;

    /**
     * Constructor to create the ChatPanel UI.
     * 
     * @param controls The controls to manage card transitions.
     */
    public ChatPanel(ICardControls controls) {
        super(new BorderLayout(10, 10));

        JPanel chatContent = new JPanel(new GridBagLayout());
        chatContent.setAlignmentY(Component.TOP_ALIGNMENT);

        JPanel input = new JPanel();
        input.setLayout(new BoxLayout(input, BoxLayout.X_AXIS));
        input.setBorder(new EmptyBorder(5, 5, 5, 5));


        JTextField textValue = new JTextField();
        input.add(textValue);

        JButton button = new JButton("Send");
        
        exportButton = new JButton("Export Chat");
        exportButton.addActionListener(e -> exportChatHistory());
        input.add(exportButton);


        // Wraps a viewport to provide scroll capabilities
        JScrollPane scroll = new JScrollPane(chatContent);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        chatArea = chatContent;

        userListPanel = new UserListPanel();

        // JSplitPane setup with chat on the left and user list on the right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, userListPanel);
        splitPane.setResizeWeight(CHAT_SPLIT_PERCENT); // Allocate % space to the chat panel initially

        // Enforce splitPane split
        this.addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(CHAT_SPLIT_PERCENT));
            }

            @Override
            public void componentMoved(ComponentEvent e) {
            }

            @Override
            public void componentShown(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(CHAT_SPLIT_PERCENT));
            }

            @Override
            public void componentHidden(ComponentEvent e) {
            }
        });


        //st278 and 07/27/24



        // Allows submission with the enter key instead of just the button click
        textValue.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    button.doClick();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
        });

        button.addActionListener((event) -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    String text = textValue.getText().trim();
                    if (!text.isEmpty()) {
                        Client.INSTANCE.sendMessage(text);
                        textValue.setText(""); // Clear the original text
                    }
                } catch (NullPointerException | IOException e) {
                    LoggerUtil.INSTANCE.severe("Error sending message", e);
                }
            });
        });

        input.add(button);
        

        this.add(splitPane, BorderLayout.CENTER);
        this.add(input, BorderLayout.SOUTH);

        this.setName(CardView.CHAT.name());
        controls.addPanel(CardView.CHAT.name(), this);

        chatArea.addContainerListener(new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (chatArea.isVisible()) {
                        chatArea.revalidate();
                        chatArea.repaint();
                    }
                });
            }

            @Override
            public void componentRemoved(ContainerEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (chatArea.isVisible()) {
                        chatArea.revalidate();
                        chatArea.repaint();
                    }
                });
            }
        });

        // Add vertical glue to push messages to the top
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; // Column index 0
        gbc.gridy = GridBagConstraints.RELATIVE; // Automatically move to the next row
        gbc.weighty = 1.0; // Give extra space vertically to this component
        gbc.fill = GridBagConstraints.BOTH; // Fill both horizontally and vertically
        chatArea.add(Box.createVerticalGlue(), gbc);
    }

    /**
     * Adds a user to the user list.
     * 
     * @param clientId   The ID of the client.
     * @param clientName The name of the client.
     */
    public void addUserListItem(long clientId, String clientName) {
        SwingUtilities.invokeLater(() -> userListPanel.addUserListItem(clientId, clientName));
    }

    /**
     * Removes a user from the user list.
     * 
     * @param clientId The ID of the client to be removed.
     */
    public void removeUserListItem(long clientId) {
        SwingUtilities.invokeLater(() -> userListPanel.removeUserListItem(clientId));
    }

    /**
     * Clears the user list.
     */
    public void clearUserList() {
        SwingUtilities.invokeLater(() -> userListPanel.clearUserList());
    }

    /**
     * Adds a message to the chat area.
     * 
     * @param text The text of the message.
     */





     

     //st278 and 07/24/24
    public void addText(String text) {
        SwingUtilities.invokeLater(() -> {
            JEditorPane textContainer = new JEditorPane("text/html", "<html>" + text + "</html>");
            textContainer.setEditable(false);
            textContainer.setBorder(BorderFactory.createEmptyBorder());
            textContainer.setOpaque(false);
            textContainer.setBackground(new Color(0, 0, 0, 0));
            

            // Account for the width of the vertical scrollbar
            JScrollPane parentScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, chatArea);
            int scrollBarWidth = parentScrollPane.getVerticalScrollBar().getPreferredSize().width;

            // Adjust the width of the text container
            int availableWidth = chatArea.getWidth() - scrollBarWidth - 10; // Subtract an additional padding
            textContainer.setSize(new Dimension(availableWidth, Integer.MAX_VALUE));
            Dimension d = textContainer.getPreferredSize();
            textContainer.setPreferredSize(new Dimension(availableWidth, d.height));
            // Remove background and border
            textContainer.setOpaque(false);
            textContainer.setBorder(BorderFactory.createEmptyBorder());
            textContainer.setBackground(new Color(0, 0, 0, 0));

            // GridBagConstraints settings for each message
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0; // Column index 0
            gbc.gridy = GridBagConstraints.RELATIVE; // Automatically move to the next row
            gbc.weightx = 1; // Let the component grow horizontally to fill the space
            gbc.fill = GridBagConstraints.HORIZONTAL; // Fill horizontally
            gbc.insets = new Insets(0, 0, 5, 0); // Add spacing between messages

            chatArea.add(textContainer, gbc);
            chatArea.revalidate();
            chatArea.repaint();

            // Scroll down on new message
            SwingUtilities.invokeLater(() -> {
                JScrollBar vertical = parentScrollPane.getVerticalScrollBar();
                vertical.setValue(vertical.getMaximum());
            });
        });
    }



    //st278 and 07/27/24
    private void exportChatHistory() {
        StringBuilder chatHistory = new StringBuilder();
        
        for (Component component : chatArea.getComponents()) {
            if (component instanceof JEditorPane) {
                JEditorPane textContainer = (JEditorPane) component;
                chatHistory.append(textContainer.getText()).append("\n");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String fileName = "chat_history_" + now.format(formatter) + ".txt";

        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(chatHistory.toString());
            JOptionPane.showMessageDialog(this, "Chat history exported to " + fileName, "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error exporting chat history: " + ex.getMessage(), "Export Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

}