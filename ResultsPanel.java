import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ResultsPanel extends JPanel {
    private final AppState state;
    private ChartPanel chart;
    private JLabel accuracyValue;
    private JLabel accuracyCaption;
    private JTextArea suggestionArea;
    private JLabel headerStatus;

    public ResultsPanel(AppState state) {
        this.state = state;
        setLayout(new BorderLayout(16, 16));
        setBackground(new Color(24, 26, 32));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Results: Predicted vs Actual");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(Color.WHITE);

        headerStatus = new JLabel("Run both the Simulator and Sandbox tabs to see the comparison.");
        headerStatus.setFont(new Font("SansSerif", Font.PLAIN, 13));
        headerStatus.setForeground(new Color(160, 165, 175));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBackground(new Color(24, 26, 32));
        north.add(title);
        north.add(Box.createVerticalStrut(6));
        north.add(headerStatus);

        chart = new ChartPanel();
        chart.setPreferredSize(new Dimension(760, 260));

        JPanel accuracyCard = new JPanel(new BorderLayout());
        accuracyCard.setBackground(new Color(32, 35, 43));
        accuracyCard.setBorder(new EmptyBorder(18, 24, 18, 24));
        accuracyValue = new JLabel("--%", SwingConstants.CENTER);
        accuracyValue.setFont(new Font("SansSerif", Font.BOLD, 46));
        accuracyValue.setForeground(new Color(220, 190, 90));
        accuracyCaption = new JLabel("Prediction accuracy", SwingConstants.CENTER);
        accuracyCaption.setFont(new Font("SansSerif", Font.PLAIN, 13));
        accuracyCaption.setForeground(new Color(170, 175, 185));
        accuracyCard.add(accuracyValue, BorderLayout.CENTER);
        accuracyCard.add(accuracyCaption, BorderLayout.SOUTH);

        suggestionArea = new JTextArea();
        suggestionArea.setEditable(false);
        suggestionArea.setLineWrap(true);
        suggestionArea.setWrapStyleWord(true);
        suggestionArea.setBackground(new Color(32, 35, 43));
        suggestionArea.setForeground(new Color(210, 214, 220));
        suggestionArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        suggestionArea.setBorder(new EmptyBorder(16, 16, 16, 16));
        suggestionArea.setText("No suggestion yet.");

        JPanel right = new JPanel(new BorderLayout(0, 14));
        right.setBackground(new Color(24, 26, 32));
        right.add(accuracyCard, BorderLayout.NORTH);
        right.add(suggestionArea, BorderLayout.CENTER);
        right.setPreferredSize(new Dimension(280, 260));

        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setBackground(new Color(24, 26, 32));
        center.add(chart, BorderLayout.CENTER);
        center.add(right, BorderLayout.EAST);

        add(north, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        state.addListener(this::refresh);
    }

    public void refresh() {
        if (!state.simulatorDone || !state.sandboxDone) {
            headerStatus.setText("Run both the Simulator and Sandbox tabs to see the comparison.");
            return;
        }

        headerStatus.setText("Comparison for a limit of " + state.limit + " attempts, based on " + state.records.size() + " historical records.");

        chart.setResults(new long[]{
                state.predictedUsersLocked, state.actualUsersLocked,
                state.predictedAttackersBlocked, state.actualAttackersBlocked
        });

        double userError = percentError(state.predictedUsersLocked, state.actualUsersLocked);
        double attackerError = percentError(state.predictedAttackersBlocked, state.actualAttackersBlocked);
        double avgError = (userError + attackerError) / 2.0;
        double accuracy = Math.max(0, 100 - avgError);

        accuracyValue.setText(String.format("%.0f%%", accuracy));

        String suggestion;
        if (accuracy >= 90) {
            suggestion = "Highly trustworthy. The simulator's prediction closely matched the sandbox result for this policy type. This suggests companies could reasonably rely on simulation alone before deploying this kind of policy change, reducing the need to test on real users.";
        } else if (accuracy >= 75) {
            suggestion = "Reasonably trustworthy, but not perfect. The prediction was fairly close, though the gap suggests the behavior model could be refined further, for example by including more real-world factors before fully trusting it.";
        } else if (accuracy >= 50) {
            suggestion = "Use with caution. The gap between prediction and reality is significant for this policy type. Simulation alone should not replace careful manual or small-scale real testing yet.";
        } else {
            suggestion = "Not trustworthy for this policy type. The prediction was far from what actually happened, suggesting the behavior model used here does not capture this kind of policy well. Manual or staged real-world testing is still necessary.";
        }
        suggestionArea.setText(suggestion);
    }

    private double percentError(long predicted, long actual) {
        double base = Math.max(actual, 1);
        return Math.min(100, Math.abs(predicted - actual) / base * 100.0);
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
                g2.drawString("Run both tabs to see predicted vs actual results here.", 30, getHeight() / 2);
                return;
            }

            long maxVal = 1;
            for (long v : results) maxVal = Math.max(maxVal, v);

            String[] labels = {"Predicted\nUsers Locked", "Actual\nUsers Locked", "Predicted\nAttackers Blocked", "Actual\nAttackers Blocked"};
            Color[] colors = {
                    new Color(120, 130, 240), new Color(70, 190, 160),
                    new Color(120, 130, 240), new Color(70, 190, 160)
            };

            int chartW = getWidth() - 80;
            int chartH = getHeight() - 110;
            int baseY = getHeight() - 60;
            int barWidth = chartW / (results.length * 2);
            int gap = barWidth;

            g2.setColor(new Color(80, 85, 95));
            g2.drawLine(50, baseY, getWidth() - 30, baseY);

            for (int i = 0; i < results.length; i++) {
                int x = 50 + gap / 2 + i * (barWidth + gap);
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
}
