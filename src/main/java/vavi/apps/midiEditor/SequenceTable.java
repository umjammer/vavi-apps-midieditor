/*
 * Copyright (c) 2002 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.apps.midiEditor;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import vavi.sound.midi.MidiConstants;
import vavi.util.Debug;
import vavi.util.StringUtil;


/**
 * TableModel for Midi Sequence
 * 
 * @author <a href="mailto:vavivavi@yahoo.co.jp">Naohide Sano</a> (nsano)
 * @version 0.00 020702 nsano initial version <br>
 *          0.01 030909 nsano refine <br>
 */
public class SequenceTable extends JTable {

    /** */
    private SequenceTableModel model;

    /** */
    public SequenceTable() {
        for (int i = 0; i < 128; i++) {
            programComboBox.addItem(i + " " + MidiConstants.getInstrumentName(i));
        }
    }

    /** */
    public void addTableModelListener(TableModelListener l) {
        model.addTableModelListener(l);
    }

    /** */
    public void removeTableModelListener(TableModelListener l) {
        if (model == null) {
            return;
        }
        model.removeTableModelListener(l);
    }

    /** table init */
    public void setSequence(Sequence sequence) {
        model = new SequenceTableModel(sequence);
Debug.println("model set: " + model.getClass().getName());
        this.setModel(model);

        this.getColumn(Column.tick.name())   .setCellRenderer(tcRenderer);
        this.getColumn(Column.channel.name()).setCellRenderer(tcRenderer);
        this.getColumn(Column.event.name())  .setCellRenderer(tcRenderer);
        this.getColumn(Column.data1.name())  .setCellRenderer(tcRenderer);
        this.getColumn(Column.data2.name())  .setCellRenderer(tcRenderer);

        this.getColumn(Column.data1.name()).setCellEditor(programCellEditor);

        for (int i = 0; i < model.getColumnCount(); i++) {
            String columnName = model.getColumnName(i);
            getColumn(columnName).setPreferredWidth(Column.valueOf(columnName).width);
        }
    }

    /** */
    public boolean[] getTracks() {
//Debug.printStackTrace(new Exception());
        return model.getTracks();
    }

    /** トラックナンバーを返します． */
    public void setTrackNumber(int trackNumber) {
        model.setTrackNumber(trackNumber);
    }

    /** */
    public void setDispatchChannel(boolean dispatchChannel) {
        model.setDispatchChannel(dispatchChannel);
    }

    /** */
    public void addFilter(Filter filter) {
        model.addFilter(filter);
    }

    /** */
    public void removeFilter(Filter filter) {
        model.removeFilter(filter);
    }

    /** テーブルのセルレンダラ */
    private TableCellRenderer tcRenderer = new DefaultTableCellRenderer() {
    	/** レンダラのテキストを設定します． */
    	public Component getTableCellRendererComponent(JTable table,
    	                                               Object value,
                        						       boolean isSelected,
                        						       boolean hasFocus,
                        						       int row,
                        						       int column) {
    	    if (isSelected) {
    	        super.setForeground(table.getSelectionForeground());
    	        super.setBackground(table.getSelectionBackground());
    	    } else {
    	        super.setForeground(table.getForeground());
    	        super.setBackground(table.getBackground());
    	    }
    	    
    	    setFont(table.getFont());
    	    
    	    if (hasFocus) {
    	        setBorder(UIManager.getBorder("Table.focusCellHighlightBorder"));
    	        if (table.isCellEditable(row, column)) {
    	            super.setForeground(UIManager.getColor("Table.focusCellForeground"));
    	            super.setBackground(UIManager.getColor("Table.focusCellBackground"));
    	        }
    	    } else {
    	        setBorder(noFocusBorder);
    	    }
    	    
    	    MidiEvent event = (MidiEvent) value;
    	    
    	    // 現在の Tick 値にある Midi メッセージを取り出す
    	    MidiMessage message = event.getMessage();
    	    // 現在の Tick 値の取得
    	    long tick = event.getTick();
    	    
    	    if (message instanceof ShortMessage) {
    	        ShortMessage msg = (ShortMessage) message;
    	        int channel = msg.getChannel();
    	        int command = msg.getCommand();
    	        int data1 = msg.getData1();
    	        int data2 = msg.getData2();
    	        switch (column) {
    	        case 1:	// tick
    	            setText(String.valueOf(tick));
    	            break;
    	        case 2:	// channel
    	            setText(String.valueOf(channel + 1));
    	            break;
    	        case 3:	// event
    	            setText(getChannelMessage(command, data1));
    	            break;
    	        case 4:	// data1
    	            if (command == ShortMessage.PROGRAM_CHANGE) { 
    	                setText(String.valueOf(data1) + " " + MidiConstants.getInstrumentName(data1));
    	            } else {
    	                setText(String.valueOf(data1));
    	            }
    	            break;
    	        case 5:	// data2
    	            setText(String.valueOf(data2));
    	            break;
    	        }
    	    } else if (message instanceof SysexMessage) {
    	        // Sysex のデータを取り出す
    	        SysexMessage msg = (SysexMessage) message;
    	        byte[] data = msg.getData();
    	        StringBuilder sb = new StringBuilder();
    	        for (int i = 0; i < data.length; i++) {
    	            sb.append(StringUtil.toHex2(data[i]));
    	            sb.append(" ");
    	        }
    	        switch (column) {
    	        case 1:	// tick
    	            setText(String.valueOf(tick));
    	            break;
    	        case 2:	// channel
    	            setText("n/a");
    	            break;
    	        case 3:	// event
    	            setText("SYSX");
    	            break;
    	        case 4:	// data1
    	            setText(sb.toString());
    	            break;
    	        case 5:	// data2
    	            setText("");
    	            break;
    	        }
    	    } else if (message instanceof MetaMessage) {
    	        // MetaMessageのデータを取り出す
    	        MetaMessage msg = (MetaMessage) message;
    	        int type = msg.getType();
    	        byte[] data = msg.getData();
    	        StringBuilder sb = new StringBuilder();
    	        for (int i = 0; i < data.length; i++) {
    	            sb.append(StringUtil.toHex2(data[i]));
    	            sb.append(" ");
    	        }
    	        switch (column) {
    	        case 1:	// tick
    	            setText(String.valueOf(tick));
    	            break;
    	        case 2:	// channel
    	            setText("n/a");
    	            break;
    	        case 3:	// event
    	            setText("META");
    	            break;
    	        case 4:	// data1
    	            setText(String.valueOf(type));
    	            break;
    	        case 5:	// data2
    	            setText(sb.toString());
    	            break;
    	        }
    	    }
    	    
    	    return this;
    	}
    	
    	/** チャンネルメッセージ名を取得します． */
    	private String getChannelMessage(int statusByte, int value1) {
    	    switch (statusByte / 16) {
    	    case 8:    // 128
    	        return "NOTE_OFF";
    	    case 9:    // 144
    	        return "NOTE_ON";
    	    case 10:   // 160
    	        return "POLY_PRESSURE";
    	    case 11:   // 176
    	        if (value1 >= 120) {
    	            return "CHANNEL_MODE_MESSAGE";
    	        } else {
    	            return "CONTROL_CHANGE";
    	        }
    	    case 12:   // 192
    	        return "PROGRAM_CHANGE";
    	    case 13:   // 208
    	        return "CHANNEL_PRESSURE";
    	    case 14:   // 224
    	        return "PITCH_BEND_CHANGE";
    	    default:
    	        return String.valueOf(statusByte);
    	    }
    	}
    };

    //-------------------------------------------------------------------------

    /** */
    private JComboBox programComboBox = new JComboBox();

    /** */
    private TableCellEditor programCellEditor = new DefaultCellEditor(programComboBox) {
    	/** */
    	private MidiEvent event;
    	int column;
    	int row;
    	int data1;
    	/** */
    	public Component getTableCellEditorComponent(JTable table,
                        						     Object value,
                        						     boolean isSelected,
                        						     int row,
                        						     int column) {
//Debug.println(value);
    	    this.column = column;
    	    this.row = row;
    	    event = (MidiEvent) value;
    	    ShortMessage message = (ShortMessage) event.getMessage();
    	    data1 = message.getData1();
    	    
    	    ((JComboBox) getComponent()).setSelectedIndex(data1);
    	    
    	    return super.getTableCellEditorComponent(table, value, isSelected, row, column);
    	}
    	/** */
    	public boolean stopCellEditing() {
    	    if (model != null) {
    	        int data1 = ((JComboBox) getComponent()).getSelectedIndex();
    	        if (this.data1 != data1) {
Debug.println(this.data1 + " -> " + data1);
                    ShortMessage message = (ShortMessage) event.getMessage();
                    int channel = message.getChannel();
                    int command = message.getCommand();
                    try {
                        message.setMessage(command, channel, data1, 0);
                    } catch (Exception e) {
Debug.println(e);
                        return false;
                    }
                    this.data1 = data1;
                    model.fireTableChanged(new TableModelEvent(model, row, row, column));
                }
    	    }
    	    return true;
    	}
    };

    //-------------------------------------------------------------------------

    /** */
    public static class Filter {
        /** */
        public int command;
        /** */
        public Filter(int command) {
            this.command = command;
        }
        /** */
        public boolean accept(MidiMessage message) {
            if (message instanceof ShortMessage) {
                int command = ((ShortMessage) message).getCommand();
                if (command == this.command) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    //-------------------------------------------------------------------------

    /** カラムの名前 */
    enum Column {
        track(50),
        tick(100),
        channel(60),
        event(150),
        data1(150),
        data2(100);
        int width;
        Column(int width) {
            this.width = width;
        }
    };

    /** */
    private class SequenceTableModel extends AbstractTableModel {

        /** */
        private Sequence sequence;

        /** */
        private List<MidiEvent> events;

        /** トラック番号 */
        private int trackNumber;

        /** */
        private boolean[] tracks;

        /** */
        private boolean dispatchChannel = false;
        /** */
        private Set<Filter> filters = new HashSet<Filter>();

        /** テーブルモデルを構築します． */
        public SequenceTableModel(Sequence sequence) {

            if (sequence.getTracks().length < 1) {
                throw new IllegalArgumentException("no tracks");
            }

            this.tracks = new boolean[sequence.getTracks().length == 1 ? 16 : sequence.getTracks().length];

            this.sequence = sequence;

            events = new ArrayList<MidiEvent>();

            trackNumber = 0;

            initModel();
        }

        /** */
        public boolean[] getTracks() {
            return tracks;
        }

        /** トラックナンバーを返します． */
        public int getTrackNumber() {
            return trackNumber;
        }

        /** トラックナンバーを返します． */
        public void setTrackNumber(int trackNumber) {
            this.trackNumber = trackNumber;
Debug.println(this.trackNumber);

            initModel();
        }

        /** */
        public void setDispatchChannel(boolean dispatchChannel) {
            this.dispatchChannel = dispatchChannel;

            trackNumber = 0;

            initModel();
        }

        /** */
        public void addFilter(Filter filter) {
            this.filters.add(filter);
            initModel();
        }

        /** */
        public void removeFilter(Filter filter) {
            this.filters.remove(filter);
            initModel();
        }

        /** */
        private void initModel() {
            events.clear();

            if (dispatchChannel) {
                // tracks
                for (int i = 0; i < 16; i++) {
                    tracks[i] = false;
                }

                // events, tracks
                for (int j = 0; j < sequence.getTracks().length; j++) {
                    Track track = sequence.getTracks()[j];
                    for (int i = 0; i < track.size(); i++) {
                        MidiEvent event = track.get(i);
                        MidiMessage message = event.getMessage();
                        if (message instanceof ShortMessage) {
                            int channel = ((ShortMessage) message).getChannel();
                            if (channel == trackNumber) {
                                addEvent(event);
                            }
                            tracks[channel] = true;
                        } else {
                            if (0 == trackNumber) {
                                addEvent(event);
                            }
                        }
                    }
                }
            } else {
                // events
                Track track = sequence.getTracks()[trackNumber];
                for (int i = 0; i < track.size(); i++) {
                    MidiEvent event = track.get(i);

                    addEvent(event);
                }

                // tracks
                int trackSize = sequence.getTracks().length;
                for (int i = 0; i < trackSize; i++) {
                    tracks[i] = true;
                }
                for (int i = trackSize; i < Math.min(16, trackSize); i++) {
                    tracks[i] = false;
                }
            }

            fireTableChanged(new TableModelEvent(this));
        }

        /** */
        private void addEvent(MidiEvent event) {
            MidiMessage message = event.getMessage();
            if (filters.size() == 0) {
                events.add(event);
                return;
            }
            for (Filter filter : filters) {
                if (filter.accept(message)) {
                    events.add(event);
                    return;
                }
            }
        }

        //---------------------------------------------------------------------

        /** カラム数を取得します． */
        public int getColumnCount() {
            return Column.values().length;
        }

        /** カラム名を取得します． */
        public String getColumnName(int columnIndex) {
            return Column.values()[columnIndex].name();
        }

        /** 行数を取得します． */
        public int getRowCount() {
            return events.size();
        }

        /** 指定したカラム，行にある値を取得します． */
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                if (dispatchChannel) {
                    return String.valueOf(trackNumber + 1) + " : " + rowIndex;
                } else {
                    return 1 + " : " + rowIndex;
                }
            } else {
                return events.get(rowIndex);
            }
        }

        /** カラムのクラスを取得します． */
        public Class<?> getColumnClass(int columnIndex) {
            return getValueAt(0, columnIndex).getClass();
        }

        /** 指定したセルが編集可能かどうか． */
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            MidiMessage message = events.get(rowIndex).getMessage();
            if (message instanceof ShortMessage &&
                ((ShortMessage) message).getCommand() == ShortMessage.PROGRAM_CHANGE && 
                columnIndex == 4) {
                return true;
            } else {
                return false;
            }
        }
    }
}

/* */
