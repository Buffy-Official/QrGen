package com.qrgen;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

class QRCodeStudio {

    private static BitMatrix lastMatrix = null;
    private static BufferedImage lastImage = null;
    private static Color qrColor = Color.BLACK;
    private static Color bgColor = Color.WHITE;
    private static boolean darkMode = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(QRCodeStudio::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("QR Code Generator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 700);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(Color.decode("#f7f8fa"));

        // Top Panel
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(new EmptyBorder(15, 15, 10, 15));
        topPanel.setBackground(Color.decode("#f7f8fa"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        JTextField textField = new JTextField();
        textField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1; gbc.gridwidth = 3;
        topPanel.add(textField, gbc);

        String[] types = {"Plain Text", "URL", "Email", "Phone", "SMS"};
        JComboBox<String> typeBox = new JComboBox<>(types);
        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0; gbc.gridwidth = 1;
        topPanel.add(typeBox, gbc);

        JButton darkModeButton = new JButton("Dark Mode");
        gbc.gridx = 3; gbc.gridy = 1; topPanel.add(darkModeButton, gbc);

        // Center Panel
        JLabel qrLabel = new JLabel("", SwingConstants.CENTER);
        qrLabel.setBorder(new EmptyBorder(20, 20, 20, 20));
        qrLabel.setOpaque(true);
        qrLabel.setBackground(bgColor);
        qrLabel.setPreferredSize(new java.awt.Dimension(400, 400));
        qrLabel.setTransferHandler(new TransferHandler("icon"));

        // Bottom Panel
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        bottomPanel.setBackground(Color.decode("#f7f8fa"));

        JButton saveButton = new JButton("Save QR");
        JButton qrColorButton = new JButton("QR Color");
        JButton bgColorButton = new JButton("Background");
        JButton clipboardButton = new JButton("Copy QR");

        bottomPanel.add(qrColorButton);
        bottomPanel.add(bgColorButton);
        bottomPanel.add(clipboardButton);
        bottomPanel.add(saveButton);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(qrLabel, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        styleButton(saveButton, qrColorButton, bgColorButton, clipboardButton, darkModeButton);

        textField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateQRCode() {
                String rawText = textField.getText();
                if (!rawText.isEmpty()) {
                    String processed = preprocessText(rawText, (String) Objects.requireNonNull(typeBox.getSelectedItem()));
                    generateQRCode(processed, qrLabel);
                } else {
                    qrLabel.setIcon(null);
                    lastMatrix = null;
                }
            }
            public void insertUpdate(DocumentEvent e) { updateQRCode(); }
            public void removeUpdate(DocumentEvent e) { updateQRCode(); }
            public void changedUpdate(DocumentEvent e) { updateQRCode(); }
        });

        // Save QR
        saveButton.addActionListener(e -> {
            if (lastImage != null) {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Save QR Code");
                chooser.setSelectedFile(new File("QRCode.png"));
                chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                    public boolean accept(File f) { return f.isDirectory() || f.getName().toLowerCase().endsWith(".png"); }
                    public String getDescription() { return "PNG Image (*.png)"; }
                });
                if (chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    Path path = chooser.getSelectedFile().toPath();
                    try { javax.imageio.ImageIO.write(lastImage, "PNG", path.toFile()); }
                    catch (IOException ex) { JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage()); }
                }
            }
        });

        // Clipboard Function
        clipboardButton.addActionListener(e -> {
            if (lastImage != null) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(lastImage), null);
                JOptionPane.showMessageDialog(frame, "QR copied to clipboard!");
            }
        });

        // Color Pickers
        qrColorButton.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(frame, "QR Color", qrColor);
            if (chosen != null) { qrColor = chosen; updateQRCodeFromLast(qrLabel); }
        });
        bgColorButton.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(frame, "Background Color", bgColor);
            if (chosen != null) { bgColor = chosen; updateQRCodeFromLast(qrLabel); }
        });

        // Dark Mode
        darkModeButton.addActionListener(e -> {
            darkMode = !darkMode;
            Color bg = darkMode ? Color.decode("#2b2b2b") : Color.decode("#f7f8fa");
            Color panel = darkMode ? Color.decode("#3b3b3b") : Color.decode("#f7f8fa");
            frame.getContentPane().setBackground(bg);
            topPanel.setBackground(panel);
            bottomPanel.setBackground(panel);
            qrLabel.setBackground(panel);
        });

        qrLabel.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) { updateQRCodeFromLast(qrLabel); }
        });

        frame.setVisible(true);
    }

    private static String preprocessText(String text, String type) {
        text = text.trim();
        switch(type) {
            case "URL": if (!text.matches("^(?i)(https?://).*")) text = "https://" + text; break;
            case "Email": text = "mailto:" + text; break;
            case "Phone": text = "tel:" + text; break;
            case "SMS": text = "sms:" + text; break;
        }
        return text;
    }

    private static void generateQRCode(String text, JLabel qrLabel) {
        try {
            int size = Math.min(qrLabel.getWidth(), qrLabel.getHeight());
            QRCodeWriter writer = new QRCodeWriter();
            lastMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);

            BufferedImage qrImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    qrImage.setRGB(x, y, lastMatrix.get(x, y) ? qrColor.getRGB() : bgColor.getRGB());

            lastImage = qrImage;
            qrLabel.setIcon(new ImageIcon(qrImage));
        } catch (WriterException ignored) {}
    }

    private static void updateQRCodeFromLast(JLabel qrLabel) {
        if (lastMatrix != null) generateQRCodeFromMatrix(qrLabel, lastMatrix);
    }

    private static void generateQRCodeFromMatrix(JLabel qrLabel, BitMatrix matrix) {
        int size = Math.min(qrLabel.getWidth(), qrLabel.getHeight());
        BufferedImage qrImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < size; x++)
            for (int y = 0; y < size; y++)
                qrImage.setRGB(x, y, matrix.get(x, y) ? qrColor.getRGB() : bgColor.getRGB());
        lastImage = qrImage;
        qrLabel.setIcon(new ImageIcon(qrImage));
    }

    private static void styleButton(JButton... buttons) {
        for (JButton btn : buttons) {
            btn.setFocusPainted(false);
            btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            btn.setBackground(Color.decode("#4a90e2"));
            btn.setForeground(Color.WHITE);
            btn.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            addHoverEffect(btn);
        }
    }

    private static void addHoverEffect(JButton button) {
        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { button.setBackground(Color.decode("#357ab8")); }
            public void mouseExited(MouseEvent e) { button.setBackground(Color.decode("#4a90e2")); }
        });
    }

    private record ImageSelection(Image image) implements Transferable {
        public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
        public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
        public Object getTransferData(DataFlavor flavor) { return image; }
    }
}