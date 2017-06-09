package application.controller;

import com.android.ddmlib.input.EventData;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.input.InputDevice;
import com.android.ddmlib.input.TouchEvent;
import com.android.ddmlib.input.TouchEventObserver;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ResourceBundle;

public class MonitorController implements Initializable, AndroidDebugBridge.IDeviceChangeListener, TouchEventObserver.onTouchEventListener {
    @FXML
    ChoiceBox<InputDevice> choicebox_device;
    @FXML
    ToolBar toolbar_main;
    @FXML
    Button toolbtn_start, toolbtn_pause, toolbtn_stop, toolbtn_replay;
    @FXML
    Pane panel_table;
    @FXML
    TableView tableview_events;
    @FXML
    Pane root;

    private TouchEventObserver mEventObserver;


    private StringConverter inputDevConverter = new StringConverter<InputDevice>() {
        @Override
        public String toString(InputDevice object) {
            return object.getDevFile();
        }

        @Override
        public InputDevice fromString(String string) {
            return null;
        }
    };


    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        AndroidDebugBridge.addDeviceChangeListener(this);
        ObservableList<TableColumn> observableList = tableview_events.getColumns();
        observableList.get(0).setCellValueFactory(new PropertyValueFactory("eventType"));
        observableList.get(1).setCellValueFactory(new PropertyValueFactory("eventDesc"));
        observableList.get(2).setCellValueFactory(new PropertyValueFactory("eventDur"));
        observableList.get(3).setCellValueFactory(new PropertyValueFactory("progress"));
        observableList.get(3).setCellFactory(ProgressBarTableCell.forTableColumn());
        ObservableList<EventData> data = FXCollections.observableArrayList();
        tableview_events.setItems(data);
        choicebox_device.setTooltip(new Tooltip("Select..."));
        choicebox_device.setConverter(inputDevConverter);
    }

    public void doStartMonitor(ActionEvent ev) {
        InputDevice inputDev=choicebox_device.getSelectionModel().getSelectedItem();
        inputDev.getTouchEventObserver().setTouchEventListener(this);
        inputDev.getTouchEventObserver().monitor();
    }
    public void doPauseMonitor(ActionEvent ev) {



    }
    public void doStopMonitor(ActionEvent ev) {



    }
    public void doReplayMonitor(ActionEvent ev) {



    }

    @Override
    public void deviceConnected(IDevice device) {
        updateInputDeviceList(device);
    }

    @Override
    public void deviceDisconnected(IDevice device) {

    }

    @Override
    public void deviceChanged(IDevice device, int changeMask) {
        updateInputDeviceList(device);
    }

    private void updateInputDeviceList(IDevice device) {
        choicebox_device.getItems().addAll(device.getInputDeviceManager().getDevice(false));
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                //更新JavaFX的主线程的代码放在此处
                choicebox_device.getSelectionModel().select(0);
            }
        });
        MainController.Companion.getCurrent().setStatus("请选择输入设备");
    }


    @Override
    public void onMonitorStarted() {

    }

    @Override
    public void onMonitorStoped() {

    }

    @Override
    public void onTouchEvent(TouchEvent event) {
           System.out.println("onTouchEvent");
           tableview_events.getItems().add(event);
    }
}
