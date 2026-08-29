import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Random;
import java.util.ArrayList;

public class SandboxPanel extends JPanel {
    private final AppState state;
    private JButton runButton;
    private JProgressBar progressBar;
    private JLabel usersValue;
    private JLabel attackersValue;
    private JLabel statusLabel;
    private SandboxGrid grid;
    private Timer timer;
    private int revealed;
    private List<Color> sampleColors;
    private long targetUsers;
    private long targetAttackers;

    public SandboxPanel(AppState state) {
        this.state = state;
        setLayout(new BorderLayout(14, 14));
        setBackground(new Color(24, 26, 32));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Sandbox: Replaying Real Login Attempts");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(Color.WHITE);

        JLabel desc = new JLabel("<html>Every record is actually replayed with small real-world variability added,<br>the same way real users and attackers would not behave perfectly on average.</html>");
        desc.setFont(new Font("SansSerif", Font.PLAIN, 13));
        desc.setForeground(new Color(160, 165, 175));

        runButton = new JButton("Run Sandbox");
        runButton.setBackground(new Color(70, 190, 160));
        runButton.setForeground(Color.WHITE);
        runButton.setFocusPainted(false);
        runButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        runButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        runButton.addActionListener(e -> runSandbox());

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(70, 190, 160));

        JPanel controls = new JPanel(new BorderLayout(10, 10));
        controls.setBackground(new Color(24, 26, 32));
        controls.add(runButton, BorderLayout.WEST);
        controls.add(progressBar, BorderLayout.CENTER);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setBackground(new Color(24, 26, 32));
        north.add(title);
        north.add(Box.createVerticalStrut(6));
        north.add(desc);
        north.add(Box.createVerticalStrut(12));
        north.add(controls);

        grid = new SandboxGrid();

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        legend.setBackground(new Color(24, 26, 32));
        legend.add(legendDot(new Color(70, 190, 160), "Normal outcome (user got in, or attacker blocked)"));
        legend.add(legendDot(new Color(220, 90, 80), "Notable outcome (user locked out, or attacker got through)"));

        JPanel cards = new JPanel(new GridLayout(1, 2, 20, 0));
        cards.setBackground(new Color(24, 26, 32));
        cards.add(buildCard("Actual users locked out", true));
        cards.add(buildCard("Actual attackers blocked", false));

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setBackground(new Color(24, 26, 32));
        center.add(grid, BorderLayout.CENTER);
        center.add(legend, BorderLayout.SOUTH);

        JPanel south = new JPanel(new BorderLayout(10, 10));
        south.setBackground(new Color(24, 26, 32));
        south.add(cards, BorderLayout.NORTH);
        statusLabel = new JLabel("Load historical data, then run the sandbox.");
        statusLabel.setForeground(new Color(160, 165, 175));
        south.add(statusLabel, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        state.addListener(() -> runButton.setEnabled(!state.records.isEmpty()));
        runButton.setEnabled(false);
    }

    private JLabel legendDot(Color c, String text) {
        JLabel l = new JLabel(text);
        l.setForeground(new Color(170, 175, 185));
        l.setFont(new Font("SansSerif", Font.PLAIN, 12));
        l.setIcon(new Icon() {
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                g.setColor(c);
                g.fillOval(x, y + 4, 10, 10);
            }
            public int getIconWidth() { return 16; }
            public int getIconHeight() { return 18; }
        });
        return l;
    }

    private JPanel buildCard(String label, boolean isUserCard) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(new Color(32, 35, 43));
        card.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel value = new JLabel("--", SwingConstants.CENTER);
        value.setFont(new Font("SansSerif", Font.BOLD, 34));
        value.setForeground(new Color(70, 190, 160));

        JLabel caption = new JLabel(label, SwingConstants.CENTER);
        caption.setFont(new Font("SansSerif", Font.PLAIN, 12));
        caption.setForeground(new Color(170, 175, 185));

        card.add(value, BorderLayout.CENTER);
        card.add(caption, BorderLayout.SOUTH);

        if (isUserCard) usersValue = value;
        else attackersValue = value;

        return card;
    }

    private void runSandbox() {
        if (state.records.isEmpty()) return;
        runButton.setEnabled(false);
        usersValue.setText("--");
        attackersValue.setText("--");
        progressBar.setValue(0);
        statusLabel.setText("Replaying " + state.records.size() + " records with real-world variability...");

        Random rand = new Random();
        long actualUsers = 0;
        long actualAttackers = 0;
        for (LoginRecord r : state.records) {
            int noise = rand.nextInt(3) - 1;
            int effective = Math.max(1, r.attemptsNeeded + noise);
            if (r.isUser() && effective > state.limit) actualUsers++;
            if (r.isAttacker() && effective > state.limit) actualAttackers++;
        }
        targetUsers = actualUsers;
        targetAttackers = actualAttackers;

        int sampleSize = Math.min(state.records.size(), 800);
        int step = Math.max(1, state.records.size() / sampleSize);
        sampleColors = new ArrayList<>();
        for (int i = 0; i < state.records.size(); i += step) {
            LoginRecord r = state.records.get(i);
            int noise = rand.nextInt(3) - 1;
            int effective = Math.max(1, r.attemptsNeeded + noise);
            boolean notable = (r.isUser() && effective > state.limit) || (r.isAttacker() && effective <= state.limit);
            sampleColors.add(notable ? new Color(220, 90, 80) : new Color(70, 190, 160));
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
            usersValue.setText(String.valueOf((long) (targetUsers * frac)));
            attackersValue.setText(String.valueOf((long) (targetAttackers * frac)));
            grid.repaint();

            if (revealed >= sampleColors.size()) {
                timer.stop();
                usersValue.setText(String.valueOf(targetUsers));
                attackersValue.setText(String.valueOf(targetAttackers));
                statusLabel.setText("Sandbox replay complete for a limit of " + state.limit + " attempts.");
                state.actualUsersLocked = targetUsers;
                state.actualAttackersBlocked = targetAttackers;
                state.sandboxDone = true;
                state.notifyListeners();
                runButton.setEnabled(true);
            }
        });
        timer.start();
    }

    static class SandboxGrid extends JPanel {
        private Color[] cells = new Color[0];

        SandboxGrid() {
            setBackground(new Color(18, 20, 25));
            setBorder(BorderFactory.createLineBorder(new Color(50, 54, 63)));
            setPreferredSize(new Dimension(760, 260));
        }

        void reset(int count) {
            cells = new Color[count];
            repaint();
        }

        void setCell(int index, Color c) {
            if (index >= 0 && index < cells.length) cells[index] = c;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (cells.length == 0) {
                g.setColor(new Color(120, 125, 135));
                g.drawString("Run the sandbox to see records being processed live.", 20, getHeight() / 2);
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
                g.setColor(c == null ? new Color(38, 41, 49) : c);
                g.fillRect(col * cellW + 1, row * cellH + 1, cellW - 2, cellH - 2);
            }
        }
    }
}
