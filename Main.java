import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main extends JFrame {

    enum PolicyType {
        LOCKOUT("Lockout Threshold", "Attempts allowed", 1, 10, 5, "locked out", "blocked"),
        MFA("MFA Requirement", "MFA strictness", 1, 10, 5, "failing MFA", "blocked by MFA"),
        SESSION("Session Timeout", "Timeout minutes", 5, 60, 15, "timed out", "session revoked");

        final String displayName;
        final String paramLabel;
        final int minParam;
        final int maxParam;
        final int defaultParam;
        final String userOutcome;
        final String attackerOutcome;

        PolicyType(String displayName, String paramLabel, int minParam, int maxParam, int defaultParam, String userOutcome, String attackerOutcome) {
            this.displayName = displayName;
            this.paramLabel = paramLabel;
            this.minParam = minParam;
            this.maxParam = maxParam;
            this.defaultParam = defaultParam;
            this.userOutcome = userOutcome;
            this.attackerOutcome = attackerOutcome;
        }

        public String toString() {
            return displayName;
        }
    }

    static class LoginRecord {
        String id;
        String type;
        int attemptsNeeded;
        int mfaDifficulty;
        int sessionTolerance;

        LoginRecord(String id, String type, int attemptsNeeded, int mfaDifficulty, int sessionTolerance) {
            this.id = id;
            this.type = type;
            this.attemptsNeeded = attemptsNeeded;
            this.mfaDifficulty = mfaDifficulty;
            this.sessionTolerance = sessionTolerance;
        }

        boolean isUser() {
            return type.equalsIgnoreCase("USER");
        }

        boolean isAttacker() {
            return type.equalsIgnoreCase("ATTACKER");
        }

        int valueFor(PolicyType p) {
            if (p == PolicyType.LOCKOUT) return attemptsNeeded;
            if (p == PolicyType.MFA) return mfaDifficulty;
            return sessionTolerance;
        }
    }

    static class AppState {
        List<LoginRecord> records = new ArrayList<>();
        PolicyType policy = PolicyType.LOCKOUT;
        int paramValue = PolicyType.LOCKOUT.defaultParam;
        long predictedUsers = -1;
        long predictedAttackers = -1;
        long actualUsers = -1;
        long actualAttackers = -1;
        boolean simulatorDone = false;
        boolean sandboxDone = false;
        List<Runnable> listeners = new ArrayList<>();

        void addListener(Runnable r) {
            listeners.add(r);
        }

        void notifyListeners() {
            for (Runnable r : listeners) r.run();
        }

        void resetResults() {
            predictedUsers = -1;
            predictedAttackers = -1;
            actualUsers = -1;
            actualAttackers = -1;
            simulatorDone = false;
            sandboxDone = false;
        }
    }

    static class Theme {
        static final Color BG = new Color(20, 23, 29);
        static final Color PANEL = new Color(27, 31, 39);
        static final Color PANEL_ALT = new Color(34, 39, 48);
        static final Color TEXT = new Color(232, 236, 242);
        static final Color MUTED = new Color(164, 173, 186);
        static final Color LINE = new Color(53, 60, 72);
        static final Color BLUE = new Color(107, 145, 255);
        static final Color GREEN = new Color(94, 177, 150);
        static final Color ORANGE = new Color(198, 155, 103);
        static final Color RED = new Color(208, 112, 121);
        static final Color GOLD = new Color(107, 145, 255);
    }

    static void configureLookAndFeel() {
        FlatDarkLaf.setup();
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("TabbedPane.tabArc", 8);
        UIManager.put("TabbedPane.showTabSeparators", false);
        UIManager.put("Panel.background", Theme.BG);
        UIManager.put("TabbedPane.background", Theme.PANEL);
        UIManager.put("TabbedPane.foreground", Theme.TEXT);
        UIManager.put("TabbedPane.selectedBackground", Theme.PANEL_ALT);
        UIManager.put("TabbedPane.contentAreaColor", Theme.BG);
        UIManager.put("ProgressBar.background", Theme.PANEL_ALT);
        UIManager.put("TabbedPane.underlineColor", Theme.BLUE);
        UIManager.put("Component.focusColor", Theme.BLUE);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
    }

    static class RoundedPanel extends JPanel {
        private final Color fill;
        private final Color border;
        private final int radius;

        RoundedPanel(Color fill, Color border, int radius) {
            this.fill = fill;
            this.border = border;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), radius, radius));
            g2.setColor(border);
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static class FancyButton extends JButton {
        FancyButton(String text, Color bg, Color textColor) {
            super(text);
            setFocusPainted(false);
            setBorder(new EmptyBorder(9, 16, 9, 16));
            setBackground(bg);
            setForeground(textColor);
            setFont(new Font("SansSerif", Font.BOLD, 13));
            setOpaque(true);
            setContentAreaFilled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            putClientProperty("JButton.buttonType", "roundRect");
        }
    }

    static class MetricCard extends RoundedPanel {
        JLabel valueLabel;
        JLabel captionLabel;

        MetricCard(String title, String value, Color accent) {
            super(Theme.PANEL_ALT, Theme.LINE, 20);
            setLayout(new BorderLayout(6, 8));
            setBorder(new EmptyBorder(18, 18, 16, 18));

            valueLabel = new JLabel(value, SwingConstants.CENTER);
            valueLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
            valueLabel.setForeground(accent);

            captionLabel = new JLabel(title, SwingConstants.CENTER);
            captionLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            captionLabel.setForeground(Theme.MUTED);

            add(valueLabel, BorderLayout.CENTER);
            add(captionLabel, BorderLayout.SOUTH);
        }
    }

    static List<LoginRecord> loadCsv(File file) throws Exception {
        List<LoginRecord> records = new ArrayList<>();
        List<String> lines = Files.readAllLines(file.toPath());
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            if (parts.length < 5) continue;
            records.add(new LoginRecord(
                    parts[0].trim(), parts[1].trim(),
                    Integer.parseInt(parts[2].trim()),
                    Integer.parseInt(parts[3].trim()),
                    Integer.parseInt(parts[4].trim())
            ));
        }
        return records;
    }

    static long countPredicted(List<LoginRecord> records, PolicyType policy, int param, boolean users) {
        return records.stream()
                .filter(r -> users ? r.isUser() : r.isAttacker())
                .filter(r -> r.valueFor(policy) > param)
                .count();
    }

    static long[] countActual(List<LoginRecord> records, PolicyType policy, int param) {
        Random rand = new Random();
        long users = 0;
        long attackers = 0;
        for (LoginRecord r : records) {
            int noise = rand.nextInt(3) - 1;
            int effective = Math.max(1, r.valueFor(policy) + noise);
            if (r.isUser() && effective > param) users++;
            if (r.isAttacker() && effective > param) attackers++;
        }
        return new long[]{users, attackers};
    }

    static double percentError(long predicted, long actual) {
        double base = Math.max(actual, 1);
        return Math.min(100, Math.abs(predicted - actual) / base * 100.0);
    }

    static double accuracyFor(List<LoginRecord> records, PolicyType policy, int param) {
        long predUsers = countPredicted(records, policy, param, true);
        long predAttackers = countPredicted(records, policy, param, false);
        long[] actual = countActual(records, policy, param);
        double userErr = percentError(predUsers, actual[0]);
        double attackerErr = percentError(predAttackers, actual[1]);
        return Math.max(0, 100 - (userErr + attackerErr) / 2.0);
    }

    static class SimulatorPanel extends JPanel {
        AppState state;
        FancyButton runButton;
        JProgressBar progressBar;
        MetricCard usersCard;
        MetricCard attackersCard;
        JLabel statusLabel;
        Timer timer;
        int tick;
        long targetUsers;
        long targetAttackers;

        SimulatorPanel(AppState state) {
            this.state = state;
            setLayout(new BorderLayout(18, 18));
            setOpaque(false);
            setBorder(new EmptyBorder(18, 18, 18, 18));

            JLabel title = new JLabel("Policy simulator");
            title.setFont(new Font("SansSerif", Font.BOLD, 24));
            title.setForeground(Theme.TEXT);

            JLabel desc = new JLabel("Predict outcomes from historical login data before any live replay.");
            desc.setFont(new Font("SansSerif", Font.PLAIN, 13));
            desc.setForeground(Theme.MUTED);

            JPanel header = new JPanel();
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.setOpaque(false);
            header.add(title);
            header.add(Box.createVerticalStrut(4));
            header.add(desc);

            runButton = new FancyButton("Run simulator", Theme.BLUE, Color.WHITE);
            runButton.addActionListener(e -> runSimulation());

            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setForeground(Theme.BLUE);
            progressBar.setBackground(new Color(26, 34, 46));
            progressBar.setBorderPainted(false);

            JPanel controls = new JPanel(new BorderLayout(12, 0));
            controls.setOpaque(false);
            controls.add(runButton, BorderLayout.WEST);
            controls.add(progressBar, BorderLayout.CENTER);

            usersCard = new MetricCard("Predicted users affected", "--", Theme.BLUE);
            attackersCard = new MetricCard("Predicted attackers affected", "--", Theme.BLUE);

            JPanel cards = new JPanel(new GridLayout(1, 2, 18, 0));
            cards.setOpaque(false);
            cards.add(usersCard);
            cards.add(attackersCard);

            statusLabel = new JLabel("Load historical data, then run the simulator.");
            statusLabel.setForeground(Theme.MUTED);
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

            JPanel top = new JPanel(new BorderLayout(0, 18));
            top.setOpaque(false);
            top.add(header, BorderLayout.NORTH);
            top.add(controls, BorderLayout.SOUTH);

            add(top, BorderLayout.NORTH);
            add(cards, BorderLayout.CENTER);
            add(statusLabel, BorderLayout.SOUTH);

            state.addListener(() -> runButton.setEnabled(!state.records.isEmpty()));
            runButton.setEnabled(false);
        }

        void runSimulation() {
            if (state.records.isEmpty()) return;
            runButton.setEnabled(false);
            usersCard.valueLabel.setText("--");
            attackersCard.valueLabel.setText("--");
            progressBar.setValue(0);
            statusLabel.setText("Calculating statistics for " + state.policy.displayName + " (" + state.records.size() + " records)...");

            targetUsers = countPredicted(state.records, state.policy, state.paramValue, true);
            targetAttackers = countPredicted(state.records, state.policy, state.paramValue, false);

            tick = 0;
            timer = new Timer(22, e -> {
                tick++;
                int progress = Math.min(100, tick * 4);
                progressBar.setValue(progress);
                double frac = progress / 100.0;
                usersCard.valueLabel.setText(String.valueOf((long) (targetUsers * frac)));
                attackersCard.valueLabel.setText(String.valueOf((long) (targetAttackers * frac)));

                if (progress >= 100) {
                    timer.stop();
                    usersCard.valueLabel.setText(String.valueOf(targetUsers));
                    attackersCard.valueLabel.setText(String.valueOf(targetAttackers));
                    statusLabel.setText("Prediction complete for " + state.policy.displayName + " at " + state.paramValue + ".");
                    state.predictedUsers = targetUsers;
                    state.predictedAttackers = targetAttackers;
                    state.simulatorDone = true;
                    state.notifyListeners();
                    runButton.setEnabled(true);
                }
            });
            timer.start();
        }
    }

    static class SandboxGrid extends JPanel {
        Color[] cells = new Color[0];

        SandboxGrid() {
            setOpaque(false);
            setPreferredSize(new Dimension(760, 240));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.LINE),
                    new EmptyBorder(10, 10, 10, 10)
            ));
        }

        void reset(int count) {
            cells = new Color[count];
            repaint();
        }

        void setCell(int index, Color c) {
            if (index >= 0 && index < cells.length) cells[index] = c;
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.PANEL);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            if (cells.length == 0) {
                g2.setColor(Theme.MUTED);
                g2.drawString("Run the sandbox to see records being processed live.", 20, getHeight() / 2);
                g2.dispose();
                return;
            }
            int cols = (int) Math.ceil(Math.sqrt(cells.length * (getWidth() / (double) getHeight())));
            int rows = (int) Math.ceil(cells.length / (double) cols);
            int cellW = getWidth() / cols;
            int cellH = getHeight() / rows;
            for (int i = 0; i < cells.length; i++) {
                int col = i % cols;
                int row = i / cols;
                Color c = cells[i];
                g2.setColor(c == null ? Theme.PANEL_ALT : c);
                g2.fillRoundRect(col * cellW + 1, row * cellH + 1, cellW - 2, cellH - 2, 4, 4);
            }
            g2.dispose();
        }
    }

    static class SandboxPanel extends JPanel {
        AppState state;
        FancyButton runButton;
        JProgressBar progressBar;
        MetricCard usersCard;
        MetricCard attackersCard;
        JLabel statusLabel;
        SandboxGrid grid;
        Timer timer;
        int revealed;
        List<Color> sampleColors;
        long targetUsers;
        long targetAttackers;

        SandboxPanel(AppState state) {
            this.state = state;
            setLayout(new BorderLayout(18, 18));
            setOpaque(false);
            setBorder(new EmptyBorder(18, 18, 18, 18));

            JLabel title = new JLabel("Live replay sandbox");
            title.setFont(new Font("SansSerif", Font.BOLD, 24));
            title.setForeground(Theme.TEXT);

            JLabel desc = new JLabel("Replay each historical record with small real-world variation for the selected policy.");
            desc.setFont(new Font("SansSerif", Font.PLAIN, 13));
            desc.setForeground(Theme.MUTED);

            JPanel header = new JPanel();
            header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
            header.setOpaque(false);
            header.add(title);
            header.add(Box.createVerticalStrut(4));
            header.add(desc);

            runButton = new FancyButton("Run sandbox", Theme.BLUE, Color.WHITE);
            runButton.addActionListener(e -> runSandbox());

            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressBar.setForeground(Theme.BLUE);
            progressBar.setBackground(new Color(26, 34, 46));
            progressBar.setBorderPainted(false);

            JPanel controls = new JPanel(new BorderLayout(12, 0));
            controls.setOpaque(false);
            controls.add(runButton, BorderLayout.WEST);
            controls.add(progressBar, BorderLayout.CENTER);

            JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 2));
            legend.setOpaque(false);
            legend.add(legendDot(new Color(91, 202, 158), "Normal outcome"));
            legend.add(legendDot(new Color(239, 99, 99), "Notable outcome"));

            grid = new SandboxGrid();

            usersCard = new MetricCard("Actual users affected", "--", Theme.BLUE);
            attackersCard = new MetricCard("Actual attackers affected", "--", Theme.BLUE);
            JPanel cards = new JPanel(new GridLayout(1, 2, 18, 0));
            cards.setOpaque(false);
            cards.add(usersCard);
            cards.add(attackersCard);

            JPanel top = new JPanel(new BorderLayout(10, 10));
            top.setOpaque(false);
            top.add(header, BorderLayout.NORTH);
            top.add(controls, BorderLayout.SOUTH);

            JPanel center = new JPanel(new BorderLayout(10, 10));
            center.setOpaque(false);
            center.add(grid, BorderLayout.CENTER);
            center.add(legend, BorderLayout.SOUTH);

            statusLabel = new JLabel("Load historical data, then run the sandbox.");
            statusLabel.setForeground(Theme.MUTED);
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

            JPanel south = new JPanel(new BorderLayout(10, 10));
            south.setOpaque(false);
            south.add(cards, BorderLayout.CENTER);
            south.add(statusLabel, BorderLayout.SOUTH);

            add(top, BorderLayout.NORTH);
            add(center, BorderLayout.CENTER);
            add(south, BorderLayout.SOUTH);

            state.addListener(() -> runButton.setEnabled(!state.records.isEmpty()));
            runButton.setEnabled(false);
        }

        JLabel legendDot(Color c, String text) {
            JLabel l = new JLabel(text);
            l.setForeground(Theme.MUTED);
            l.setFont(new Font("SansSerif", Font.PLAIN, 12));
            l.setIcon(new Icon() {
                public void paintIcon(Component comp, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(c);
                    g2.fillOval(x, y + 4, 10, 10);
                    g2.dispose();
                }
                public int getIconWidth() { return 16; }
                public int getIconHeight() { return 18; }
            });
            return l;
        }

        void runSandbox() {
            if (state.records.isEmpty()) return;
            runButton.setEnabled(false);
            usersCard.valueLabel.setText("--");
            attackersCard.valueLabel.setText("--");
            progressBar.setValue(0);
            statusLabel.setText("Replaying " + state.records.size() + " records for " + state.policy.displayName + "...");

            long[] actual = countActual(state.records, state.policy, state.paramValue);
            targetUsers = actual[0];
            targetAttackers = actual[1];

            Random rand = new Random();
            int sampleSize = Math.min(state.records.size(), 800);
            int step = Math.max(1, state.records.size() / sampleSize);
            sampleColors = new ArrayList<>();
            for (int i = 0; i < state.records.size(); i += step) {
                LoginRecord r = state.records.get(i);
                int noise = rand.nextInt(3) - 1;
                int effective = Math.max(1, r.valueFor(state.policy) + noise);
                boolean notable = (r.isUser() && effective > state.paramValue) || (r.isAttacker() && effective <= state.paramValue);
                sampleColors.add(notable ? new Color(239, 99, 99) : new Color(91, 202, 158));
            }
            grid.reset(sampleColors.size());

            revealed = 0;
            timer = new Timer(12, e -> {
                int batch = Math.max(1, sampleColors.size() / 90);
                for (int i = 0; i < batch && revealed < sampleColors.size(); i++) {
                    grid.setCell(revealed, sampleColors.get(revealed));
                    revealed++;
                }
                int progress = (int) ((revealed / (double) sampleColors.size()) * 100);
                progressBar.setValue(progress);
                double frac = progress / 100.0;
                usersCard.valueLabel.setText(String.valueOf((long) (targetUsers * frac)));
                attackersCard.valueLabel.setText(String.valueOf((long) (targetAttackers * frac)));
                grid.repaint();

                if (revealed >= sampleColors.size()) {
                    timer.stop();
                    usersCard.valueLabel.setText(String.valueOf(targetUsers));
                    attackersCard.valueLabel.setText(String.valueOf(targetAttackers));
                    statusLabel.setText("Sandbox replay complete for " + state.policy.displayName + " at " + state.paramValue + ".");
                    state.actualUsers = targetUsers;
                    state.actualAttackers = targetAttackers;
                    state.sandboxDone = true;
                    state.notifyListeners();
                    runButton.setEnabled(true);
                }
            });
            timer.start();
        }
    }

    static class ComparisonChart extends JPanel {
        long[] results;
        String[] labels;

        ComparisonChart() {
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.LINE),
                    new EmptyBorder(12, 12, 12, 12)
            ));
        }

        void setResults(long[] results, String[] labels) {
            this.results = results;
            this.labels = labels;
            repaint();
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (results == null) {
                g2.setColor(Theme.MUTED);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.drawString("Run both tabs to see predicted vs actual results here.", 30, getHeight() / 2);
                return;
            }

            long maxVal = 1;
            for (long v : results) maxVal = Math.max(maxVal, v);

            Color[] colors = {Theme.BLUE, new Color(145, 171, 255), Theme.BLUE, new Color(145, 171, 255)};
            int chartW = getWidth() - 80;
            int chartH = getHeight() - 110;
            int baseY = getHeight() - 60;
            int barWidth = chartW / (results.length * 2);
            int gap = barWidth;

            g2.setColor(Theme.LINE);
            for (int line = 0; line <= 4; line++) {
                int y = baseY - chartH * line / 4;
                g2.drawLine(50, y, getWidth() - 30, y);
            }

            for (int i = 0; i < results.length; i++) {
                int x = 50 + gap / 2 + i * (barWidth + gap);
                int barHeight = (int) ((results[i] / (double) maxVal) * chartH);
                int y = baseY - barHeight;

                g2.setColor(colors[i]);
                g2.fillRoundRect(x, y, barWidth, barHeight, 12, 12);

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                String valStr = String.valueOf(results[i]);
                int strW = g2.getFontMetrics().stringWidth(valStr);
                g2.drawString(valStr, x + barWidth / 2 - strW / 2, y - 8);

                g2.setColor(Theme.MUTED);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                String[] lines = labels[i].split("\n");
                for (int l = 0; l < lines.length; l++) {
                    int lw = g2.getFontMetrics().stringWidth(lines[l]);
                    g2.drawString(lines[l], x + barWidth / 2 - lw / 2, baseY + 18 + l * 14);
                }
            }
        }
    }

    static class ResultsPanel extends JPanel {
        AppState state;
        ComparisonChart chart;
        JLabel accuracyValue;
        JTextArea suggestionArea;
        JLabel headerStatus;

        ResultsPanel(AppState state) {
            this.state = state;
            setLayout(new BorderLayout(18, 18));
            setOpaque(false);
            setBorder(new EmptyBorder(18, 18, 18, 18));

            JLabel title = new JLabel("Outcome comparison");
            title.setFont(new Font("SansSerif", Font.BOLD, 24));
            title.setForeground(Theme.TEXT);

            headerStatus = new JLabel("Run both the Simulator and Sandbox tabs to see the comparison.");
            headerStatus.setFont(new Font("SansSerif", Font.PLAIN, 13));
            headerStatus.setForeground(Theme.MUTED);

            JPanel north = new JPanel();
            north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
            north.setOpaque(false);
            north.add(title);
            north.add(Box.createVerticalStrut(6));
            north.add(headerStatus);

            chart = new ComparisonChart();
            chart.setPreferredSize(new Dimension(760, 260));

            RoundedPanel accuracyCard = new RoundedPanel(Theme.PANEL_ALT, Theme.LINE, 22);
            accuracyCard.setLayout(new BorderLayout());
            accuracyCard.setBorder(new EmptyBorder(18, 24, 18, 24));
            accuracyValue = new JLabel("--%", SwingConstants.CENTER);
            accuracyValue.setFont(new Font("SansSerif", Font.BOLD, 46));
            accuracyValue.setForeground(Theme.BLUE);
            JLabel accuracyCaption = new JLabel("Prediction accuracy", SwingConstants.CENTER);
            accuracyCaption.setFont(new Font("SansSerif", Font.PLAIN, 13));
            accuracyCaption.setForeground(Theme.MUTED);
            accuracyCard.add(accuracyValue, BorderLayout.CENTER);
            accuracyCard.add(accuracyCaption, BorderLayout.SOUTH);

            suggestionArea = new JTextArea();
            suggestionArea.setEditable(false);
            suggestionArea.setLineWrap(true);
            suggestionArea.setWrapStyleWord(true);
            suggestionArea.setOpaque(false);
            suggestionArea.setForeground(Theme.TEXT);
            suggestionArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
            suggestionArea.setBorder(new EmptyBorder(16, 16, 16, 16));
            suggestionArea.setText("No suggestion yet.");

            RoundedPanel right = new RoundedPanel(Theme.PANEL_ALT, Theme.LINE, 22);
            right.setLayout(new BorderLayout(0, 14));
            right.setBorder(new EmptyBorder(14, 14, 14, 14));
            right.add(accuracyCard, BorderLayout.NORTH);
            right.add(suggestionArea, BorderLayout.CENTER);
            right.setPreferredSize(new Dimension(300, 260));

            JPanel center = new JPanel(new BorderLayout(16, 0));
            center.setOpaque(false);
            center.add(chart, BorderLayout.CENTER);
            center.add(right, BorderLayout.EAST);

            add(north, BorderLayout.NORTH);
            add(center, BorderLayout.CENTER);

            state.addListener(this::refresh);
        }

        void refresh() {
            if (!state.simulatorDone || !state.sandboxDone) {
                headerStatus.setText("Run both the Simulator and Sandbox tabs to see the comparison.");
                return;
            }

            headerStatus.setText("Comparison for " + state.policy.displayName + " at " + state.paramValue + ", based on " + state.records.size() + " records.");

            String[] labels = {"Predicted\nUsers", "Actual\nUsers", "Predicted\nAttackers", "Actual\nAttackers"};
            chart.setResults(new long[]{state.predictedUsers, state.actualUsers, state.predictedAttackers, state.actualAttackers}, labels);

            double userError = percentError(state.predictedUsers, state.actualUsers);
            double attackerError = percentError(state.predictedAttackers, state.actualAttackers);
            double avgError = (userError + attackerError) / 2.0;
            double accuracy = Math.max(0, 100 - avgError);
            accuracyValue.setText(String.format("%.0f%%", accuracy));

            String suggestion;
            String policyName = state.policy.displayName;
            if (accuracy >= 90) {
                suggestion = "Highly trustworthy for " + policyName + ". The simulator closely matched the sandbox result. This suggests the model can be relied on for early decision-making.";
            } else if (accuracy >= 75) {
                suggestion = "Reasonably trustworthy for " + policyName + ", but not perfect. The gap suggests the simulation could be refined before deployment.";
            } else if (accuracy >= 50) {
                suggestion = "Use with caution for " + policyName + ". The gap is significant, so simulation should not replace live testing.";
            } else {
                suggestion = "Not trustworthy for " + policyName + ". The prediction diverged substantially from the replayed reality, so the model needs revision.";
            }
            suggestionArea.setText(suggestion);
        }
    }

    static class AccuracyBarChart extends JPanel {
        double[] values;
        String[] labels;

        AccuracyBarChart() {
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.LINE),
                    new EmptyBorder(12, 12, 12, 12)
            ));
        }

        void setValues(double[] values, String[] labels) {
            this.values = values;
            this.labels = labels;
            repaint();
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (values == null) {
                g2.setColor(Theme.MUTED);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
                g2.drawString("Run all policies to compare their simulation accuracy here.", 30, getHeight() / 2);
                return;
            }

            int chartW = getWidth() - 80;
            int chartH = getHeight() - 100;
            int baseY = getHeight() - 60;
            int barWidth = chartW / (values.length * 2);
            int gap = barWidth;

            g2.setColor(Theme.LINE);
            for (int line = 0; line <= 4; line++) {
                int y = baseY - chartH * line / 4;
                g2.drawLine(50, y, getWidth() - 30, y);
            }

            Color[] palette = {Theme.BLUE, new Color(145, 171, 255), new Color(114, 139, 201)};
            for (int i = 0; i < values.length; i++) {
                int x = 50 + gap / 2 + i * (barWidth + gap);
                int barHeight = (int) ((values[i] / 100.0) * chartH);
                int y = baseY - barHeight;

                g2.setColor(palette[i % palette.length]);
                g2.fillRoundRect(x, y, barWidth, barHeight, 12, 12);

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 15));
                String valStr = String.format("%.0f%%", values[i]);
                int strW = g2.getFontMetrics().stringWidth(valStr);
                g2.drawString(valStr, x + barWidth / 2 - strW / 2, y - 10);
                g2.setColor(Theme.MUTED);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                int lw = g2.getFontMetrics().stringWidth(labels[i]);
                g2.drawString(labels[i], x + barWidth / 2 - lw / 2, baseY + 20);
            }
        }
    }

    static class PolicyComparisonPanel extends JPanel {
        AppState state;
        AccuracyBarChart chart;
        JTextArea conclusionArea;
        FancyButton runButton;
        JLabel statusLabel;

        PolicyComparisonPanel(AppState state) {
            this.state = state;
            setLayout(new BorderLayout(18, 18));
            setOpaque(false);
            setBorder(new EmptyBorder(18, 18, 18, 18));

            JLabel title = new JLabel("Tri-policy comparison");
            title.setFont(new Font("SansSerif", Font.BOLD, 24));
            title.setForeground(Theme.TEXT);

            JLabel desc = new JLabel("Compare the trustworthiness of each policy type using default parameters.");
            desc.setFont(new Font("SansSerif", Font.PLAIN, 13));
            desc.setForeground(Theme.MUTED);

            runButton = new FancyButton("Run all policies", Theme.BLUE, Color.WHITE);
            runButton.addActionListener(e -> runAllPolicies());

            JPanel top = new JPanel();
            top.setOpaque(false);
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            top.add(title);
            top.add(Box.createVerticalStrut(4));
            top.add(desc);
            top.add(Box.createVerticalStrut(12));
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            controls.setOpaque(false);
            controls.add(runButton);
            top.add(controls);

            chart = new AccuracyBarChart();
            chart.setPreferredSize(new Dimension(760, 260));

            conclusionArea = new JTextArea();
            conclusionArea.setEditable(false);
            conclusionArea.setLineWrap(true);
            conclusionArea.setWrapStyleWord(true);
            conclusionArea.setOpaque(false);
            conclusionArea.setForeground(Theme.TEXT);
            conclusionArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
            conclusionArea.setBorder(new EmptyBorder(16, 16, 16, 16));
            conclusionArea.setText("Run all policies to see which type of simulation is most trustworthy.");

            statusLabel = new JLabel("Load historical data, then run all policies.");
            statusLabel.setForeground(Theme.MUTED);
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

            JPanel bottom = new JPanel(new BorderLayout(10, 10));
            bottom.setOpaque(false);
            bottom.add(conclusionArea, BorderLayout.CENTER);
            bottom.add(statusLabel, BorderLayout.SOUTH);

            add(top, BorderLayout.NORTH);
            add(chart, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);

            state.addListener(() -> runButton.setEnabled(!state.records.isEmpty()));
            runButton.setEnabled(false);
        }

        void runAllPolicies() {
            if (state.records.isEmpty()) return;
            statusLabel.setText("Calculating accuracy for each policy type...");

            PolicyType[] all = PolicyType.values();
            double[] accuracies = new double[all.length];
            String[] labels = new String[all.length];
            for (int i = 0; i < all.length; i++) {
                accuracies[i] = accuracyFor(state.records, all[i], all[i].defaultParam);
                labels[i] = all[i].displayName;
            }
            chart.setValues(accuracies, labels);

            int bestIndex = 0;
            int worstIndex = 0;
            for (int i = 1; i < accuracies.length; i++) {
                if (accuracies[i] > accuracies[bestIndex]) bestIndex = i;
                if (accuracies[i] < accuracies[worstIndex]) worstIndex = i;
            }

            String conclusion = "Most trustworthy to simulate: " + all[bestIndex].displayName
                    + " (" + String.format("%.0f%%", accuracies[bestIndex]) + " accuracy). "
                    + "Least trustworthy: " + all[worstIndex].displayName
                    + " (" + String.format("%.0f%%", accuracies[worstIndex]) + " accuracy). "
                    + "This reflects how well simulation predicts each policy type's real impact, not which policy is better to implement. "
                    + "A low-accuracy policy type still needs real or sandbox testing before being trusted from simulation alone.";
            conclusionArea.setText(conclusion);
            statusLabel.setText("Done. Compared " + all.length + " policy types on " + state.records.size() + " records.");
        }
    }

    AppState state = new AppState();
    JLabel fileLabel;
    JComboBox<PolicyType> policyCombo;
    JSpinner paramSpinner;
    JLabel paramLabel;
    JTabbedPane tabs;
    ResultsPanel resultsPanel;

    Main() {
        setTitle("Authentication Policy: Simulator vs Sandbox");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 760);
        setMinimumSize(new Dimension(900, 680));
        setLocationRelativeTo(null);
        getContentPane().setBackground(Theme.BG);

        JPanel root = new JPanel();
        root.setBackground(Theme.BG);
        root.setLayout(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(20, 20, 20, 20));
        setContentPane(root);

        root.add(buildTopBar(), BorderLayout.NORTH);

        SimulatorPanel simulatorPanel = new SimulatorPanel(state);
        SandboxPanel sandboxPanel = new SandboxPanel(state);
        resultsPanel = new ResultsPanel(state);
        PolicyComparisonPanel comparisonPanel = new PolicyComparisonPanel(state);

        tabs = new JTabbedPane();
        tabs.setBackground(Theme.PANEL_ALT);
        tabs.setForeground(Theme.TEXT);
        tabs.setOpaque(true);
        tabs.setBorder(new EmptyBorder(8, 0, 0, 0));
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));
        tabs.addTab("Simulator", simulatorPanel);
        tabs.addTab("Sandbox", sandboxPanel);
        tabs.addTab("Results", resultsPanel);
        tabs.addTab("Policy Comparison", comparisonPanel);

        root.add(tabs, BorderLayout.CENTER);
    }

    JPanel buildTopBar() {
        RoundedPanel panel = new RoundedPanel(Theme.PANEL, Theme.LINE, 22);
        panel.setLayout(new GridLayout(2, 1, 0, 8));
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        FancyButton loadButton = new FancyButton("Load historical data", Theme.BLUE, Color.WHITE);
        loadButton.addActionListener(e -> loadFile());

        fileLabel = new JLabel("No file loaded");
        fileLabel.setForeground(Theme.MUTED);
        fileLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        left.add(loadButton);
        left.add(fileLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        right.setOpaque(false);

        JLabel policyLabel = new JLabel("Policy");
        policyLabel.setForeground(Theme.MUTED);
        policyLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        policyCombo = new JComboBox<>(PolicyType.values());
        policyCombo.setBackground(Theme.PANEL_ALT);
        policyCombo.setForeground(Theme.TEXT);
        policyCombo.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        policyCombo.addActionListener(e -> {
            state.policy = (PolicyType) policyCombo.getSelectedItem();
            state.paramValue = state.policy.defaultParam;
            paramSpinner.setModel(new SpinnerNumberModel(state.policy.defaultParam, state.policy.minParam, state.policy.maxParam, 1));
            paramLabel.setText(state.policy.paramLabel + ":");
            state.resetResults();
            state.notifyListeners();
            resultsPanel.refresh();
        });

        paramLabel = new JLabel(PolicyType.LOCKOUT.paramLabel + ":");
        paramLabel.setForeground(Theme.MUTED);
        paramLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        paramSpinner = new JSpinner(new SpinnerNumberModel(PolicyType.LOCKOUT.defaultParam, PolicyType.LOCKOUT.minParam, PolicyType.LOCKOUT.maxParam, 1));
        paramSpinner.setPreferredSize(new Dimension(84, 30));
        paramSpinner.setFont(new Font("SansSerif", Font.PLAIN, 12));
        paramSpinner.addChangeListener(e -> {
            state.paramValue = (Integer) paramSpinner.getValue();
            state.resetResults();
            state.notifyListeners();
            resultsPanel.refresh();
        });

        right.add(policyLabel);
        right.add(policyCombo);
        right.add(paramLabel);
        right.add(paramSpinner);

        panel.add(left);
        panel.add(right);
        return panel;
    }

    void loadFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files", "csv"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            state.records = loadCsv(file);
            state.resetResults();
            fileLabel.setText(file.getName() + " (" + state.records.size() + " records)");
            state.notifyListeners();
            tabs.setSelectedIndex(0);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not read file: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        configureLookAndFeel();
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
