package application.controller;

import application.EventData;
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
import java.util.ResourceBundle;

public class EventController implements Initializable {
    @FXML
    MenuBar menubar_main;
    @FXML
    Menu menu_file, menu_edit, menu_help;
    @FXML
    MenuItem menuitem_close, menuitem_delete, menuitem_about;

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
    }

    public void onMenuClick(ActionEvent ev){
        System.out.println(ev);
    }

    public void onToolBtnClick(ActionEvent ev){
        System.out.println(ev);
    }
}
