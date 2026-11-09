import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.Arrays;

public class CPUSimulator extends JFrame {

    // ---------- CPU state ----------
    int[] reg = new int[4];          // R0 - R3
    int[] mem = new int[8];          // data memory
    int pc = 0, mar = 0, mdr = 0, zf = 0;
    String ir = "-";
    String[] program = new String[0]; // instruction memory

    // ---------- control ----------
    int stage = 0;        // next stage: 0=Fetch 1=Decode 2=Execute 3=Write-back
    int lastStage = -1;
    int cycle = 1;
    boolean halted = false;

    // decoded instruction
    String op = "";
    String[] args = new String[0];
    int result = 0, wbType = 0, wbIndex = 0; // wbType: 0 none, 1 register, 2 memory

    // ---------- GUI ----------
    String[] stageNames = {"1. FETCH", "2. DECODE", "3. EXECUTE", "4. WRITE-BACK"};
    Color[] stageColors = {
        new Color(52, 152, 219), new Color(243, 156, 18),
        new Color(46, 204, 113), new Color(155, 89, 182)
    };
    String[] regNames = {"PC", "MAR", "MDR", "IR", "R0", "R1", "R2", "R3", "Z flag"};

    JLabel[] stageLabels = new JLabel[4];
    JTextArea programArea = new JTextArea();
    JTextArea logArea = new JTextArea();
    JButton runBtn;
    javax.swing.Timer timer;

    DefaultTableModel regModel = new DefaultTableModel(
            new String[]{"Register", "Before stage", "After stage"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };
    DefaultTableModel memModel = new DefaultTableModel(
            new String[]{"Address", "Value"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };

    public CPUSimulator() {
        setTitle("Instruction Execution and Sequencing Simulator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(new Color(236, 240, 241));

        // ----- top: title + stage indicators -----
        JPanel top = new JPanel(new BorderLayout(5, 5));
        top.setOpaque(false);
        JLabel title = new JLabel("CPU Instruction Cycle Simulator", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setOpaque(true);
        title.setBackground(new Color(44, 62, 80));
        title.setForeground(Color.WHITE);
        title.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        top.add(title, BorderLayout.NORTH);

        JPanel stagePanel = new JPanel(new GridLayout(1, 4, 8, 8));
        stagePanel.setOpaque(false);
        stagePanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        for (int i = 0; i < 4; i++) {
            stageLabels[i] = new JLabel(stageNames[i], SwingConstants.CENTER);
            stageLabels[i].setOpaque(true);
            stageLabels[i].setFont(new Font("Segoe UI", Font.BOLD, 16));
            stageLabels[i].setBorder(BorderFactory.createEmptyBorder(12, 5, 12, 5));
            stagePanel.add(stageLabels[i]);
        }
        top.add(stagePanel, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        // ----- center: program | registers | memory -----
        JPanel center = new JPanel(new GridLayout(1, 3, 8, 8));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        // program editor
        programArea.setFont(new Font("Consolas", Font.PLAIN, 16));
        programArea.setText(
            "LOAD R0, #5\n" +
            "LOAD R1, #0\n" +
            "LOAD R2, #1\n" +
            "ADD R1, R0\n" +
            "SUB R0, R2\n" +
            "JNZ 3\n" +
            "STR R1, 0\n" +
            "HALT\n");
        JButton loadBtn = makeButton("Load Program", new Color(41, 128, 185));
        loadBtn.addActionListener(e -> resetCPU());
        JPanel progPanel = new JPanel(new BorderLayout(4, 4));
        progPanel.add(new JScrollPane(programArea), BorderLayout.CENTER);
        progPanel.add(loadBtn, BorderLayout.SOUTH);
        progPanel.setBorder(BorderFactory.createTitledBorder("Program (instruction memory)"));
        center.add(progPanel);

        // register table
        JTable regTable = new JTable(regModel);
        regTable.setRowHeight(28);
        regTable.setFont(new Font("Consolas", Font.PLAIN, 15));
        regTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        regTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                Component c = super.getTableCellRendererComponent(t, v, false, false, row, col);
                boolean changed = !String.valueOf(t.getValueAt(row, 1))
                        .equals(String.valueOf(t.getValueAt(row, 2)));
                c.setBackground(changed ? new Color(255, 235, 59) : Color.WHITE);
                c.setForeground(Color.BLACK);
                return c;
            }
        });
        JScrollPane regScroll = new JScrollPane(regTable);
        regScroll.setBorder(BorderFactory.createTitledBorder("Registers (yellow = changed)"));
        center.add(regScroll);

        // data memory table
        JTable memTable = new JTable(memModel);
        memTable.setRowHeight(28);
        memTable.setFont(new Font("Consolas", Font.PLAIN, 15));
        memTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        JScrollPane memScroll = new JScrollPane(memTable);
        memScroll.setBorder(BorderFactory.createTitledBorder("Data Memory"));
        center.add(memScroll);
        add(center, BorderLayout.CENTER);

        // ----- bottom: log + buttons -----
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        logArea.setBackground(new Color(30, 39, 46));
        logArea.setForeground(new Color(46, 204, 113));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(100, 180));
        logScroll.setBorder(BorderFactory.createTitledBorder("Execution log"));

        JButton stepBtn = makeButton("Step (next stage)", new Color(39, 174, 96));
        runBtn = makeButton("Run", new Color(230, 126, 34));
        JButton resetBtn = makeButton("Reset", new Color(192, 57, 43));
        stepBtn.addActionListener(e -> step());
        resetBtn.addActionListener(e -> resetCPU());
        runBtn.addActionListener(e -> {
            if (timer.isRunning()) { timer.stop(); runBtn.setText("Run"); }
            else if (!halted) { timer.start(); runBtn.setText("Pause"); }
        });
        timer = new javax.swing.Timer(700, e -> {
            step();
            if (halted) runBtn.setText("Run");
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));
        btnPanel.setOpaque(false);
        btnPanel.add(stepBtn);
        btnPanel.add(runBtn);
        btnPanel.add(resetBtn);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        bottom.add(logScroll, BorderLayout.CENTER);
        bottom.add(btnPanel, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        resetCPU();
    }

    JButton makeButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("Segoe UI", Font.BOLD, 15));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        return b;
    }

    // ---------- helpers ----------
    String[] values() {
        return new String[]{"" + pc, "" + mar, "" + mdr, ir,
            "" + reg[0], "" + reg[1], "" + reg[2], "" + reg[3], "" + zf};
    }

    void log(String s) {
        logArea.append(s + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    void refresh(String[] before) {
        String[] now = values();
        regModel.setRowCount(0);
        for (int i = 0; i < regNames.length; i++)
            regModel.addRow(new Object[]{regNames[i], before[i], now[i]});
        memModel.setRowCount(0);
        for (int i = 0; i < mem.length; i++)
            memModel.addRow(new Object[]{i, mem[i]});
        for (int i = 0; i < 4; i++) {
            if (i == lastStage) {
                stageLabels[i].setBackground(stageColors[i]);
                stageLabels[i].setForeground(Color.WHITE);
            } else {
                stageLabels[i].setBackground(new Color(210, 215, 220));
                stageLabels[i].setForeground(Color.DARK_GRAY);
            }
        }
    }

    int r(String s) {            // parse register like R2
        int n = Integer.parseInt(s.trim().substring(1));
        if (s.trim().toUpperCase().charAt(0) != 'R' || n < 0 || n > 3)
            throw new IllegalArgumentException("Bad register: " + s);
        return n;
    }

    int num(String s) {          // parse #5 or 5
        return Integer.parseInt(s.trim().replace("#", ""));
    }

    // ---------- reset / load ----------
    void resetCPU() {
        if (timer != null) timer.stop();
        if (runBtn != null) runBtn.setText("Run");
        Arrays.fill(reg, 0);
        Arrays.fill(mem, 0);
        pc = 0; mar = 0; mdr = 0; zf = 0; ir = "-";
        stage = 0; lastStage = -1; cycle = 1; halted = false;

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (String l : programArea.getText().split("\n")) {
            l = l.trim();
            if (!l.isEmpty() && !l.startsWith(";")) lines.add(l);
        }
        program = lines.toArray(new String[0]);
        logArea.setText("");
        log("Program loaded: " + program.length + " instructions. Press Step or Run.");
        refresh(values());
    }

    // ---------- the 4 stages ----------
    void step() {
        if (halted) { log("CPU halted. Press Reset to run again."); timer.stop(); return; }
        String[] before = values();
        try {
            switch (stage) {
                case 0: fetch(); break;
                case 1: decode(); break;
                case 2: execute(); break;
                case 3: writeBack(); break;
            }
        } catch (Exception ex) {
            log("ERROR: invalid instruction '" + ir + "' (" + ex.getMessage() + ")");
            halted = true;
        }
        lastStage = stage;
        stage = (stage + 1) % 4;
        refresh(before);
        if (halted) timer.stop();
    }

    void fetch() {
        if (pc >= program.length) {
            log("[Cycle " + cycle + "] FETCH: PC beyond program end. CPU stopped.");
            halted = true;
            return;
        }
        mar = pc;                 // MAR <- PC
        ir = program[mar];        // IR  <- Memory[MAR]
        mdr = mar;                // (MDR shows address just read)
        pc = pc + 1;              // PC  <- PC + 1
        log("[Cycle " + cycle + "] FETCH: MAR=PC=" + mar + ", IR <- '" + ir + "', PC=" + pc);
    }

    void decode() {
        String[] p = ir.replace(",", " ").trim().split("\\s+");
        op = p[0].toUpperCase();
        args = Arrays.copyOfRange(p, 1, p.length);
        log("[Cycle " + cycle + "] DECODE: opcode=" + op + ", operands=" + Arrays.toString(args));
    }

    void execute() {
        wbType = 0;
        switch (op) {
            case "LOAD":
                result = num(args[1]); wbType = 1; wbIndex = r(args[0]);
                log("[Cycle " + cycle + "] EXECUTE: immediate value " + result);
                break;
            case "MOV":
                result = reg[r(args[1])]; wbType = 1; wbIndex = r(args[0]);
                log("[Cycle " + cycle + "] EXECUTE: copy value " + result);
                break;
            case "ADD":
                result = reg[r(args[0])] + reg[r(args[1])];
                wbType = 1; wbIndex = r(args[0]); zf = (result == 0) ? 1 : 0;
                log("[Cycle " + cycle + "] EXECUTE: ALU add -> " + result);
                break;
            case "SUB":
                result = reg[r(args[0])] - reg[r(args[1])];
                wbType = 1; wbIndex = r(args[0]); zf = (result == 0) ? 1 : 0;
                log("[Cycle " + cycle + "] EXECUTE: ALU subtract -> " + result);
                break;
            case "MUL":
                result = reg[r(args[0])] * reg[r(args[1])];
                wbType = 1; wbIndex = r(args[0]); zf = (result == 0) ? 1 : 0;
                log("[Cycle " + cycle + "] EXECUTE: ALU multiply -> " + result);
                break;
            case "LDM":   // load register from data memory
                mar = num(args[1]); mdr = mem[mar]; result = mdr;
                wbType = 1; wbIndex = r(args[0]);
                log("[Cycle " + cycle + "] EXECUTE: MAR=" + mar + ", MDR <- Mem[" + mar + "]=" + mdr);
                break;
            case "STR":   // store register to data memory
                mar = num(args[1]); result = reg[r(args[0])]; mdr = result;
                if (mar < 0 || mar >= mem.length) throw new IllegalArgumentException("bad address");
                wbType = 2; wbIndex = mar;
                log("[Cycle " + cycle + "] EXECUTE: MAR=" + mar + ", MDR=" + mdr);
                break;
            case "JMP":
                pc = num(args[0]);
                log("[Cycle " + cycle + "] EXECUTE: jump, PC=" + pc);
                break;
            case "JNZ":
                if (zf == 0) { pc = num(args[0]); log("[Cycle " + cycle + "] EXECUTE: Z=0, jump taken, PC=" + pc); }
                else log("[Cycle " + cycle + "] EXECUTE: Z=1, jump not taken");
                break;
            case "HALT":
                halted = true;
                log("[Cycle " + cycle + "] EXECUTE: HALT - CPU stopped.");
                break;
            default:
                throw new IllegalArgumentException("unknown opcode " + op);
        }
    }

    void writeBack() {
        if (wbType == 1) {
            reg[wbIndex] = result;
            log("[Cycle " + cycle + "] WRITE-BACK: R" + wbIndex + " <- " + result);
        } else if (wbType == 2) {
            mem[wbIndex] = result;
            log("[Cycle " + cycle + "] WRITE-BACK: Mem[" + wbIndex + "] <- " + result);
        } else {
            log("[Cycle " + cycle + "] WRITE-BACK: nothing to write");
        }
        log("---------------------------------------------");
        cycle++;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CPUSimulator().setVisible(true));
    }
}