package application.controller

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.input.TouchEventObserver
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.*
import javafx.scene.layout.HBox

import java.net.URL
import java.util.ResourceBundle


class MainController : Initializable {
    init {
        //        new Throwable().printStackTrace();
        current = this
    }

    @FXML
    internal var menubar_main: MenuBar? = null
    @FXML
    internal var menu_file: Menu? = null
    @FXML
    internal var menu_edit: Menu? = null
    @FXML
    internal var menu_help: Menu? = null
    @FXML
    internal var menuitem_close: MenuItem? = null
    @FXML
    internal var menuitem_delete: MenuItem? = null
    @FXML
    internal var menuitem_about: MenuItem? = null
    @FXML
    internal var pane_statusbar: HBox? = null
    @FXML
    internal var statusbar_text: Label? = null
    private val mEventObserver: TouchEventObserver? = null

    override fun initialize(url: URL?, rb: ResourceBundle?) {
        AndroidDebugBridge.initIfNeeded(false)
        AndroidDebugBridge.createBridge()
    }

    fun onMenuClick(ev: ActionEvent) {
        println(ev)
    }

    fun setStatus(text: String) {
        statusbar_text!!.text = text
    }

    companion object {
        var current: MainController? = null
            internal set
    }

}
