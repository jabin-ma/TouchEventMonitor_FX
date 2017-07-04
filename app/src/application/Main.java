package application;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("main_layout.fxml"));
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
            primaryStage.setTitle("TouchEvent Monitor");
            primaryStage.setScene(scene);
//			primaryStage.setResizable(false);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {


        launch(args);

//        int state=0;
//        int state_1=0x00000002;
//        int state_2=0x00000020;
//        int state_3=0x00000200;
//
//        state|=state_1;
//        state|=state_2;
//        state|=state_3;
//        System.out.println(((state&state_1)==state_1));
//        System.out.println(((state&state_2)==state_2));
//        System.out.println(((state&state_3)==state_3));
//        state &=~state_3;
//        System.out.println(((state&state_1)==state_1));
//        System.out.println(((state&state_2)==state_2));
//        System.out.println(((state&state_3)==state_3));
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        System.exit(0);
    }
}
