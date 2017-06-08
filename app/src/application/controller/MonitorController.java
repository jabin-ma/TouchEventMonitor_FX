package application.controller;

import application.EventData;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.input.InputDevice;
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

public class MonitorController implements Initializable, AndroidDebugBridge.IDeviceChangeListener {
    @FXML
    ChoiceBox<InputDevice> choicebox_device;
    @FXML
    ToolBar toolbar_main;
    @FXML
    Button toolbtn_start,toolbtn_pause,toolbtn_stop,toolbtn_replay;
    @FXML
    Pane panel_table;
    @FXML
    TableView tableview_events;

    private TouchEventObserver mEventObserver;

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
        for (int i = 0; i < 1000; i++) {
            data.add(new EventData("type", "desc", "dur", i/1000.00));
        }
        tableview_events.setItems(data);
        choicebox_device.setTooltip(new Tooltip("Select..."));
        choicebox_device.setConverter(new StringConverter<InputDevice>() {
            @Override
            public String toString(InputDevice object) {
                return object.getDevFile();
            }

            @Override
            public InputDevice fromString(String string) {
                return null;
            }
        });


    }

    public void onMenuClick(ActionEvent ev){
        System.out.println(ev);
    }

    public void onToolBtnClick(ActionEvent ev){
        System.out.println(ev);
    }

    @Override
    public void deviceConnected(IDevice device) {

    }

    @Override
    public void deviceDisconnected(IDevice device) {

    }

    @Override
    public void deviceChanged(IDevice device, int changeMask) {
        choicebox_device.getItems().addAll(device.getInputDeviceManager().getDevice());
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                //更新JavaFX的主线程的代码放在此处
                choicebox_device.getSelectionModel().select(0);
            }
        });
    }
}
