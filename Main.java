import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.*;

public class Main extends JFrame {

    static class Record {
        String id;
        String type;
        int attemptsNeeded;
        Record(String id, String type, int attemptsNeeded) {
            this.id = id;
            this.type = type;
            this.attemptsNeeded = attemptsNeeded;
        }
    }

    private List<Record> data = new ArrayList<>();
    private JLabel fileLabel;
    private JSpinner limitSpinner;
    private JButton loadButton;
    private JButton runButton;
    private JTextArea logArea;
    private ChartPanel chartPanel;
    private JLabel statusLabel;

    public Main() {
        setTitle("Authentication Policy Simulation vs Sandbox");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 700);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(24, 26, 32));

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(20, 20, 20, 20));
        root.setBackground(new Color(24, 26, 32));
        setContentPane(root);

        root.add(buildTopPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildTopPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(24, 26, 32));

        JLabel title = new JLabel("Predict vs Sandbox: Authentication Policy Impact");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Load historical login data, choose a new attempt limit, and compare the predicted outcome to a sandbox replay.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(new Color(160, 165, 175));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
        controls.setBackground(new Color(32, 35, 43));
        controls.setBorder(new EmptyBorder(6, 6, 6, 6));

        loadButton = styledButton("Load Historical Data (CSV)");
        fileLabel = new JLabel("No file loaded");
        fileLabel.setForeground(new Color(160, 165, 175));

        JLabel limitLabel = new JLabel("New attempt limit:");
        limitLabel.setForeground(Color.WHITE);
        limitSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 10, 1));
        limitSpinner.setPreferredSize(new Dimension(60, 28));

        runButton = styledButton("Run Simulator + Sandbox");
        runButton.setEnabled(false);

        controls.add(loadButton);
        controls.add(fileLabel);
        controls.add(Box.createHorizontalStrut(20));
        controls.add(limitLabel);
        controls.add(limitSpinner);
        controls.add(runButton);

        panel.add(title);
        panel.add(Box.createVerticalStrut(4));
        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(12));
        panel.add(controls);

        loadButton.addActionListener(e -> loadFile());
        runButton.addActionListener(e -> runAll());

        return panel;
    }

    private JButton styledButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(88, 101, 242));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(8, 16, 8, 16));
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(24, 26, 32));
        chartPanel = new ChartPanel();
        panel.add(chartPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(24, 26, 32));
        panel.setPreferredSize(new Dimension(980, 190));

        statusLabel = new JLabel("Load a CSV to begin.");
        statusLabel.setForeground(new Color(160, 165, 175));
        statusLabel.setBorder(new EmptyBorder(0, 0, 6, 0));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setBackground(new Color(18, 20, 25));
        logArea.setForeground(new Color(210, 214, 220));
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(50, 54, 63)));

        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void loadFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files", "csv"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        data.clear();
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                data.add(new Record(parts[0].trim(), parts[1].trim(), Integer.parseInt(parts[2].trim())));
            }
            fileLabel.setText(file.getName() + " (" + data.size() + " records)");
            statusLabel.setText("Loaded " + data.size() + " records. Choose a limit and run.");
            runButton.setEnabled(!data.isEmpty());
            chartPanel.setResults(null);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not read file: " + ex.getMessage());
        }
    }

    private void runAll() {
        int limit = (Integer) limitSpinner.getValue();
        long totalUsers = data.stream().filter(r -> r.type.equalsIgnoreCase("USER")).count();
        long totalAttackers = data.stream().filter(r -> r.type.equalsIgnoreCase("ATTACKER")).count();

        long predictedUsersLocked = data.stream()
                .filter(r -> r.type.equalsIgnoreCase("USER") && r.attemptsNeeded > limit)
                .count();
        long predictedAttackersBlocked = data.stream()
                .filter(r -> r.type.equalsIgnoreCase("ATTACKER") && r.attemptsNeeded > limit)
                .count();

        Random rand = new Random();
        long actualUsersLocked = 0;
        long actualAttackersBlocked = 0;
        for (Record r : data) {
            int noise = rand.nextInt(3) - 1;
            int effectiveAttempts = Math.max(1, r.attemptsNeeded + noise);
            if (r.type.equalsIgnoreCase("USER") && effectiveAttempts > limit) actualUsersLocked++;
            if (r.type.equalsIgnoreCase("ATTACKER") && effectiveAttempts > limit) actualAttackersBlocked++;
        }

        chartPanel.setResults(new long[]{predictedUsersLocked, actualUsersLocked, predictedAttackersBlocked, actualAttackersBlocked});

        int userGap = (int) Math.abs(predictedUsersLocked - actualUsersLocked);
        int attackerGap = (int) Math.abs(predictedAttackersBlocked - actualAttackersBlocked);

        StringBuilder sb = new StringBuilder();
        sb.append("Policy tested: lock account after ").append(limit).append(" wrong attempts\n");
        sb.append("Total legit users: ").append(totalUsers).append("  |  Total attacker attempts: ").append(totalAttackers).append("\n\n");
        sb.append("SIMULATOR (predicted, calculated from historical statistics):\n");
        sb.append("  Users locked out:      ").append(predictedUsersLocked).append("\n");
        sb.append("  Attackers blocked:     ").append(predictedAttackersBlocked).append("\n\n");
        sb.append("SANDBOX (actual, replayed with real-world variability):\n");
        sb.append("  Users locked out:      ").append(actualUsersLocked).append("\n");
        sb.append("  Attackers blocked:     ").append(actualAttackersBlocked).append("\n\n");
        sb.append("GAP (how far off the prediction was):\n");
        sb.append("  Users locked out gap:  ").append(userGap).append("\n");
        sb.append("  Attackers blocked gap: ").append(attackerGap).append("\n\n");
        String verdict = (userGap + attackerGap) <= (totalUsers + totalAttackers) * 0.08
                ? "Prediction is CLOSE to reality. This policy type appears trustworthy to simulate."
                : "Prediction is FAR from reality. This policy type should not be trusted from simulation alone.";
        sb.append(verdict).append("\n");

        logArea.setText(sb.toString());
        statusLabel.setText("Done. Limit tested: " + limit + " attempts.");
    }

    static class ChartPanel extends JPanel {
        private long[] results;

        ChartPanel() {
            setBackground(new Color(18, 20, 25));
            setBorder(BorderFactory.createLineBorder(new Color(50, 54, 63)));
        }

        void setResults(long[] results) {
            this.results = results;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (results == null) {
                g2.setColor(new Color(120, 125, 135));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.drawString("Run the simulator to see predicted vs actual results here.", 30, getHeight() / 2);
                return;
            }

            long maxVal = 1;
            for (long v : results) maxVal = Math.max(maxVal, v);

            String[] labels = {"Predicted\nUsers Locked", "Actual\nUsers Locked", "Predicted\nAttackers Blocked", "Actual\nAttackers Blocked"};
            Color[] colors = {
                    new Color(120, 130, 240),
                    new Color(70, 190, 160),
                    new Color(120, 130, 240),
                    new Color(70, 190, 160)
            };

            int chartW = getWidth() - 100;
            int chartH = getHeight() - 130;
            int baseY = getHeight() - 70;
            int barWidth = chartW / (results.length * 2);
            int gap = barWidth;

            g2.setColor(new Color(80, 85, 95));
            g2.drawLine(60, baseY, getWidth() - 40, baseY);

            for (int i = 0; i < results.length; i++) {
                int x = 60 + gap / 2 + i * (barWidth + gap);
                int barHeight = (int) ((results[i] / (double) maxVal) * chartH);
                int y = baseY - barHeight;

                g2.setColor(colors[i]);
                g2.fillRoundRect(x, y, barWidth, barHeight, 8, 8);

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                String valStr = String.valueOf(results[i]);
                int strW = g2.getFontMetrics().stringWidth(valStr);
                g2.drawString(valStr, x + barWidth / 2 - strW / 2, y - 8);

                g2.setColor(new Color(170, 175, 185));
                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                String[] lines = labels[i].split("\n");
                for (int l = 0; l < lines.length; l++) {
                    int lw = g2.getFontMetrics().stringWidth(lines[l]);
                    g2.drawString(lines[l], x + barWidth / 2 - lw / 2, baseY + 18 + l * 14);
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}