package application.controller;

import java.net.URL;
import java.util.ResourceBundle;

import com.android.ddmlib.input.TouchEventObserver;

import application.EventData;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Pane;

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
	Button toolbtn_first;
	@FXML
	Pane panel_table;
	@FXML
	TableView<EventData> tableview_events;

	private TouchEventObserver mEventObserver;

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void initialize(URL url, ResourceBundle rb) {
		ObservableList<TableColumn<EventData,?>> observableList = tableview_events.getColumns();
		observableList.get(0).setCellValueFactory(new PropertyValueFactory("eventType"));
		observableList.get(1).setCellValueFactory(new PropertyValueFactory("eventDesc"));
		observableList.get(2).setCellValueFactory(new PropertyValueFactory("eventDur"));
		observableList.get(3).setCellValueFactory(new PropertyValueFactory("progress"));
		ObservableList<EventData> data = FXCollections.observableArrayList();
		data.add(new EventData("type", "desc", "dur", 10));
		tableview_events.setItems(data);
	}
	
	
	
	
	

	public void testButton(ActionEvent event) {

	}
}
