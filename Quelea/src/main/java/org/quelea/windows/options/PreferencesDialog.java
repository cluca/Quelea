/*
 * This file is part of Quelea, free projection software for churches.
 *
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITYs or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.quelea.windows.options;

import com.dlsc.formsfx.model.structure.Field;
import com.dlsc.formsfx.model.structure.StringField;
import com.dlsc.preferencesfx.PreferencesFx;
import com.dlsc.preferencesfx.formsfx.view.controls.SimpleControl;
import com.dlsc.preferencesfx.model.Setting;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.quelea.services.languages.LabelGrabber;
import org.quelea.services.utils.QueleaProperties;
import org.quelea.services.utils.Utils;
import org.quelea.windows.main.DisplayStage;
import org.quelea.windows.main.QueleaApp;
import org.quelea.windows.options.customprefs.ColorPickerPreference;

import java.util.HashMap;

public class PreferencesDialog extends Stage {
    private PreferencesFx preferencesFx;
    private final BorderPane mainPane;
    private final Button okButton;
    private final OptionsGeneralPanel generalPanel;
    private final OptionsDisplaySetupPanel displayPanel;
    private final OptionsNoticePanel noticePanel;
    private final OptionsPresentationPanel presentationPanel;
    private final OptionsBiblePanel biblePanel;
    private final OptionsStageViewPanel stageViewPanel;
    private final OptionsServerSettingsPanel optionsServerSettingsPanel;
    private final OptionsRecordingPanel recordingPanel;
    private final OptionsImportExportPanel importExportPanel;
    private HashMap<Field, ObservableValue> bindings = new HashMap<>();
    private boolean previousLinkPreviewLiveDividers;

    /**
     * Create a new preference dialog.
     *
     * @author Arvid
     */
    public PreferencesDialog(Class parent, boolean hasVLC) {
        setTitle(LabelGrabber.INSTANCE.getLabel("options.title"));
        initModality(Modality.APPLICATION_MODAL);
        initOwner(QueleaApp.get().getMainWindow());
        getIcons().add(new Image("file:icons/options.png", 16, 16, false, true));
        mainPane = new BorderPane();

        generalPanel = new OptionsGeneralPanel(bindings);
        displayPanel = new OptionsDisplaySetupPanel(bindings);
        stageViewPanel = new OptionsStageViewPanel(bindings);
        noticePanel = new OptionsNoticePanel(bindings);
        presentationPanel = new OptionsPresentationPanel(bindings);
        biblePanel = new OptionsBiblePanel(bindings);
        optionsServerSettingsPanel = new OptionsServerSettingsPanel(bindings);
        recordingPanel = new OptionsRecordingPanel(bindings, hasVLC);
        importExportPanel = new OptionsImportExportPanel(bindings);

        preferencesFx =
                PreferencesFx.of(new PreferenceStorageHandler(parent),
                        generalPanel.getGeneralTab(),
                        displayPanel.getDisplaySetupTab(),
                        stageViewPanel.getStageViewTab(),
                        noticePanel.getNoticesTab(),
                        presentationPanel.getPresentationsTab(),
                        biblePanel.getBiblesTab(),
                        optionsServerSettingsPanel.getServerTab(),
                        recordingPanel.getRecordingsTab(),
                        importExportPanel.getImportExportTab()
                );

        okButton = new Button(LabelGrabber.INSTANCE.getLabel("ok.button"), new ImageView(new Image("file:icons/tick.png")));
        BorderPane.setMargin(okButton, new Insets(5));
        okButton.setOnAction((ActionEvent t) -> {
            preferencesFx.saveSettings();
            if (displayPanel.isDisplayChange()) {
                updatePos();
            }
            displayPanel.setDisplayChange(false);
            QueleaApp.get().getMainWindow().getMainPanel().getSchedulePanel().getThemeNode().refresh();

            // If the option has changed for linking the preview and live dividers, update the current UI to respect
            // the options now
            if( previousLinkPreviewLiveDividers != QueleaProperties.get().getLinkPreviewAndLiveDividers() ) {
                // Get both the preview and live split panes
                SplitPane previewSplit = QueleaApp.get().getMainWindow().getMainPanel().getPreviewPanel().getLyricsPanel().getSplitPane();
                SplitPane liveSplit = QueleaApp.get().getMainWindow().getMainPanel().getLivePanel().getLyricsPanel().getSplitPane();

                if( QueleaProperties.get().getLinkPreviewAndLiveDividers() ) {
                    // If the option is now to have them linked, need to move the preview divider to line up with the
                    // live one, before linking them together
                    previewSplit.setDividerPositions(liveSplit.getDividerPositions()[0]);
                    previewSplit.getDividers().get(0).positionProperty().
                            bindBidirectional(liveSplit.getDividers().get(0).positionProperty());
                } else {
                    // Option is now to have them unlinked, so do that
                    previewSplit.getDividers().get(0).positionProperty().
                            unbindBidirectional(liveSplit.getDividers().get(0).positionProperty());
                }
            }

            hide();
        });
        BorderPane.setAlignment(okButton, Pos.CENTER);

        mainPane.setBottom(okButton);
        mainPane.setMinWidth(1005);
        mainPane.setMinHeight(600);
        mainPane.setCenter(preferencesFx.getView().getCenter());

        Scene scene = new Scene(mainPane);
        if (QueleaProperties.get().getUseDarkTheme()) {
            scene.getStylesheets().add("org/modena_dark.css");
        }
        setScene(scene);

        getScene().getWindow().addEventFilter(WindowEvent.WINDOW_SHOWN, e -> callBeforeShowing());
        getScene().getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, e -> callBeforeHiding());

        bindings.forEach(this::bind);
    }

    private void callBeforeShowing() {
        getDisplaySetupPanel().setDisplayChange(false);

        // Before showing the preferences dialog we save the current state of the link preview and live dividers so
        // that we can tell if it has been changed and whether we need to action anything
        previousLinkPreviewLiveDividers = QueleaProperties.get().getLinkPreviewAndLiveDividers();
    }

    private void bind(Field field, ObservableValue<? extends Boolean> booleanProperty) {
        ((SimpleControl) field.getRenderer()).getNode().disableProperty().bind(booleanProperty);
    }

    public static Setting getColorPicker(String label, Color color) {
        StringProperty property = new SimpleStringProperty(QueleaProperties.get().getStr(color));
        StringField field = Field.ofStringType(property).render(
                new ColorPickerPreference(color));
        return Setting.of(label, field, property);
    }

    public static Setting getPositionSelector(String label, boolean horizontal, String selectedValue, BooleanProperty booleanBind, HashMap<Field, ObservableValue> bindings) {
        Setting setting;
        if (horizontal)
            setting = Setting.of(label, FXCollections.observableArrayList(
                    LabelGrabber.INSTANCE.getLabel("left"),
                    LabelGrabber.INSTANCE.getLabel("centre"),
                    LabelGrabber.INSTANCE.getLabel("right")),
                    new SimpleObjectProperty<>(LabelGrabber.INSTANCE.getLabel(selectedValue.toLowerCase())));
        else
            setting = Setting.of(label, FXCollections.observableArrayList(
                    LabelGrabber.INSTANCE.getLabel("top.text.position"),
                    LabelGrabber.INSTANCE.getLabel("bottom.text.position")),
                    new SimpleObjectProperty<>(LabelGrabber.INSTANCE.getLabel(selectedValue.toLowerCase())));
        if (booleanBind != null)
            bindings.put((Field) setting.getElement(), booleanBind.not());
        return setting;
    }

    public static Setting getShowOnSlidesSelector(String label, String selectedValue, BooleanProperty booleanBind, HashMap<Field, ObservableValue> bindings) {
        Setting setting = Setting.of(label, FXCollections.observableArrayList(
                LabelGrabber.INSTANCE.getLabel("all"),
                LabelGrabber.INSTANCE.getLabel("first"),
                LabelGrabber.INSTANCE.getLabel("last")),
                new SimpleObjectProperty<>(LabelGrabber.INSTANCE.getLabel(selectedValue.toLowerCase())));
        if (booleanBind != null)
            bindings.put((Field) setting.getElement(), booleanBind.not());
        return setting;
    }

    public void updatePos() {
        DisplayStage appWindow = QueleaApp.get().getProjectionWindow();
        DisplayStage stageWindow = QueleaApp.get().getStageWindow();
        if (appWindow == null) {
            appWindow = new DisplayStage(QueleaProperties.get().getProjectorCoords(), false);
        }
        final DisplayStage fiLyricWindow = appWindow; //Fudge for AIC
        final ObservableList<Screen> monitors = Screen.getScreens();
        Platform.runLater(() -> {
            int projectorScreen = QueleaProperties.get().getProjectorScreen();
            Bounds bounds;
            if (QueleaProperties.get().isProjectorModeCoords()) {
                bounds = QueleaProperties.get().getProjectorCoords();
            } else {
                bounds = Utils.getBoundsFromRect2D(
                        monitors.get(projectorScreen < 0 || projectorScreen >= monitors.size() ? 0 : projectorScreen)
                                .getBounds());
            }
            fiLyricWindow.setAreaImmediate(bounds);
            if (!QueleaApp.get().getMainWindow().getMainPanel().getLivePanel().getHide().isSelected()) {
                fiLyricWindow.show();
            }

            // non-custom positioned windows are fullscreen
            if (!QueleaProperties.get().isProjectorModeCoords()) {
                if (QueleaProperties.get().getProjectorScreen() == -1) {
                    fiLyricWindow.hide();
                } else {
                    fiLyricWindow.setFullScreenAlwaysOnTop(true);
                }
            } else {
                fiLyricWindow.setFullScreenAlwaysOnTop(false);
            }
        });
        if (stageWindow == null) {
            stageWindow = new DisplayStage(QueleaProperties.get().getStageCoords(), true);
        }
        final DisplayStage fiStageWindow = stageWindow; //Fudge for AIC
        Platform.runLater(() -> {
            int stageScreen = QueleaProperties.get().getStageScreen();
            Bounds bounds;
            if (QueleaProperties.get().isStageModeCoords()) {
                bounds = QueleaProperties.get().getStageCoords();
            } else {
                bounds = Utils.getBoundsFromRect2D(
                        monitors.get(stageScreen < 0 || stageScreen >= monitors.size() ? 0 : stageScreen)
                                .getBounds());
            }
            fiStageWindow.setAreaImmediate(bounds);

            if (!QueleaApp.get().getMainWindow().getMainPanel().getLivePanel().getHide().isSelected()) {
                fiStageWindow.show();
            }
            if (QueleaProperties.get().getStageScreen() == -1 && !QueleaProperties.get().isStageModeCoords())
                fiStageWindow.hide();
        });
    }

    private void callBeforeHiding() {
        preferencesFx.discardChanges();
    }

    public OptionsServerSettingsPanel getOptionsServerSettingsPanel() {
        return optionsServerSettingsPanel;
    }

    public OptionsDisplaySetupPanel getDisplaySetupPanel() {
        return displayPanel;
    }
}
