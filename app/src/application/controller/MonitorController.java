package application.controller;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.controller.Type;
import com.android.ddmlib.input.InputDevice;
import com.android.ddmlib.input.MonitorEvent;
import com.android.ddmlib.input.OnTouchEventListener;
import com.android.ddmlib.input.TouchEvent;
import javafx.application.Platform;
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
    }

    public void doStartMonitor(ActionEvent ev) {
    }

    public void doPauseMonitor(ActionEvent ev) {


    }

    public void doStopMonitor(ActionEvent ev) {


    }

    public void doReplayMonitor(ActionEvent ev) {
//        curDev.getRemoteControler(Type.MONKEY).keyDown(KeyCode.BACK);
//        curDev.getRemoteControler(Type.MONKEY).sleep(100);
//        curDev.getRemoteControler(Type.MONKEY).keyUp(KeyCode.BACK);
//        for (Object o : tableview_events.getItems()) {
//            if (o instanceof TouchEvent) {
//                ((TouchEvent) o).processController(curDev.getRemoteControler(Type.MONKEY));
//                try {
//                    Thread.sleep(200);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }

        ObservableList olist=tableview_events.getItems();
        for (int i = 0; i < olist.size(); i++) {
             Object o=olist.get(i);
             if(o instanceof TouchEvent){
                 ((TouchEvent) o).processController(curDev.getRemoteControler(Type.MONKEY));
                 if(i+1<olist.size()){
                     Object oo=olist.get(i+1);
                     if(oo instanceof TouchEvent){
                         try {
                             Thread.sleep(((TouchEvent) oo).beginTime()-((TouchEvent) o).endTime());
                         } catch (InterruptedException e) {
                             e.printStackTrace();
                         }
                     }
                 }
             }
        }

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
