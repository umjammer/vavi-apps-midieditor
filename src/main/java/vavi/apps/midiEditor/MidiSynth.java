/*
 * http://ehess.modelisationsavoirs.fr/marc/ens/projets/cours/java/
 */

package vavi.apps.midiEditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

import javax.sound.midi.Instrument;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Track;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import vavi.sound.midi.MidiConstants;

/**
 * Illustrates general MIDI melody instruments and MIDI controllers.
 *
 * @version @(#)MidiSynth.java    1.15 99/12/03
 * @author Brian Lichtenwalter
 */
public class MidiSynth extends JPanel {

    final int PROGRAM = 192;
    final int NOTEON = 144;
    final int NOTEOFF = 128;
    final int SUSTAIN = 64;
    final int REVERB = 91;
    final int ON = 0, OFF = 1;
    final Color jfcBlue = new Color(204, 204, 255);
    final Color pink = new Color(255, 175, 175);
    Sequencer sequencer;
    Sequence sequence;
    Synthesizer synthesizer;
    Instrument[] instruments;
    ChannelData[] channels;
    ChannelData cc;    // current channel
    JCheckBox mouseOverCB = new JCheckBox("mouseOver", true);
    JSlider veloS, presS, bendS, revbS;
    JCheckBox soloCB, monoCB, muteCB, sustCB;
    Vector<Key> keys = new Vector<>();
    Vector<Key> whiteKeys = new Vector<>();
    JTable table;
    Piano piano;
    boolean record;
    Track track;
    long startTime;
    RecordFrame recordFrame;
    Controls controls;

    public MidiSynth() {
        setLayout(new BorderLayout());

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
//        EmptyBorder eb = new EmptyBorder(5,5,5,5);
//        BevelBorder bb = new BevelBorder(BevelBorder.LOWERED);
//        CompoundBorder cb = new CompoundBorder(eb,bb);
//        p.setBorder(new CompoundBorder(cb,eb));
        JPanel pp = new JPanel(new BorderLayout());
        pp.setBorder(new EmptyBorder(10,20,10,5));
        pp.add(piano = new Piano());
        p.add(pp);
        p.add(controls = new Controls());
        p.add(new InstrumentsTable());

        add(p);
    }


    public void open() {
        try {
            if (synthesizer == null) {
                if ((synthesizer = MidiSystem.getSynthesizer()) == null) {
                    System.out.println("getSynthesizer() failed!");
                    return;
                }
            }
            synthesizer.open();
            sequencer = MidiSystem.getSequencer();
            sequence = new Sequence(Sequence.PPQ, 10);
        } catch (Exception ex) { ex.printStackTrace(); return; }

        Soundbank sb = synthesizer.getDefaultSoundbank();
    if (sb != null) {
            instruments = synthesizer.getDefaultSoundbank().getInstruments();
            synthesizer.loadInstrument(instruments[0]);
        }
        MidiChannel[] midiChannels = synthesizer.getChannels();
        channels = new ChannelData[midiChannels.length];
        for (int i = 0; i < channels.length; i++) {
            channels[i] = new ChannelData(midiChannels[i], i);
        }
        cc = channels[0];

        ListSelectionModel lsm = table.getSelectionModel();
        lsm.setSelectionInterval(0,0);
        lsm = table.getColumnModel().getSelectionModel();
        lsm.setSelectionInterval(0,0);
    }


    public void close() {
        if (synthesizer != null) {
            synthesizer.close();
        }
        if (sequencer != null) {
            sequencer.close();
        }
        sequencer = null;
        synthesizer = null;
        instruments = null;
        channels = null;
        if (recordFrame != null) {
            recordFrame.dispose();
            recordFrame = null;
        }
    }




    /**
     * given 120 bpm:
     *   (120 bpm) / (60 seconds per minute) = 2 beats per second
     *   2 / 1000 beats per millisecond
     *   (2 * resolution) ticks per second
     *   (2 * resolution)/1000 ticks per millisecond, or
     *      (resolution / 500) ticks per millisecond
     *   ticks = milliseconds * resolution / 500
     */
    public void createShortEvent(int type, int num) {
        ShortMessage message = new ShortMessage();
        try {
            long millis = System.currentTimeMillis() - startTime;
            long tick = millis * sequence.getResolution() / 500;
            message.setMessage(type+cc.num, num, cc.velocity);
            MidiEvent event = new MidiEvent(message, tick);
            track.add(event);
        } catch (Exception ex) { ex.printStackTrace(); }
    }


    /**
     * Black and white keys or notes on the piano.
     */
    class Key extends Rectangle {
        int noteState = OFF;
        int kNum;
        public Key(int x, int y, int width, int height, int num) {
            super(x, y, width, height);
            kNum = num;
        }
        public boolean isNoteOn() {
            return noteState == ON;
        }
        public void on() {
            setNoteState(ON);
            cc.channel.noteOn(kNum, cc.velocity);
            if (record) {
                createShortEvent(NOTEON, kNum);
            }
        }
        public void off() {
            setNoteState(OFF);
            cc.channel.noteOff(kNum, cc.velocity);
            if (record) {
                createShortEvent(NOTEOFF, kNum);
            }
        }
        public void setNoteState(int state) {
            noteState = state;
        }
    } // End class Key



    /**
     * Piano renders black & white keys and plays the notes for a MIDI
     * channel.
     */
    class Piano extends JPanel implements MouseListener {

        Vector<Key> blackKeys = new Vector<>();
        Key prevKey;
        final int kw = 16, kh = 80;


        public Piano() {
            setLayout(new BorderLayout());
            setPreferredSize(new Dimension(42*kw, kh+1));
            int transpose = 24;
            int[] whiteIDs = { 0, 2, 4, 5, 7, 9, 11 };

            for (int i = 0, x = 0; i < 6; i++) {
                for (int j = 0; j < 7; j++, x += kw) {
                    int keyNum = i * 12 + whiteIDs[j] + transpose;
                    whiteKeys.add(new Key(x, 0, kw, kh, keyNum));
                }
            }
            for (int i = 0, x = 0; i < 6; i++, x += kw) {
                int keyNum = i * 12 + transpose;
                blackKeys.add(new Key((x += kw)-4, 0, kw/2, kh/2, keyNum+1));
                blackKeys.add(new Key((x += kw)-4, 0, kw/2, kh/2, keyNum+3));
                x += kw;
                blackKeys.add(new Key((x += kw)-4, 0, kw/2, kh/2, keyNum+6));
                blackKeys.add(new Key((x += kw)-4, 0, kw/2, kh/2, keyNum+8));
                blackKeys.add(new Key((x += kw)-4, 0, kw/2, kh/2, keyNum+10));
            }
            keys.addAll(blackKeys);
            keys.addAll(whiteKeys);

            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    if (mouseOverCB.isSelected()) {
                        Key key = getKey(e.getPoint());
                        if (prevKey != null && prevKey != key) {
                            prevKey.off();
                        }
                        if (key != null && prevKey != key) {
                            key.on();
                        }
                        prevKey = key;
                        repaint();
                    }
                }
            });
            addMouseListener(this);
        }

        public void mousePressed(MouseEvent e) {
            prevKey = getKey(e.getPoint());
            if (prevKey != null) {
                prevKey.on();
                repaint();
            }
        }
        public void mouseReleased(MouseEvent e) {
            if (prevKey != null) {
                prevKey.off();
                repaint();
            }
        }
        public void mouseExited(MouseEvent e) {
            if (prevKey != null) {
                prevKey.off();
                repaint();
                prevKey = null;
            }
        }
        public void mouseClicked(MouseEvent e) { }
        public void mouseEntered(MouseEvent e) { }


        public Key getKey(Point point) {
            for (Key key : keys) {
                if (key.contains(point)) {
                    return key;
                }
            }
            return null;
        }

        public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            Dimension d = getSize();

            g2.setBackground(getBackground());
            g2.clearRect(0, 0, d.width, d.height);

            g2.setColor(Color.white);
            g2.fillRect(0, 0, 42*kw, kh);

            for (Key key : whiteKeys) {
                if (key.isNoteOn()) {
                    g2.setColor(record ? pink : jfcBlue);
                    g2.fill(key);
                }
                g2.setColor(Color.black);
                g2.draw(key);
            }
            for (Key key : blackKeys) {
                if (key.isNoteOn()) {
                    g2.setColor(record ? pink : jfcBlue);
                    g2.fill(key);
                    g2.setColor(Color.black);
                    g2.draw(key);
                } else {
                    g2.setColor(Color.black);
                    g2.fill(key);
                }
            }
        }
    } // End class Piano



    /**
     * Stores MidiChannel information.
     */
    class ChannelData {

        MidiChannel channel;
        boolean solo, mono, mute, sustain;
        int velocity, pressure, bend, reverb;
        int row, col, num;

        public ChannelData(MidiChannel channel, int num) {
            this.channel = channel;
            this.num = num;
            velocity = pressure = bend = reverb = 64;
        }

        public void setComponentStates() {
            table.setRowSelectionInterval(row, row);
            table.setColumnSelectionInterval(col, col);

            soloCB.setSelected(solo);
            monoCB.setSelected(mono);
            muteCB.setSelected(mute);
            //sustCB.setSelected(sustain);

            JSlider[] slider = { veloS, presS, bendS, revbS };
            int[] v = { velocity, pressure, bend, reverb };
            for (int i = 0; i < slider.length; i++) {
                TitledBorder tb = (TitledBorder) slider[i].getBorder();
                String s = tb.getTitle();
                tb.setTitle(s.substring(0, s.indexOf('=')+1)+ v[i]);
                slider[i].repaint();
            }
        }
    } // End class ChannelData



    /**
     * Table for 128 general MIDI melody instruments.
     */
    class InstrumentsTable extends JPanel {

        private String[] names = {
           "Piano", "Chromatic Perc.", "Organ", "Guitar",
           "Bass", "Strings", "Ensemble", "Brass",
           "Reed", "Pipe", "Synth Lead", "Synth Pad",
           "Synth Effects", "Ethnic", "Percussive", "Sound Effects" };
        private int nRows = 8;
        private int nCols = names.length; // just show 128 instruments

        public InstrumentsTable() {
            setLayout(new BorderLayout());

            TableModel dataModel = new AbstractTableModel() {
                public int getColumnCount() { return nCols; }
                public int getRowCount() { return nRows;}
                public Object getValueAt(int r, int c) {
                    if (instruments != null) {
                        return instruments[c*nRows+r].getName();
                    } else {
                        return c * nRows + r + " " +
                            MidiConstants.getInstrumentName(c*nRows+r);
                    }
                }
                public String getColumnName(int c) {
                    return names[c];
                }
                public Class<?> getColumnClass(int c) {
                    return getValueAt(0, c).getClass();
                }
                public boolean isCellEditable(int r, int c) {return false;}
                public void setValueAt(Object obj, int r, int c) {}
            };

            table = new JTable(dataModel);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            // Listener for row changes
            ListSelectionModel lsm = table.getSelectionModel();
            lsm.addListSelectionListener(e -> {
                ListSelectionModel sm = (ListSelectionModel) e.getSource();
                if (!sm.isSelectionEmpty()) {
                    cc.row = sm.getMinSelectionIndex();
                }
                programChange(cc.col*nRows+cc.row);
            });

            // Listener for column changes
            lsm = table.getColumnModel().getSelectionModel();
            lsm.addListSelectionListener(e -> {
                ListSelectionModel sm = (ListSelectionModel) e.getSource();
                if (!sm.isSelectionEmpty()) {
                    cc.col = sm.getMinSelectionIndex();
                }
                programChange(cc.col*nRows+cc.row);
            });

            table.setPreferredScrollableViewportSize(new Dimension(nCols*110, 200));
            table.setCellSelectionEnabled(true);
            table.setColumnSelectionAllowed(true);
            for (String name : names) {
                TableColumn column = table.getColumn(name);
                column.setPreferredWidth(110);
            }
            table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

            JScrollPane sp = new JScrollPane(table);
            sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            add(sp);
        }

        public Dimension getPreferredSize() {
            return new Dimension(800,170);
        }
        public Dimension getMaximumSize() {
            return new Dimension(800,170);
        }

        private void programChange(int program) {
            if (instruments != null) {
                synthesizer.loadInstrument(instruments[program]);
            }
            cc.channel.programChange(program);
            if (record) {
                createShortEvent(PROGRAM, program);
            }
        }
    }


    /**
     * A collection of MIDI controllers.
     */
    class Controls extends JPanel implements ActionListener, ChangeListener, ItemListener {

        public JButton recordB;
        JMenu menu;
        int fileNum = 0;

        public Controls() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(5,10,5,10));

            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));

            veloS = createSlider("Velocity", p);
            presS = createSlider("Pressure", p);
            revbS = createSlider("Reverb", p);

            // create a slider with a 14-bit range of values for pitch-bend
            bendS = create14BitSlider("Bend", p);

            p.add(Box.createHorizontalStrut(10));
            add(p);

            p = new JPanel();
            p.setBorder(new EmptyBorder(10,0,10,0));
            p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));

            JComboBox<String> combo = new JComboBox<>();
            combo.setPreferredSize(new Dimension(120,25));
            combo.setMaximumSize(new Dimension(120,25));
            for (int i = 1; i <= 16; i++) {
                combo.addItem("Channel " + i);
            }
            combo.addItemListener(this);
            p.add(combo);
            p.add(Box.createHorizontalStrut(20));

            muteCB = createCheckBox("Mute", p);
            soloCB = createCheckBox("Solo", p);
            monoCB = createCheckBox("Mono", p);
            //sustCB = createCheckBox("Sustain", p);

            createButton("All Notes Off", p);
            p.add(Box.createHorizontalStrut(10));
            p.add(mouseOverCB);
            p.add(Box.createHorizontalStrut(10));
            recordB = createButton("Record...", p);
recordB.setEnabled(false);
            add(p);
        }

        public JButton createButton(String name, JPanel p) {
            JButton b = new JButton(name);
            b.addActionListener(this);
            p.add(b);
            return b;
        }

        private JCheckBox createCheckBox(String name, JPanel p) {
            JCheckBox cb = new JCheckBox(name);
            cb.addItemListener(this);
            p.add(cb);
            return cb;
        }

        private JSlider createSlider(String name, JPanel p) {
            JSlider slider = new JSlider(JSlider.HORIZONTAL, 0, 127, 64);
            slider.addChangeListener(this);
            TitledBorder tb = new TitledBorder(new EtchedBorder());
            tb.setTitle(name + " = 64");
            slider.setBorder(tb);
            p.add(slider);
            p.add(Box.createHorizontalStrut(5));
            return slider;
        }

        private JSlider create14BitSlider(String name, JPanel p) {
            JSlider slider = new JSlider(JSlider.HORIZONTAL, 0, 16383, 8192);
            slider.addChangeListener(this);
            TitledBorder tb = new TitledBorder(new EtchedBorder());
            tb.setTitle(name + " = 8192");
            slider.setBorder(tb);
            p.add(slider);
            p.add(Box.createHorizontalStrut(5));
            return slider;
        }

        public void stateChanged(ChangeEvent e) {
            JSlider slider = (JSlider) e.getSource();
            int value = slider.getValue();
            TitledBorder tb = (TitledBorder) slider.getBorder();
            String s = tb.getTitle();
            tb.setTitle(s.substring(0, s.indexOf('=')+1) + value);
            if (s.startsWith("Velocity")) {
                cc.velocity = value;
            } else if (s.startsWith("Pressure")) {
                cc.channel.setChannelPressure(cc.pressure = value);
            } else if (s.startsWith("Bend")) {
                cc.channel.setPitchBend(cc.bend = value);
            } else if (s.startsWith("Reverb")) {
                cc.channel.controlChange(REVERB, cc.reverb = value);
            }
            slider.repaint();
        }

        public void itemStateChanged(ItemEvent e) {
            if (e.getSource() instanceof JComboBox) {
                JComboBox<?> combo = (JComboBox<?>) e.getSource();
                cc = channels[combo.getSelectedIndex()];
                cc.setComponentStates();
            } else {
                JCheckBox cb = (JCheckBox) e.getSource();
                String name = cb.getText();
                if (name.startsWith("Mute")) {
                    cc.channel.setMute(cc.mute = cb.isSelected());
                } else if (name.startsWith("Solo")) {
                    cc.channel.setSolo(cc.solo = cb.isSelected());
                } else if (name.startsWith("Mono")) {
                    cc.channel.setMono(cc.mono = cb.isSelected());
                } else if (name.startsWith("Sustain")) {
                    cc.sustain = cb.isSelected();
                    cc.channel.controlChange(SUSTAIN, cc.sustain ? 127 : 0);
                }
            }
        }

        public void actionPerformed(ActionEvent e) {
            JButton button = (JButton) e.getSource();
            if (button.getText().startsWith("All")) {
                for (ChannelData channel : channels) {
                    channel.channel.allNotesOff();
                }
                for (Key key : keys) {
                    key.setNoteState(OFF);
                }
            } else if (button.getText().startsWith("Record")) {
                if (recordFrame != null) {
                    recordFrame.toFront();
                } else {
                    recordFrame = new RecordFrame();
                }
            }
        }
    } // End class Controls



    /**
     * A frame that allows for midi capture & saving the captured data.
     */
    class RecordFrame extends JFrame implements ActionListener, MetaEventListener {

        public JButton recordB, playB, saveB;
        Vector<TrackData> tracks = new Vector<>();
        DefaultListModel<String> listModel = new DefaultListModel<>();
        TableModel dataModel;
//        JTable table;

        public RecordFrame() {
            super("Midi Capture");
            addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {recordFrame = null;}
            });

            sequencer.addMetaEventListener(this);
            try {
                sequence = new Sequence(Sequence.PPQ, 10);
            } catch (Exception ex) { ex.printStackTrace(); }

//         JPanel p1 = new JPanel(new BorderLayout());

            JPanel p2 = new JPanel();
            p2.setBorder(new EmptyBorder(5,5,5,5));
            p2.setLayout(new BoxLayout(p2, BoxLayout.X_AXIS));

            recordB = createButton("Record", p2, true);
            playB = createButton("Play", p2, false);
            saveB = createButton("Save...", p2, false);

            getContentPane().add("North", p2);

            String[] names = { "Channel #", "Instrument" };

            dataModel = new AbstractTableModel() {
                public int getColumnCount() { return names.length; }
                public int getRowCount() { return tracks.size();}
                public Object getValueAt(int row, int col) {
                    if (col == 0) {
                        return tracks.get(row).chanNum;
                    } else if (col == 1) {
                        return tracks.get(row).name;
                    }
                    return null;
                }
                public String getColumnName(int col) {return names[col]; }
                public Class<?> getColumnClass(int c) {
                    return getValueAt(0, c).getClass();
                }
                public boolean isCellEditable(int row, int col) {
                    return false;
                }
                public void setValueAt(Object val, int row, int col) {
                    if (col == 0) {
                        tracks.get(row).chanNum = (Integer) val;
                    } else if (col == 1) {
                        tracks.get(row).name = (String) val;
                    }
                }
            };

            table = new JTable(dataModel);
            TableColumn col = table.getColumn("Channel #");
            col.setMaxWidth(65);
            table.sizeColumnsToFit(0);

            JScrollPane scrollPane = new JScrollPane(table);
            EmptyBorder eb = new EmptyBorder(0,5,5,5);
            scrollPane.setBorder(new CompoundBorder(eb,new EtchedBorder()));

            getContentPane().add("Center", scrollPane);
            pack();
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            int w = 210;
            int h = 160;
            setLocation(d.width/2 - w/2, d.height/2 - h/2);
            setSize(w, h);
            setVisible(true);
        }


        public JButton createButton(String name, JPanel p, boolean state) {
            JButton b = new JButton(name);
            b.setFont(new Font("serif", Font.PLAIN, 10));
            b.setEnabled(state);
            b.addActionListener(this);
            p.add(b);
            return b;
        }


        public void actionPerformed(ActionEvent e) {
            JButton button = (JButton) e.getSource();
            if (button.equals(recordB)) {
                record = recordB.getText().startsWith("Record");
                if (record) {
                    track = sequence.createTrack();
                    startTime = System.currentTimeMillis();

                    // add a program change right at the beginning of
                    // the track for the current instrument
                    createShortEvent(PROGRAM,cc.col*8+cc.row);

                    recordB.setText("Stop");
                    playB.setEnabled(false);
                    saveB.setEnabled(false);
                } else {
                    String name = null;
                    if (instruments != null) {
                        name = instruments[cc.col*8+cc.row].getName();
                    } else {
                        name = Integer.toString(cc.col*8+cc.row);
                    }
                    tracks.add(new TrackData(cc.num+1, name, track));
                    table.tableChanged(new TableModelEvent(dataModel));
                    recordB.setText("Record");
                    playB.setEnabled(true);
                    saveB.setEnabled(true);
                }
            } else if (button.equals(playB)) {
                if (playB.getText().startsWith("Play")) {
                    try {
                        sequencer.open();
                        sequencer.setSequence(sequence);
                    } catch (Exception ex) { ex.printStackTrace(); }
                    sequencer.start();
                    playB.setText("Stop");
                    recordB.setEnabled(false);
                } else {
                    sequencer.stop();
                    playB.setText("Play");
                    recordB.setEnabled(true);
                }
            } else if (button.equals(saveB)) {
                try {
                    File file = new File(System.getProperty("user.dir"));
                    JFileChooser fc = new JFileChooser(file);
                    fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
                        public boolean accept(File f) {
                            if (f.isDirectory()) {
                                return true;
                            }
                            return false;
                        }
                        public String getDescription() {
                            return "Save as .mid file.";
                        }
                    });
                    if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                        saveMidiFile(fc.getSelectedFile());
                    }
                } catch (SecurityException ex) {
//                    JavaSound.showInfoDialog();
                    ex.printStackTrace();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }


        public void meta(MetaMessage message) {
            if (message.getType() == 47) {  // 47 is end of track
                playB.setText("Play");
                recordB.setEnabled(true);
            }
        }


        public void saveMidiFile(File file) {
            try {
                int[] fileTypes = MidiSystem.getMidiFileTypes(sequence);
                if (fileTypes.length == 0) {
                    System.out.println("Can't save sequence");
                } else {
                    if (MidiSystem.write(sequence, fileTypes[0], file) == -1) {
                        throw new IOException("Problems writing to file");
                    }
                }
            } catch (SecurityException ex) {
//                JavaSound.showInfoDialog();
                ex.printStackTrace();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        /** */
        class TrackData {
            Integer chanNum;
            String name;
            @SuppressWarnings("hiding")
            Track track;
            public TrackData(int chanNum, String name, Track track) {
                this.chanNum = chanNum;
                this.name = name;
                this.track = track;
            }
        }
    }

    /** */
    public static void main(String[] args) {
        MidiSynth midiSynth = new MidiSynth();
        midiSynth.open();
        JFrame f = new JFrame("Midi Synthesizer");
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {System.exit(0);}
        });
        f.getContentPane().add("Center", midiSynth);
        f.pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int w = 760;
        int h = 470;
        f.setLocation(screenSize.width/2 - w/2, screenSize.height/2 - h/2);
        f.setSize(w, h);
        f.setVisible(true);
    }
}
