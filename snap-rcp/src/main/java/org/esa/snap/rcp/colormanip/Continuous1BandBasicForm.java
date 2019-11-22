/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.snap.rcp.colormanip;

import com.bc.ceres.swing.TableLayout;
import org.esa.snap.core.datamodel.ColorPaletteDef;
import org.esa.snap.core.datamodel.ImageInfo;
import org.esa.snap.core.datamodel.ProductNodeEvent;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.datamodel.Stx;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.util.PropertyMap;
import org.esa.snap.core.util.math.Range;
import org.esa.snap.rcp.SnapApp;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;

/**
 *
 * @author Brockmann Consult
 * @author Daniel Knowles (NASA)
 * @author Bing Yang (NASA)
 */
// OCT 2019 - Knowles / Yang
//          - Added DocumentListener to minField and maxField to make sure that the values are being updated.
//            Previously the values would only be updated if the user hit enter and a lose focus event would not
//            trigger a value update.
//          - Fixes log scaling bug where the log scaling was not affecting the palette values.  This was achieved
//            by tracking the source and target log scaling and passing this information to the method
//            setColorPaletteDef() in the class ImageInfo.
//          - Added numerical checks on the minField and maxField.



public class Continuous1BandBasicForm implements ColorManipulationChildForm {

    private final ColorManipulationForm parentForm;
    private final JPanel contentPanel;
    private final AbstractButton logDisplayButton;
    private final MoreOptionsForm moreOptionsForm;
    private final ColorPaletteChooser colorPaletteChooser;
    private final JFormattedTextField minField;
    private final JFormattedTextField maxField;
    private String currentMinFieldValue = "";
    private String currentMaxFieldValue = "";
    private final DiscreteCheckBox discreteCheckBox;
    private final JCheckBox loadWithCPDFileValuesCheckBox;
    private final ColorPaletteSchemes standardColorPaletteSchemes;
    private JLabel colorSchemeJLabel;



    final Boolean[] minFieldActivated = {new Boolean(false)};
    final Boolean[] maxFieldActivated = {new Boolean(false)};
    final Boolean[] listenToLogDisplayButtonEnabled = {true};
    final Boolean[] basicSwitcherIsActive;


    private enum RangeKey {FromPaletteSource, FromData, FromMinMaxFields, FromCurrentPalette, ToggleLog, InvertPalette, Dummy}
    private boolean shouldFireChooserEvent;
    private boolean hidden = false;

    Continuous1BandBasicForm(final ColorManipulationForm parentForm, final Boolean[] basicSwitcherIsActive) {
        ColorPaletteManager.getDefault().loadAvailableColorPalettes(parentForm.getIODir().toFile());

        this.parentForm = parentForm;
        this.basicSwitcherIsActive = basicSwitcherIsActive;


        PropertyMap configuration = null;
//        if (parentForm.getProductSceneView() != null && parentForm.getProductSceneView().getSceneImage() != null) {
//            configuration = parentForm.getProductSceneView().getSceneImage().getConfiguration();
//        }


        colorSchemeJLabel = new JLabel("");
        colorSchemeJLabel.setToolTipText("The color data is stored in the band.  Astericks suffix (*) denotes that some parameters have been altered");

        standardColorPaletteSchemes = new ColorPaletteSchemes(parentForm.getIODir().toFile(), ColorPaletteSchemes.Id.SELECTOR, true, configuration);


        final TableLayout layout = new TableLayout();
        layout.setTableWeightX(1.0);
        layout.setTableWeightY(1.0);
        layout.setTablePadding(2, 2);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setTableAnchor(TableLayout.Anchor.NORTH);
        layout.setCellPadding(0, 0, new Insets(8, 2, 2, 2));
        layout.setCellPadding(2, 0, new Insets(13, 2, 5, 2));

        loadWithCPDFileValuesCheckBox = new JCheckBox("Load cpd file exact values", false);
        loadWithCPDFileValuesCheckBox.setToolTipText("When loading a new cpd file, use it's actual value and overwrite user min/max values");


        final JPanel editorPanel = new JPanel(layout);

        JPanel colorPaletteInfoComboBoxJPanel = getSchemaPanel("Scheme");
        editorPanel.add(colorPaletteInfoComboBoxJPanel);


        editorPanel.add(new JLabel("Colour ramp:"));
        colorPaletteChooser = new ColorPaletteChooser();
        editorPanel.add(colorPaletteChooser);
        editorPanel.add(loadWithCPDFileValuesCheckBox);
        editorPanel.add(new JLabel("Display range"));




        minField = getNumberTextField(0.00001);
        maxField = getNumberTextField(1);

        final JPanel minPanel = new JPanel(new BorderLayout(5, 2));
        minPanel.add(new JLabel("Min:"), BorderLayout.WEST);
        minPanel.add(minField, BorderLayout.SOUTH);
        final JPanel maxPanel = new JPanel(new BorderLayout(5, 2));
        maxPanel.add(new JLabel("Max:"), BorderLayout.EAST);
        maxPanel.add(maxField, BorderLayout.SOUTH);

        final JPanel minMaxPanel = new JPanel(new BorderLayout(5, 5));
        minMaxPanel.add(minPanel, BorderLayout.WEST);
        minMaxPanel.add(maxPanel, BorderLayout.EAST);
        editorPanel.add(minMaxPanel);

        final JButton fromFile = new JButton("Range from File");
        final JButton fromData = new JButton("Range from Data");

        final JPanel buttonPanel = new JPanel(new BorderLayout(5, 10));
        buttonPanel.add(fromFile, BorderLayout.WEST);
        buttonPanel.add(fromData, BorderLayout.EAST);
        editorPanel.add(new JLabel(" "));
        editorPanel.add(buttonPanel);

        shouldFireChooserEvent = true;

        colorPaletteChooser.addActionListener(createListener(RangeKey.FromCurrentPalette));
//        minField.addActionListener(createListener(RangeKey.FromMinMaxFields));
//        maxField.addActionListener(createListener(RangeKey.FromMinMaxFields));
        maxField.getDocument().addDocumentListener(new DocumentListener() {
            @Override

            public void insertUpdate(DocumentEvent documentEvent) {
                handleMaxTextfield();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
            }
        });

        minField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                handleMinTextfield();
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
            }
        });



        fromFile.addActionListener(createListener(RangeKey.FromPaletteSource));
        fromData.addActionListener(createListener(RangeKey.FromData));

        contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(editorPanel, BorderLayout.NORTH);
        moreOptionsForm = new MoreOptionsForm(this, parentForm.getFormModel().canUseHistogramMatching());
        discreteCheckBox = new DiscreteCheckBox(parentForm);
        moreOptionsForm.addRow(discreteCheckBox);
        parentForm.getFormModel().modifyMoreOptionsForm(moreOptionsForm);

        logDisplayButton = LogDisplay.createButton();
        logDisplayButton.addActionListener(e -> {
//            final boolean shouldLog10Display = logDisplayButton.isSelected();
//            final ImageInfo imageInfo = parentForm.getFormModel().getModifiedImageInfo();
//            if (shouldLog10Display) {
//                final ColorPaletteDef cpd = imageInfo.getColorPaletteDef();
//                if (LogDisplay.checkApplicability(cpd)) {
//                    colorPaletteChooser.setLog10Display(true);
//                    imageInfo.setLogScaled(true);
//                    parentForm.applyChanges();
//                } else {
//                    LogDisplay.showNotApplicableInfo(parentForm.getContentPanel());
//                    logDisplayButton.setSelected(false);
//                }
//            } else {
//                colorPaletteChooser.setLog10Display(false);
//                imageInfo.setLogScaled(false);
//                parentForm.applyChanges();
//            }
            if (listenToLogDisplayButtonEnabled[0]) {
                listenToLogDisplayButtonEnabled[0] = false;
                logDisplayButton.setSelected(!logDisplayButton.isSelected());

                applyChanges(RangeKey.ToggleLog);
                listenToLogDisplayButtonEnabled[0] = true;
            }
        });


        standardColorPaletteSchemes.getjComboBox().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (standardColorPaletteSchemes.getjComboBox().getSelectedIndex() != 0) {
                    if (standardColorPaletteSchemes.isjComboBoxShouldFire()) {
                        standardColorPaletteSchemes.setjComboBoxShouldFire(false);

                        // todo DANNY commented out temporarily
                        handleColorPaletteInfoComboBoxSelection(standardColorPaletteSchemes.getjComboBox(), false);
                        standardColorPaletteSchemes.reset();
                        standardColorPaletteSchemes.setjComboBoxShouldFire(true);
                    }
                }
            }
        });
    }

    private void handleMaxTextfield() {

        if (!currentMaxFieldValue.equals(maxField.getText().toString())) {
            if (!maxFieldActivated[0] && !basicSwitcherIsActive[0]) {
                maxFieldActivated[0] = true;
                applyChanges(RangeKey.FromMinMaxFields);
                maxFieldActivated[0] = false;
            }
        }
    }

    private void handleMinTextfield() {

        if (!currentMinFieldValue.equals(minField.getText().toString())) {
            if (!minFieldActivated[0] && !basicSwitcherIsActive[0]) {
                minFieldActivated[0] = true;
                applyChanges(RangeKey.FromMinMaxFields);
                minFieldActivated[0] = false;
            }
        }
    }

    @Override
    public Component getContentPanel() {
        return contentPanel;
    }

    @Override
    public ColorManipulationForm getParentForm() {
        return parentForm;
    }

    @Override
    public void handleFormShown(ColorFormModel formModel) {
        hidden = false;
        updateFormModel(formModel);
    }

    @Override
    public void handleFormHidden(ColorFormModel formModel) {
        hidden = true;
    }

    @Override
    public void updateFormModel(ColorFormModel formModel) {
        if (!hidden) {
            ColorPaletteManager.getDefault().loadAvailableColorPalettes(parentForm.getIODir().toFile());
            colorPaletteChooser.reloadPalettes();
        }

        final ImageInfo imageInfo = formModel.getOriginalImageInfo();
        final ColorPaletteDef cpd = imageInfo.getColorPaletteDef();

        final boolean logScaled = cpd.isLogScaled();
        final boolean discrete = cpd.isDiscrete();

        colorPaletteChooser.setLog10Display(logScaled);
        colorPaletteChooser.setDiscreteDisplay(discrete);

        shouldFireChooserEvent = false;
        colorPaletteChooser.setSelectedColorPaletteDefinition(cpd);


        discreteCheckBox.setDiscreteColorsMode(discrete);
        logDisplayButton.setSelected(logScaled);

        parentForm.revalidateToolViewPaneControl();

        if (!minFieldActivated[0]) {
            minField.setValue(cpd.getMinDisplaySample());
            currentMinFieldValue = minField.getText().toString();
        }

        if (!maxFieldActivated[0]) {
            maxField.setValue(cpd.getMaxDisplaySample());
            currentMaxFieldValue = maxField.getText().toString();
        }
//        minField.setValue(cpd.getMinDisplaySample());
//        maxField.setValue(cpd.getMaxDisplaySample());


        boolean originalStandardShouldFire = standardColorPaletteSchemes.isjComboBoxShouldFire();

        standardColorPaletteSchemes.setjComboBoxShouldFire(false);

        standardColorPaletteSchemes.reset();

        // todo some palette sources code goes here

        standardColorPaletteSchemes.setjComboBoxShouldFire(originalStandardShouldFire);

        shouldFireChooserEvent = true;
    }

    @Override
    public void resetFormModel(ColorFormModel formModel) {
        updateFormModel(formModel);
        parentForm.revalidateToolViewPaneControl();
    }

    @Override
    public void handleRasterPropertyChange(ProductNodeEvent event, RasterDataNode raster) {
        if (event.getPropertyName().equals(RasterDataNode.PROPERTY_NAME_STX)) {
            updateFormModel(parentForm.getFormModel());
        }
    }

    @Override
    public RasterDataNode[] getRasters() {
        return parentForm.getFormModel().getRasters();
    }

    @Override
    public MoreOptionsForm getMoreOptionsForm() {
        return moreOptionsForm;
    }

    @Override
    public AbstractButton[] getToolButtons() {
        return new AbstractButton[]{
                    logDisplayButton,
        };
    }

    private ActionListener createListener(final RangeKey key) {
        return e -> applyChanges(key);
    }

    private JFormattedTextField getNumberTextField(double value) {
        final NumberFormatter formatter = new NumberFormatter(new DecimalFormat("0.0############"));
        formatter.setValueClass(Double.class); // to ensure that double values are returned
        final JFormattedTextField numberField = new JFormattedTextField(formatter);
        numberField.setValue(value);
        final Dimension preferredSize = numberField.getPreferredSize();
        preferredSize.width = 70;
        numberField.setPreferredSize(preferredSize);
        return numberField;
    }

    private void applyChanges(RangeKey key) {
        if (shouldFireChooserEvent) {
            boolean checksOut = true;

            final ColorPaletteDef selectedCPD = colorPaletteChooser.getSelectedColorPaletteDefinition();
            final ImageInfo currentInfo = parentForm.getFormModel().getModifiedImageInfo();
            final ColorPaletteDef currentCPD = currentInfo.getColorPaletteDef();
            final ColorPaletteDef deepCopy = selectedCPD.createDeepCopy();
            deepCopy.setDiscrete(currentCPD.isDiscrete());

            final double min;
            final double max;
            final boolean isSourceLogScaled;
            final boolean isTargetLogScaled;
            final ColorPaletteDef cpd;
            final boolean autoDistribute;

            switch (key) {
            case FromPaletteSource:
                Range rangeFromFile = colorPaletteChooser.getRangeFromFile();
                isSourceLogScaled = currentInfo.isLogScaled();
                isTargetLogScaled = currentInfo.isLogScaled();
                min = rangeFromFile.getMin();
                max = rangeFromFile.getMax();
                cpd = currentCPD;
                autoDistribute = true;
                break;
            case FromData:
                final Stx stx = parentForm.getStx(parentForm.getFormModel().getRaster());
                isSourceLogScaled = currentInfo.isLogScaled();
                isTargetLogScaled = currentInfo.isLogScaled();
                min = stx.getMinimum();
                max = stx.getMaximum();
                cpd = currentCPD;
                autoDistribute = true;
                break;
            case FromMinMaxFields:
                isSourceLogScaled = currentInfo.isLogScaled();
                isTargetLogScaled = currentInfo.isLogScaled();
//                parentForm.getImageInfo().getColorPaletteSourcesInfo().setAlteredScheme(true);


                if (ColorUtils.checkRangeCompatibility(minField.getText().toString(), maxField.getText().toString())) {
                    min = Double.parseDouble(minField.getText().toString());
                    max = Double.parseDouble(maxField.getText().toString());
                } else {
                    checksOut = false;
                    min = 0; //bogus unused values set just so it is initialized to make idea happy
                    max = 0; //bogus unused values set just so it is initialized to make idea happy
                }


//                min = (double) minField.getValue();
//                max = (double) maxField.getValue();
                cpd = currentCPD;
                autoDistribute = true;
                break;
            case ToggleLog:
                isSourceLogScaled = currentInfo.isLogScaled();
                isTargetLogScaled = !currentInfo.isLogScaled();
//                parentForm.getImageInfo().getColorPaletteSourcesInfo().setAlteredScheme(true);

                min = currentCPD.getMinDisplaySample();
                max = currentCPD.getMaxDisplaySample();
                cpd = currentCPD;

                autoDistribute = true;
                break;
            default:
//                isSourceLogScaled = selectedCPD.isLogScaled();
//                isTargetLogScaled = currentInfo.isLogScaled();
//                min = currentCPD.getMinDisplaySample();
//                max = currentCPD.getMaxDisplaySample();
//                cpd = deepCopy;
//                autoDistribute = true;
                if (loadWithCPDFileValuesCheckBox.isSelected()) {
                    isSourceLogScaled = selectedCPD.isLogScaled();
                    isTargetLogScaled = selectedCPD.isLogScaled();
                    autoDistribute = false;
                    currentInfo.setLogScaled(isTargetLogScaled);
                    rangeFromFile = colorPaletteChooser.getRangeFromFile();

                    min = rangeFromFile.getMin();
                    max = rangeFromFile.getMax();
//                        min = selectedCPD.getMinDisplaySample();
//                        max = selectedCPD.getMaxDisplaySample();
                    cpd = deepCopy;
                    deepCopy.setLogScaled(isTargetLogScaled);
                    deepCopy.setAutoDistribute(autoDistribute);


                    if (ColorUtils.checkRangeCompatibility(min, max, isTargetLogScaled)) {
                        listenToLogDisplayButtonEnabled[0] = false;
                        logDisplayButton.setSelected(isTargetLogScaled);
                        listenToLogDisplayButtonEnabled[0] = true;
                    }
                } else {
                    isSourceLogScaled = selectedCPD.isLogScaled();
                    isTargetLogScaled = currentInfo.isLogScaled();
                    min = currentCPD.getMinDisplaySample();
                    max = currentCPD.getMaxDisplaySample();
                    cpd = deepCopy;
                    autoDistribute = true;
                }

            }

            if (checksOut && ColorUtils.checkRangeCompatibility(min, max, isTargetLogScaled)) {
//                if (key == RangeKey.InvertPalette) {
//                    currentInfo.setColorPaletteDefInvert(cpd);
//                } else {
//                    currentInfo.setColorPaletteDef(cpd, min, max, autoDistribute, isSourceLogScaled, isTargetLogScaled);
//                }
                currentInfo.setColorPaletteDef(cpd, min, max, autoDistribute, isSourceLogScaled, isTargetLogScaled);

                if (key == RangeKey.ToggleLog) {
                    currentInfo.setLogScaled(isTargetLogScaled);
                    colorPaletteChooser.setLog10Display(isTargetLogScaled);
                }
                currentMinFieldValue = Double.toString(min);
                currentMaxFieldValue = Double.toString(max);
                parentForm.applyChanges();


            }
        }
    }

    private JPanel getSchemaPanel(String title) {


        JPanel jPanel = new JPanel(new GridBagLayout());
        jPanel.setBorder(BorderFactory.createTitledBorder(title));
        //   jPanel.setToolTipText("Load a preset color scheme (sets the color-palette, min, max, and log fields)");
        GridBagConstraints gbc = new GridBagConstraints();


        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 5, 0, 0);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        jPanel.add(colorSchemeJLabel, gbc);

        gbc.gridy++;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);

        jPanel.add(standardColorPaletteSchemes.getjComboBox(), gbc);

        return jPanel;
    }



    private void handleColorPaletteInfoComboBoxSelection(JComboBox jComboBox, boolean isDefaultList) {
//        ColorPaletteInfo colorPaletteInfo = (ColorPaletteInfo) jComboBox.getSelectedItem();
//
//        PropertyMap configuration = null;
////        if (parentForm.getProductSceneView() != null && parentForm.getProductSceneView().getSceneImage() != null) {
////            configuration = parentForm.getProductSceneView().getSceneImage().getConfiguration();
////        }
//        if (parentForm.getFormModel().getProductSceneView() != null && parentForm.getFormModel().getProductSceneView().getSceneImage() != null) {
//            configuration = parentForm.getFormModel().getProductSceneView().getSceneImage().getConfiguration();
//        }
//
//        boolean useColorBlindPalettes = ColorPaletteSchemes.getUseColorBlind(configuration);
//
//        if (colorPaletteInfo.getCpdFilename(useColorBlindPalettes) != null && colorPaletteInfo.isEnabled()) {
//
//
//            try {
//
//                File cpdFile = new File(parentForm.getIODir().toFile(), colorPaletteInfo.getCpdFilename(useColorBlindPalettes));
//                ColorPaletteDef colorPaletteDef = ColorPaletteDef.loadColorPaletteDef(cpdFile);
//
//
//                boolean origShouldFireChooserEvent = shouldFireChooserEvent;
//                shouldFireChooserEvent = false;
//
//                colorPaletteChooser.setSelectedColorPaletteDefinition(colorPaletteDef);
//
//
//
////                parentForm.getFormModel().getProductSceneView().getImageInfo().getColorPaletteSourcesInfo().setCpdFileName(colorPaletteInfo.getCpdFilename(useColorBlindPalettes));
//
//
////                parentForm.getFormModel().getProductSceneView().getImageInfo().getColorPaletteSourcesInfo().setColorBarLabels(colorPaletteInfo.getColorBarLabels());
////                parentForm.getFormModel().getProductSceneView().getImageInfo().getColorPaletteSourcesInfo().setColorBarTitle(colorPaletteInfo.getColorBarTitle());
////                parentForm.getFormModel().getProductSceneView().getImageInfo().getColorPaletteSourcesInfo().setColorBarMin(colorPaletteInfo.getMinValue());
////                parentForm.getFormModel().getProductSceneView().getImageInfo().getColorPaletteSourcesInfo().setColorBarMax(colorPaletteInfo.getMaxValue());
////                parentForm.getFormModel().getProductSceneView().getImageInfo().getColorPaletteSourcesInfo().setLogScaled(colorPaletteInfo.isLogScaled());
////
//
////                if (ImageLegend.allowColorbarAutoReset(configuration)) {
////                    parentForm.getFormModel().getProductSceneView().getImageInfo().getColorPaletteSourcesInfo().setColorBarInitialized(false);
////                    parentForm.getFormModel().getProductSceneView().getColorBarParamInfo().setParamsInitialized(false);
////                }
//
//
//                applyChanges(colorPaletteInfo.getMinValue(),
//                        colorPaletteInfo.getMaxValue(),
//                        colorPaletteDef,
//                        colorPaletteDef.isLogScaled(),
//                        colorPaletteInfo.isLogScaled(), colorPaletteInfo.getRootName(), isDefaultList);
//
//
//                shouldFireChooserEvent = origShouldFireChooserEvent;
//
//                String id = parentForm.getFormModel().getProductSceneView().getRaster().getDisplayName();
//                //   VisatApp.getApp().setStatusBarMessage("Loaded '" + colorPaletteInfo.getName() + "' color schema settings into '" + id);
//                String colorPaletteName = (colorPaletteInfo.getName() != null) ? colorPaletteInfo.getName() : "";
//                //     VisatApp.getApp().setStatusBarMessage("'" + colorPaletteName + "' color scheme loaded");
//
//
//            } catch (IOException e1) {
//                e1.printStackTrace();
//            }
//        }


    }

    private void applyChanges(double min,
                              double max,
                              ColorPaletteDef selectedCPD,
                              boolean isSourceLogScaled,
                              boolean isTargetLogScaled,
                              String colorSchemaName,
                              boolean isDefaultList) {


        final ImageInfo currentInfo = parentForm.getFormModel().getProductSceneView().getImageInfo();
        final ColorPaletteDef currentCPD = currentInfo.getColorPaletteDef();
        final ColorPaletteDef deepCopy = selectedCPD.createDeepCopy();
        deepCopy.setDiscrete(currentCPD.isDiscrete());
        deepCopy.setAutoDistribute(true);

        final boolean autoDistribute = true;
        currentInfo.setLogScaled(isTargetLogScaled);
        currentInfo.setColorPaletteDef(selectedCPD, min, max, autoDistribute, isSourceLogScaled, isTargetLogScaled);
//        currentInfo.getColorPaletteSourcesInfo().setSchemeName(colorSchemaName);
//        currentInfo.getColorPaletteSourcesInfo().setSchemeDefault(isDefaultList);


        currentMinFieldValue = Double.toString(min);
        currentMaxFieldValue = Double.toString(max);

        parentForm.applyChanges();
    }


}
