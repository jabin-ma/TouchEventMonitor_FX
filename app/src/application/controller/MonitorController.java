package application.controller;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MonkeyTransport;
import com.android.ddmlib.input.EventData;
import com.android.ddmlib.input.android.InputDevice;
import com.android.ddmlib.input.android.MonitorEvent;
import com.android.ddmlib.input.android.OnTouchEventListener;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.ResourceBundle;

public class MonitorController implements Initializable, AndroidDebugBridge.IDeviceChangeListener, OnTouchEventListener {
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
        AndroidDebugBridge.addDeviceChangeListener(this);
        ObservableList<TableColumn> observableList = tableview_events.getColumns();
        observableList.get(0).setCellValueFactory(new PropertyValueFactory("inputDevice"));
        observableList.get(1).setCellValueFactory(new PropertyValueFactory("eventType"));
        observableList.get(2).setCellValueFactory(new PropertyValueFactory("eventDesc"));
        observableList.get(3).setCellValueFactory(new PropertyValueFactory("eventDur"));
        ObservableList<EventData> data = FXCollections.observableArrayList();
        tableview_events.setItems(data);
    }

    public void doStartMonitor(ActionEvent ev) {
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
        setDisable(true);
    }


    public void setDisable(boolean disable) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                root.disableProperty().setValue(disable);
                if (disable) MainController.Companion.getCurrent().setStatus("请连接你的手机");
            }
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
            MonkeyTransport monkeyTransport=new MonkeyTransport(1080,device);
            monkeyTransport.createConnect();
        } else {
            curDev = null;
        }
    }

    @Override
    public boolean onTouchEvent(MonitorEvent monitorEvent) {
        tableview_events.getItems().add(monitorEvent);
        return false;
    }
}
