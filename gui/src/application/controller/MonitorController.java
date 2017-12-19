package application.controller;

import com.android.ddmlib.adb.AndroidDebugBridge;
import com.android.ddmlib.adb.IDevice;
import com.android.ddmlib.controller.Type;
import com.android.ddmlib.input.*;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ResourceBundle;

public class MonitorController implements Initializable, AndroidDebugBridge.IDeviceChangeListener, OnTouchEventListener, PlayerListener {
    @FXML
    ToolBar toolbar_main;
    @FXML
    Button toolbtn_start, toolbtn_pause, toolbtn_stop, toolbtn_replay;
    @FXML
    Pane panel_table;
    @FXML
    TableView<MonitorEvent> tableview_events;
    @FXML
    SplitPane root;

    private IDevice curDev;

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
        tableview_events.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        AndroidDebugBridge.addDeviceChangeListener(this);
        ObservableList<TableColumn<MonitorEvent, ?>> observableList = tableview_events.getColumns();
        observableList.get(0).setCellValueFactory(new PropertyValueFactory("inputDevice"));
        observableList.get(1).setCellValueFactory(new PropertyValueFactory("eventType"));
        observableList.get(2).setCellValueFactory(new PropertyValueFactory("eventDesc"));
        observableList.get(3).setCellValueFactory(new PropertyValueFactory("eventDur"));
        observableList.get(4).setCellValueFactory(new PropertyValueFactory("status"));
    }

    public void doClean(ActionEvent ev) {
        tableview_events.getItems().clear();
    }

    public void doMoveup(ActionEvent ev) {


    }

    public void doMoveDown(ActionEvent ev) {


    }

    public void doReplayMonitor(ActionEvent ev) {
        MainController.Companion.getCurrent().setStatus("初始化回放通道中...");
        EventPlayerImpl ep = new EventPlayerImpl(curDev.getRemoteControler(Type.MONKEY), this);
        MainController.Companion.getCurrent().setStatus("初始化回放通道完成");
        ObservableList<MonitorEvent> olist = tableview_events.getItems();
        for (int i = 0; i < olist.size(); i++) {
            MonitorEvent cur = olist.get(i);
            cur.statusProperty().set("等待中...");
            ep.addData(cur);
            if (i + 1 < olist.size()) {
                MonitorEvent next = olist.get(i + 1);
                ep.sleep((next.beginTime() - cur.endTime()));
            }
        }
        ep.start();
    }

    @Override
    public void deviceConnected(IDevice device) {
        updateInputDeviceList(device);
    }

    @Override
    public void deviceDisconnected(IDevice device) {
        setDisable(true);
    }


    public void setDisable(boolean disable) {
        Platform.runLater(() -> {
            root.disableProperty().setValue(disable);
            if (disable) MainController.Companion.getCurrent().setStatus("请连接你的手机");
        });
    }

    @Override
    public void deviceChanged(IDevice device, int changeMask) {
        updateInputDeviceList(device);
    }

    private void updateInputDeviceList(IDevice device) {
        if (device.isOnline()) {
            curDev = device;
            setDisable(false);
            MainController.Companion.getCurrent().setStatus("已连接:" + device.getName());
            curDev.getInputManager().addOnTouchEventListener(this);
        } else {
            curDev = null;
        }
    }

    @Override
    public boolean onTouchEvent(MonitorEvent monitorEvent) {
        tableview_events.getItems().add(monitorEvent);
        return false;
    }

    @Override
    public void onStart() {
        toolbtn_replay.setDisable(true);
    }

    @Override
    public void onStop() {
        toolbtn_replay.setDisable(false);
    }

    @Override
    public void onPause() {

    }
}
