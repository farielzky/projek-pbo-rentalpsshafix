package main;

import view.RentalView;
import controller.RentalController;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            RentalView view = new RentalView();
            new RentalController(view);
            view.setLocationRelativeTo(null);
            view.setVisible(true);
        });
    }
}