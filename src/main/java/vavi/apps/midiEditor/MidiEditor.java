/*
 * Copyright (c) 2001 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.apps.midiEditor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;

import javax.sound.midi.ControllerEventListener;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToolBar;
import javax.swing.ProgressMonitorInputStream;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import vavi.sound.midi.MidiConstants;
import vavi.util.Debug;
import vavi.util.RegexFileFilter;


/**
 * MIDI ファイルのエディタです．
 * 
 * @author <a href="mailto:vavivavi@yahoo.co.jp">Naohide Sano</a> (nsano)
 * @version 0.00 010911 nsano initial version <br>
 *          0.01 031212 nsano refine file filter <br>
 */
public class MidiEditor {

    /** */
    private File file;

    /** */
    private Sequencer sequencer;

    /** */
    private Thread thread;

    private SequenceTable table;

    /** トラックセレクタ */
    private JComboBox<Integer> selector;

    /** */
    private JCheckBox[] solos;

    private JCheckBox[] mutes;

    private JLabel[] programs;

    /** ProgramChange だけを見せるかどうか */
    private JCheckBox onlyProgramChange;

    /** NoteOn だけを見せるかどうか */
    private JCheckBox onlyNoteOn;

    /** PitchBend だけを見せるかどうか */
    private JCheckBox onlyPitchBend;

    /** チャンネルをトラックに振り分けるかどうか */
    private JCheckBox dispatchChannel;

    private JSlider pointer;

    /** MIDI ファイルエディタを構築します． */
    private MidiEditor() {

        JFrame frame = new JFrame("MidiEditor");

        frame.setSize(new Dimension(600, 480));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // ----
        table = new SequenceTable();

        JScrollPane scrollPane = new JScrollPane(table);
        frame.getContentPane().add(scrollPane);

        // ----
        JToolBar toolBar = new JToolBar();

        // 
        toolBar.add(new JLabel("Track: "));
        selector = new JComboBox<>();
        selector.addItemListener(trackListener);
        toolBar.add(selector);

        dispatchChannel = new JCheckBox(dispatchAction);
        toolBar.add(dispatchChannel);

        frame.getContentPane().add(toolBar, BorderLayout.NORTH);

        // ----
        toolBar = new JToolBar();

        JButton button = new JButton(playAction);
        toolBar.add(button);

        button = new JButton(stopAction);
        toolBar.add(button);

        pointer = new JSlider();
        pointer.addChangeListener(pointerListener);
        pointer.setMinimum(0);
        pointer.setMaximum(10000);
        pointer.setValue(0);

        toolBar.add(pointer);

        frame.getContentPane().add(toolBar, BorderLayout.SOUTH);

        // ---- メニューの構築
        JMenuBar menuBar = new JMenuBar();

        JMenu menu = new JMenu("File");
        menu.add(openAction);
        menu.add(saveAction);
        menu.add(exitAction);

        menuBar.add(menu);
        frame.setJMenuBar(menuBar);

        // ---
        JDialog dialog = new JDialog(frame, "Channel Control", false);
        programs = new JLabel[16];
        solos = new JCheckBox[16];
        mutes = new JCheckBox[16];
        GridLayout layout = new GridLayout(17, 4);
        layout.setHgap(5);
        JPanel p = new JPanel(layout);
        p.add(new JLabel("Channel"));
        p.add(new JLabel("Current Program"));
        p.add(new JLabel("Solo"));
        p.add(new JLabel("Mute"));
        for (int i = 0; i < 16; i++) {
            p.add(new JLabel("" + (i + 1)));
            programs[i] = new JLabel();
            p.add(programs[i]);
            solos[i] = new JCheckBox(soloAction);
            solos[i].setActionCommand(String.valueOf(i));
            p.add(solos[i]);
            mutes[i] = new JCheckBox(muteAction);
            mutes[i].setActionCommand(String.valueOf(i));
            p.add(mutes[i]);
        }
        dialog.getContentPane().add(p);
        dialog.pack();
        dialog.setVisible(true);

        // ---
        dialog = new JDialog(frame, "Synthesizer", false);
        p = new MidiSynth();
        ((MidiSynth) p).open();
        dialog.getContentPane().add(p);
        dialog.pack();
        dialog.setVisible(true);

        // ---
        dialog = new JDialog(frame, "Filter", false);
        // ButtonGroup group = new ButtonGroup();
        p = new JPanel(new GridLayout(3, 1));
        onlyProgramChange = new JCheckBox(onlyPCAction);
        p.add(onlyProgramChange);
        // group.add(onlyProgramChange);
        onlyNoteOn = new JCheckBox(onlyNOAction);
        p.add(onlyNoteOn);
        // group.add(onlyNoteOn);
        onlyPitchBend = new JCheckBox(onlyPBAction);
        p.add(onlyPitchBend);
        // group.add(onlyPitchBend);
        dialog.getContentPane().add(p);
        dialog.pack();
        dialog.setVisible(true);

        // init midi
        try {
            sequencer = MidiSystem.getSequencer();
            sequencer.addMetaEventListener(mel);
//          int [] r = sequencer.addControllerEventListener(cel, new int[] { 192 });
//Debug.println(r.length + ", " + r[0]);
        } catch (Exception e) {
            System.err.println("MidiSystem Sequencer Unavailable, exiting!");
            System.exit(1);
        }

        // frame
        frame.setVisible(true);
    }

    private TableModelListener tml = new TableModelListener() {
        public void tableChanged(TableModelEvent ev) {
//Debug.println(ev.getColumn());
            if (4 == ev.getColumn()) {
                try {
                    Sequence sequence = sequencer.getSequence();
                    sequencer.setSequence(sequence);
                } catch (InvalidMidiDataException e) {
Debug.printStackTrace(e);
                }
            }
        }
    };

    /** */
    private RegexFileFilter fileFilter = new RegexFileFilter();

    /* */ {
        fileFilter.addPattern(".*\\.(mid|MID|mld|MLD)");
        fileFilter.setDescription("MIDI, MLD File");
    }

    /** */
    private Action openAction = new AbstractAction("Open...") {
        private JFileChooser fc = new JFileChooser();
        /* init */ {
            fc.setFileFilter(fileFilter);
            fc.setMultiSelectionEnabled(false);
        }

        public void actionPerformed(ActionEvent ev) {
            fc.setCurrentDirectory(cwd);
            if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                try {
                    open(fc.getSelectedFile());
                    fc.setCurrentDirectory(fc.getSelectedFile());
                    cwd = fc.getCurrentDirectory();
                } catch (Exception e) {
Debug.printStackTrace(e);
                }
            }
        }
    };

    /** */
    private Action playAction = new AbstractAction("Play") {
        public void actionPerformed(ActionEvent ev) {
            play();
        }
    };

    /** */
    private void open(File file) {
        this.file = file;
        Loader worker = new Loader();
        worker.execute();
    }

    /** */
    private void play() {
        if (thread == null) {
            thread = new Thread(new Runnable() {
                /** */
                public void run() {
                    Thread thisThread = Thread.currentThread();

                    sequencer.start();

                    while (thread == thisThread) {
                        try {
                            Thread.sleep(99);
                        } catch (Exception e) {
                        }

                        long len = sequencer.getMicrosecondLength();
                        long pos = sequencer.getMicrosecondPosition();
                        int value = (int) Math.round((double) pos / len * 10000);
                        pointer.removeChangeListener(pointerListener);
                        pointer.setValue(value);
                        pointer.addChangeListener(pointerListener);

                        if (sequencer instanceof Synthesizer) {
                            Synthesizer synthesizer = (Synthesizer) sequencer;
                            MidiChannel[] channels = synthesizer.getChannels();
                            for (int i = 0; i < 16; i++) {
                                int p = channels[i].getProgram();
                                String n = MidiConstants.getInstrumentName(p);
                                programs[i].setText(n);
                                channels[i].setSolo(solos[i].isSelected());
                                channels[i].setMute(mutes[i].isSelected());
                            }
                        }
                    }

                    sequencer.stop();
                }
            });
            thread.start();

            playAction.setEnabled(false);
            stopAction.setEnabled(true);
        }
    }

    /** */
    private Action stopAction = new AbstractAction("Stop") {
        public void actionPerformed(ActionEvent ev) {
            stop();
        }
    };

    private void stop() {
        if (thread != null) {
            thread = null;

            sequencer.stop();

            playAction.setEnabled(true);
            stopAction.setEnabled(false);
        }
    }

    /** */
    private Action saveAction = new AbstractAction("Save") {
        private JFileChooser fc = new JFileChooser();
        /* init */{
            fc.setFileFilter(fileFilter);
            fc.setMultiSelectionEnabled(false);
            File cwd = new File(System.getProperty("user.dir"));
            fc.setCurrentDirectory(cwd);
        }

        public void actionPerformed(ActionEvent ev) {
            if (fc.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                file = fc.getSelectedFile();
                try {
                    Sequence sequence = sequencer.getSequence();
                    MidiSystem.write(sequence, 0, file);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    /** */
    private Action exitAction = new AbstractAction("Exit") {
        public void actionPerformed(ActionEvent ev) {
            System.exit(0);
        }
    };

    /** */
    private Action soloAction = new AbstractAction() {
        public void actionPerformed(ActionEvent ev) {
            if (sequencer instanceof Synthesizer) {
                Synthesizer synthesizer = (Synthesizer) sequencer;
                int i = Integer.parseInt(ev.getActionCommand());
                MidiChannel[] channels = synthesizer.getChannels();
                channels[i].setSolo(solos[i].isSelected());
            } else {
Debug.println("sequencer is not synthesizer");
            }
        }
    };

    /** */
    private Action muteAction = new AbstractAction() {
        public void actionPerformed(ActionEvent ev) {
            if (sequencer instanceof Synthesizer) {
                Synthesizer synthesizer = (Synthesizer) sequencer;
                int i = Integer.parseInt(ev.getActionCommand());
                MidiChannel[] channels = synthesizer.getChannels();
                channels[i].allNotesOff();
                channels[i].setMute(mutes[i].isSelected());
            } else {
Debug.println("sequencer is not synthesizer");
            }
        }
    };

    /** */
    private Action dispatchAction = new AbstractAction("Dispatch Channel") {
        public void actionPerformed(ActionEvent ev) {
            table.removeTableModelListener(tml);
            table.setDispatchChannel(dispatchChannel.isSelected());
            table.addTableModelListener(tml);
            initSelector();
        }
    };

    /** */
    SequenceTable.Filter onlyPCfilter = new SequenceTable.Filter(ShortMessage.PROGRAM_CHANGE);

    /** */
    SequenceTable.Filter onlyNOfilter = new SequenceTable.Filter(ShortMessage.NOTE_ON);

    /** */
    SequenceTable.Filter onlyPBfilter = new SequenceTable.Filter(ShortMessage.PITCH_BEND);

    /** */
    private Action onlyPCAction = new AbstractAction("Program Change") {
        public void actionPerformed(ActionEvent ev) {
            table.removeTableModelListener(tml);
            if (onlyProgramChange.isSelected()) {
                table.addFilter(onlyPCfilter);
            } else {
                table.removeFilter(onlyPCfilter);
            }
            table.addTableModelListener(tml);
        }
    };

    /** */
    private Action onlyNOAction = new AbstractAction("Note On") {
        public void actionPerformed(ActionEvent ev) {
            table.removeTableModelListener(tml);
            if (onlyNoteOn.isSelected()) {
                table.addFilter(onlyNOfilter);
            } else {
                table.removeFilter(onlyNOfilter);
            }
            table.addTableModelListener(tml);
        }
    };

    /** */
    private Action onlyPBAction = new AbstractAction("Pitch Bend") {
        public void actionPerformed(ActionEvent ev) {
            table.removeTableModelListener(tml);
            if (onlyPitchBend.isSelected()) {
                table.addFilter(onlyPBfilter);
            } else {
                table.removeFilter(onlyPBfilter);
            }
            table.addTableModelListener(tml);
        }
    };

    /** */
    private void initSelector() {
        try {
            selector.removeAllItems();
            boolean[] tracks = table.getTracks();
            for (int i = 0; i < tracks.length; i++) {
                if (tracks[i]) {
                    selector.addItem(new Integer(i + 1));
                }
            }
        } catch (NullPointerException e) {
Debug.println(Level.WARNING, "tracks has not been set");            
        }
    }

    /** */
    private ItemListener trackListener = new ItemListener() {
        public void itemStateChanged(ItemEvent ev) {
            if (ev.getStateChange() == ItemEvent.SELECTED) {
                int i = ((Integer) selector.getSelectedItem()).intValue() - 1;
                table.removeTableModelListener(tml);
                table.setTrackNumber(i);
                table.addTableModelListener(tml);
            }
        }
    };

    /** */
    private MetaEventListener mel = new MetaEventListener() {
        public void meta(MetaMessage message) {
            if (message.getType() == 47) { // 47 is end of track
                stop();
            }
        }
    };

    /** */
    @SuppressWarnings("unused")
    private ControllerEventListener cel = new ControllerEventListener() {
        public void controlChange(ShortMessage message) {
            int command = message.getCommand();
Debug.println(command);
            if (command == 192) {
                int channel = message.getChannel();
                int data1 = message.getData1();
                String name = MidiConstants.getInstrumentName(data1);
                programs[channel].setText(name);
            }
        }
    };

    /** */
    private ChangeListener pointerListener = new ChangeListener() {
        public void stateChanged(ChangeEvent ev) {
            if (sequencer == null) {
                return;
            }
//Debug.println(pointer.getValueIsAdjusting());
            double ratio = (double) pointer.getValue() / pointer.getMaximum();
            long p = Math.round(sequencer.getMicrosecondLength() * ratio);
            sequencer.setMicrosecondPosition(p);
        }
    };

    /** テーブルデータのローダ */
    private class Loader extends SwingWorker<Void, Void> {
        /** テーブルデータをロードします． */
        public Void doInBackground() {

//Debug.println(Thread.currentThread());
            playAction.setEnabled(false);
            stopAction.setEnabled(false);
            pointer.setEnabled(false);

            onlyProgramChange.setSelected(false);
            onlyNoteOn.setSelected(false);

            // dispatchChannel.setSelected(false);

            try {
                // ファイルの読み込み
                Sequence sequence = MidiSystem.getSequence(
                        new ProgressMonitorInputStream(
                                null,
                                "読み込み中 " + file,
                                new BufferedInputStream(new FileInputStream(file))));

Debug.println("divisionType: " + sequence.getDivisionType());
Debug.println("resolution  : " + sequence.getResolution());
Debug.println("tickLength  : " + sequence.getTickLength());
                table.removeTableModelListener(tml);
                table.setSequence(sequence);
                table.addTableModelListener(tml);

                sequencer.open();
                sequencer.setSequence(sequence);
            } catch (Exception e) {
Debug.println(Level.SEVERE, e);
Debug.printStackTrace(e);
            }

            return null;
        }

        /** ロード終了後呼ばれます． */
        protected void done() {
            try {
                get();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

            initSelector();

            // boolean flag = sequencer.getSequence().getTracks().length == 1;
            // dispatchChannel.setEnabled(flag);

            playAction.setEnabled(true);
            stopAction.setEnabled(false);
            pointer.setEnabled(true);
        }
    }

    /** */
    private static File cwd = new File(System.getProperty("user.dir"));

    // -------------------------------------------------------------------------

    /** */
    public static void main(String[] args) {
        MidiEditor editor = new MidiEditor();
        if (args.length > 0) {
Debug.println(args[0]);
            File file = new File(args[0]);
            if (file.isDirectory()) {
                cwd = file;
            } else {
                editor.open(file);
            }
        }
    }
}

/* */
