package application.controller;

import application.EventData;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.input.InputDevice;
import com.android.ddmlib.input.TouchEventObserver;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;


public class MainController implements Initializable, AndroidDebugBridge.IDeviceChangeListener {
    @FXML
    MenuBar menubar_main;
    @FXML
    Menu menu_file, menu_edit, menu_help;
    @FXML
    MenuItem menuitem_close, menuitem_delete, menuitem_about;

    private TouchEventObserver mEventObserver;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        AndroidDebugBridge.initIfNeeded(false);
        AndroidDebugBridge.createBridge();
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
        List<InputDevice> lists= device.getInputDeviceManager().getDevice();
        for (InputDevice dev:lists){
            System.out.println(dev);
        }
    }
}
