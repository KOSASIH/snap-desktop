package org.esa.snap.product.library.ui.v2.repository;

import org.esa.snap.product.library.ui.v2.ComponentDimension;
import org.esa.snap.product.library.ui.v2.MissionParameterListener;
import org.esa.snap.product.library.ui.v2.preferences.model.RemoteRepositoryCredentials;
import org.esa.snap.product.library.ui.v2.repository.local.AllLocalProductsRepositoryPanel;
import org.esa.snap.product.library.ui.v2.repository.local.LocalParameterValues;
import org.esa.snap.product.library.ui.v2.repository.local.LocalProductsPopupListeners;
import org.esa.snap.product.library.ui.v2.repository.output.OutputProductListPaginationPanel;
import org.esa.snap.product.library.ui.v2.repository.remote.DownloadProgressStatus;
import org.esa.snap.product.library.ui.v2.repository.remote.RemoteProductsRepositoryPanel;
import org.esa.snap.product.library.ui.v2.repository.remote.download.DownloadingProductProgressCallback;
import org.esa.snap.product.library.ui.v2.thread.ProgressBarHelperImpl;
import org.esa.snap.product.library.ui.v2.worldwind.WorldMapPanelWrapper;
import org.esa.snap.product.library.v2.database.SaveDownloadedProductData;
import org.esa.snap.product.library.v2.database.SaveProductData;
import org.esa.snap.remote.products.repository.RemoteProductsRepositoryProvider;
import org.esa.snap.remote.products.repository.RepositoryProduct;
import org.esa.snap.ui.loading.CustomComboBox;
import org.esa.snap.ui.loading.ItemRenderer;
import org.esa.snap.ui.loading.SwingUtils;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Created by jcoravu on 22/8/2019.
 */
public class RepositorySelectionPanel extends JPanel {

    private final JButton searchButton;
    private final JButton helpButton;
    private final JLabel repositoryLabel;
    private final ProgressBarHelperImpl progressBarHelper;

    private final ComponentDimension componentDimension;
    private final WorldMapPanelWrapper worldWindowPanel;

    private JComboBox<AbstractProductsRepositoryPanel> repositoriesComboBox;
    private JPanel topBarButtonsPanel;
    private ItemListener productsRepositoryListener;

    public RepositorySelectionPanel(RemoteProductsRepositoryProvider[] productsRepositoryProviders, ComponentDimension componentDimension,
                                    MissionParameterListener missionParameterListener, WorldMapPanelWrapper worldWindowPanel,
                                    int progressBarWidth) {

        super(new GridBagLayout());

        this.componentDimension = componentDimension;
        this.worldWindowPanel = worldWindowPanel;

        createRepositoriesComboBox(productsRepositoryProviders, missionParameterListener);

        int preferredHeight = this.componentDimension.getTextFieldPreferredHeight();

        Dimension buttonSize = new Dimension(preferredHeight, preferredHeight);

        this.searchButton = buildButton("/org/esa/snap/productlibrary/icons/search24.png", null, buttonSize, 1);
        this.searchButton.setToolTipText("Search");

        this.helpButton = buildButton("/org/esa/snap/resources/images/icons/Help24.gif", null, buttonSize, 1);
        this.helpButton.setToolTipText("Help");

        this.progressBarHelper = new ProgressBarHelperImpl(progressBarWidth, buttonSize.height) {
            @Override
            protected void setParametersEnabledWhileDownloading(boolean enabled) {
                RepositorySelectionPanel.this.setParametersEnabledWhileDownloading(enabled);
            }
        };

        this.repositoryLabel = new JLabel("Repository");
    }

    public void refreshUserAccounts(List<RemoteRepositoryCredentials> repositoriesCredentials) {
        int repositoryCount = this.repositoriesComboBox.getModel().getSize();
        for (int i=0; i<repositoriesCredentials.size(); i++) {
            RemoteRepositoryCredentials repositoryCredentials = repositoriesCredentials.get(i);
            for (int k=0; k<repositoryCount; k++) {
                AbstractProductsRepositoryPanel repositoryPanel = this.repositoriesComboBox.getModel().getElementAt(k);
                if (repositoryPanel instanceof RemoteProductsRepositoryPanel) {
                    RemoteProductsRepositoryPanel remoteRepositoryPanel = (RemoteProductsRepositoryPanel)repositoryPanel;
                    if (remoteRepositoryPanel.getProductsRepositoryProvider().getRepositoryName().equals(repositoryCredentials.getRepositoryName())) {
                        remoteRepositoryPanel.setUserAccounts(repositoryCredentials.getCredentialsList());
                    }
                }
            }
        }
    }

    public void setInputData(LocalParameterValues parameterValues) {
        if (parameterValues.getRepositoriesCredentials() != null) {
            refreshUserAccounts(parameterValues.getRepositoriesCredentials());
        }
        AllLocalProductsRepositoryPanel localProductsRepositoryPanel = getAllLocalProductsRepositoryPanel();
        localProductsRepositoryPanel.setLocalParameterValues(parameterValues.getLocalRepositoryParameterValues());
    }

    public ProgressBarHelperImpl getProgressBarHelper() {
        return progressBarHelper;
    }

    public void setSearchButtonListener(ActionListener searchButtonListener) {
        this.searchButton.addActionListener(searchButtonListener);
    }

    public void setHelpButtonListener(ActionListener helpButtonListener) {
        this.helpButton.addActionListener(helpButtonListener);
    }

    public void setStopButtonListener(ActionListener stopButtonListener) {
        this.progressBarHelper.getStopButton().addActionListener(stopButtonListener);
    }

    public AbstractProductsRepositoryPanel getSelectedProductsRepositoryPanel() {
        return (AbstractProductsRepositoryPanel)this.repositoriesComboBox.getSelectedItem();
    }

    public void setAllProductsRepositoryPanelBorder(Border border) {
        int count = this.repositoriesComboBox.getModel().getSize();
        for (int i=0; i<count; i++) {
            AbstractProductsRepositoryPanel repositoryPanel = this.repositoriesComboBox.getModel().getElementAt(i);
            repositoryPanel.setBorder(border);
        }
    }

    public void finishSavingProduct(SaveProductData saveProductData) {
        AllLocalProductsRepositoryPanel allLocalProductsRepositoryPanel = getAllLocalProductsRepositoryPanel();
        allLocalProductsRepositoryPanel.addLocalRepositoryFolderIfMissing(saveProductData.getLocalRepositoryFolder());
        if (saveProductData.getRemoteMission() != null) {
            allLocalProductsRepositoryPanel.addMissionIfMissing(saveProductData.getRemoteMission().getName());
        }
    }

    public void finishDownloadingProduct(RepositoryProduct repositoryProduct, DownloadProgressStatus downloadProgressStatus, SaveDownloadedProductData saveProductData) {
        if (downloadProgressStatus != null) {
            ComboBoxModel<AbstractProductsRepositoryPanel> model = this.repositoriesComboBox.getModel();
            for (int i=0; i<model.getSize(); i++) {
                AbstractProductsRepositoryPanel repositoryPanel = model.getElementAt(i);
                if (repositoryPanel instanceof RemoteProductsRepositoryPanel) {
                    if (repositoryPanel.getRepositoryName().equals(repositoryProduct.getRepositoryName())) {
                        ((RemoteProductsRepositoryPanel)repositoryPanel).addDownloadedProductProgress(repositoryProduct, downloadProgressStatus);
                    }
                } else if (repositoryPanel instanceof AllLocalProductsRepositoryPanel) {
                    if (saveProductData != null) {
                        AllLocalProductsRepositoryPanel allLocalProductsRepositoryPanel = (AllLocalProductsRepositoryPanel)repositoryPanel;
                        allLocalProductsRepositoryPanel.addLocalRepositoryFolderIfMissing(saveProductData.getLocalRepositoryFolder());
                        allLocalProductsRepositoryPanel.addMissionIfMissing(saveProductData.getRemoteMission().getName());
                        allLocalProductsRepositoryPanel.addAttributesIfMissing(saveProductData.getProductAttributeNames());
                    }
                }
            }
        }
    }

    public AllLocalProductsRepositoryPanel getAllLocalProductsRepositoryPanel() {
        int count = this.repositoriesComboBox.getModel().getSize();
        for (int i=0; i<count; i++) {
            AbstractProductsRepositoryPanel repositoryPanel = this.repositoriesComboBox.getModel().getElementAt(i);
            if (repositoryPanel instanceof AllLocalProductsRepositoryPanel) {
                return (AllLocalProductsRepositoryPanel)repositoryPanel;
            }
        }
        throw new IllegalStateException("The all local products repository does not exist.");
    }

    public Map<String, String> getRemoteMissionVisibleAttributes(String mission) {
        int count = this.repositoriesComboBox.getModel().getSize();
        for (int i=0; i<count; i++) {
            AbstractProductsRepositoryPanel repositoryPanel = this.repositoriesComboBox.getModel().getElementAt(i);
            if (repositoryPanel instanceof RemoteProductsRepositoryPanel) {
                RemoteProductsRepositoryPanel remoteProductsRepositoryPanel = (RemoteProductsRepositoryPanel)repositoryPanel;
                String[] availableMissions = remoteProductsRepositoryPanel.getProductsRepositoryProvider().getAvailableMissions();
                for (int k=0; k<availableMissions.length; k++) {
                    if (availableMissions[k].equalsIgnoreCase(mission)) {
                        return remoteProductsRepositoryPanel.getProductsRepositoryProvider().getDisplayedAttributes();
                    }
                }
            }
        }
        return null;
    }

    private void setParametersEnabledWhileDownloading(boolean enabled) {
        this.searchButton.setEnabled(enabled);
        this.repositoryLabel.setEnabled(enabled);
        this.repositoriesComboBox.setEnabled(enabled);
        if (this.topBarButtonsPanel != null) {
            for (int i=0; i<this.topBarButtonsPanel.getComponentCount(); i++) {
                this.topBarButtonsPanel.getComponent(i).setEnabled(enabled);
            }
        }
        AbstractProductsRepositoryPanel selectedDataSource = getSelectedProductsRepositoryPanel();
        Stack<JComponent> stack = new Stack<JComponent>();
        stack.push(selectedDataSource);
        while (!stack.isEmpty()) {
            JComponent component = stack.pop();
            component.setEnabled(enabled);
            int childrenCount = component.getComponentCount();
            for (int i=0; i<childrenCount; i++) {
                Component child = component.getComponent(i);
                if (child instanceof JComponent) {
                    JComponent childComponent = (JComponent) child;
                    // add the component in the stack to be enabled/disabled
                    stack.push(childComponent);
                }
            }
        }
    }

    private void refreshRepositoryLabelWidth() {
        int maximumLabelWidth = getSelectedProductsRepositoryPanel().computeLeftPanelMaximumLabelWidth();
        Dimension labelSize = this.repositoryLabel.getPreferredSize();
        labelSize.width = maximumLabelWidth;
        this.repositoryLabel.setPreferredSize(labelSize);
        this.repositoryLabel.setMinimumSize(labelSize);

        Container parentContainer = this.repositoryLabel.getParent();
        if (parentContainer != null) {
            parentContainer.revalidate();
            parentContainer.repaint();
        }
    }

    public void setRepositoriesItemListener(ItemListener productsRepositoryListener) {
        this.productsRepositoryListener = productsRepositoryListener;
    }

    private void createRepositoriesComboBox(RemoteProductsRepositoryProvider[] productsRepositoryProviders, MissionParameterListener missionParameterListener) {
        PropertyChangeListener parameterComponentsChangedListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                refreshRepositoryLabelWidth();
            }
        };
        ItemRenderer<AbstractProductsRepositoryPanel> itemRenderer = new ItemRenderer<AbstractProductsRepositoryPanel>() {
            @Override
            public String getItemDisplayText(AbstractProductsRepositoryPanel panel) {
                return (panel == null) ? "" : panel.getRepositoryName();
            }
        };
        this.repositoriesComboBox = new CustomComboBox(itemRenderer, this.componentDimension.getTextFieldPreferredHeight(), false, this.componentDimension.getTextFieldBackgroundColor());
        for (int i=0; i<productsRepositoryProviders.length; i++) {
            RemoteProductsRepositoryPanel remoteProductsRepositoryPanel = new RemoteProductsRepositoryPanel(productsRepositoryProviders[i], this.componentDimension, missionParameterListener, this.worldWindowPanel);
            remoteProductsRepositoryPanel.addInputParameterComponentsChangedListener(parameterComponentsChangedListener);
            this.repositoriesComboBox.addItem(remoteProductsRepositoryPanel);
        }
        AllLocalProductsRepositoryPanel allLocalProductsRepositoryPanel = new AllLocalProductsRepositoryPanel(this.componentDimension, this.worldWindowPanel);
        allLocalProductsRepositoryPanel.addInputParameterComponentsChangedListener(parameterComponentsChangedListener);
        this.repositoriesComboBox.addItem(allLocalProductsRepositoryPanel);
        this.repositoriesComboBox.setMaximumRowCount(7);
        this.repositoriesComboBox.setSelectedIndex(0);
        this.repositoriesComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                if (itemEvent.getStateChange() == ItemEvent.SELECTED) {
                    newSelectedRepository();
                    if (productsRepositoryListener != null) {
                        productsRepositoryListener.itemStateChanged(null);
                    }
                }
            }
        });
    }

    public void setLocalRepositoriesListeners(LocalProductsPopupListeners localProductsPopupListeners, ActionListener scanLocalRepositoryFoldersListener,
                                                     ActionListener addLocalRepositoryFolderListener, ActionListener deleteLocalRepositoryFolderListener) {

        AllLocalProductsRepositoryPanel allLocalProductsRepositoryPanel = getAllLocalProductsRepositoryPanel();
        allLocalProductsRepositoryPanel.setTopBarButtonListeners(scanLocalRepositoryFoldersListener, addLocalRepositoryFolderListener, deleteLocalRepositoryFolderListener);
        allLocalProductsRepositoryPanel.setLocalProductsPopupListeners(localProductsPopupListeners);
    }

    public void setDownloadRemoteProductListener(ActionListener downloadRemoteProductListener, ActionListener openDownloadedRemoteProductListener) {
        ComboBoxModel<AbstractProductsRepositoryPanel> model = this.repositoriesComboBox.getModel();
        for (int i=0; i<model.getSize(); i++) {
            AbstractProductsRepositoryPanel repositoryPanel = model.getElementAt(i);
            if (repositoryPanel instanceof RemoteProductsRepositoryPanel) {
                ((RemoteProductsRepositoryPanel)repositoryPanel).setDownloadProductListeners(downloadRemoteProductListener, openDownloadedRemoteProductListener);
            }
        }
    }

    public void setDownloadingProductProgressCallback(DownloadingProductProgressCallback downloadingProductProgressCallback) {
        ComboBoxModel<AbstractProductsRepositoryPanel> model = this.repositoriesComboBox.getModel();
        for (int i=0; i<model.getSize(); i++) {
            AbstractProductsRepositoryPanel repositoryPanel = model.getElementAt(i);
            if (repositoryPanel instanceof RemoteProductsRepositoryPanel) {
                ((RemoteProductsRepositoryPanel)repositoryPanel).setDownloadingProductProgressCallback(downloadingProductProgressCallback);
            }
        }
    }

    private void newSelectedRepository() {
        if (this.topBarButtonsPanel != null) {
            remove(this.topBarButtonsPanel);
            revalidate();
            repaint();
        }
        createTopBarButtonsPanel();
        if (this.topBarButtonsPanel != null) {
            GridBagConstraints c = SwingUtils.buildConstraints(3, 0, GridBagConstraints.VERTICAL, GridBagConstraints.WEST, 1, 1, 0, this.componentDimension.getGapBetweenColumns());
            add(this.topBarButtonsPanel, c);
            revalidate();
            repaint();
        }
    }

    private void createTopBarButtonsPanel() {
        JButton[] topBarButtons = getSelectedProductsRepositoryPanel().getTopBarButton();
        if (topBarButtons != null && topBarButtons.length > 0) {
            this.topBarButtonsPanel = new JPanel(new GridLayout(1, topBarButtons.length, this.componentDimension.getGapBetweenColumns(), 0));
            for (int i=0; i<topBarButtons.length; i++) {
                this.topBarButtonsPanel.add(topBarButtons[i]);
            }
        } else {
            this.topBarButtonsPanel = null;
        }
    }

    public void addComponents(OutputProductListPaginationPanel productListPaginationPanel) {
        createTopBarButtonsPanel();
        int gapBetweenColumns = this.componentDimension.getGapBetweenColumns();

        GridBagConstraints c = SwingUtils.buildConstraints(0, 0, GridBagConstraints.NONE, GridBagConstraints.WEST, 1, 1, 0, 0);
        add(this.repositoryLabel, c);
        c = SwingUtils.buildConstraints(1, 0, GridBagConstraints.BOTH, GridBagConstraints.WEST, 1, 1, 0, gapBetweenColumns);
        add(this.repositoriesComboBox, c);
        c = SwingUtils.buildConstraints(2, 0, GridBagConstraints.VERTICAL, GridBagConstraints.WEST, 1, 1, 0, gapBetweenColumns);
        add(this.searchButton, c);
        if (this.topBarButtonsPanel != null) {
            c = SwingUtils.buildConstraints(3, 0, GridBagConstraints.VERTICAL, GridBagConstraints.WEST, 1, 1, 0, gapBetweenColumns);
            add(this.topBarButtonsPanel, c);
        }

        c = SwingUtils.buildConstraints(4, 0, GridBagConstraints.VERTICAL, GridBagConstraints.WEST, 1, 1, 0, gapBetweenColumns);
        add(productListPaginationPanel, c);

        c = SwingUtils.buildConstraints(5, 0, GridBagConstraints.VERTICAL, GridBagConstraints.WEST, 1, 1, 0, gapBetweenColumns);
        add(this.helpButton, c);

        c = SwingUtils.buildConstraints(6, 0, GridBagConstraints.VERTICAL, GridBagConstraints.WEST, 1, 1, 0, gapBetweenColumns);
        add(this.progressBarHelper.getProgressBar(), c);

        c = SwingUtils.buildConstraints(7, 0, GridBagConstraints.VERTICAL, GridBagConstraints.WEST, 1, 1, 0, gapBetweenColumns);
        add(this.progressBarHelper.getStopButton(), c);
    }

    //TODO Jean move to the ui module
    public static ImageIcon loadImage(String resourceImagePath) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL imageURL = classLoader.getResource(resourceImagePath);
        if (imageURL == null) {
            throw new NullPointerException("The image '"+resourceImagePath+"' does not exist into the sources.");
        }
        return new ImageIcon(imageURL);
    }

    //TODO Jean move to the ui module
    public static ImageIcon loadImage(String resourceImagePath, Dimension buttonSize, Integer scaledImagePadding) {
        ImageIcon icon = loadImage(resourceImagePath);
        if (scaledImagePadding != null && scaledImagePadding.intValue() >= 0) {
            Image scaledImage = getScaledImage(icon.getImage(), buttonSize.width, buttonSize.height, scaledImagePadding.intValue());
            icon = new ImageIcon(scaledImage);
        }
        return icon;
    }

    //TODO Jean move to the ui module
    public static JButton buildButton(String resourceImagePath, ActionListener buttonListener, Dimension buttonSize, Integer scaledImagePadding) {
        ImageIcon icon = loadImage(resourceImagePath, buttonSize, scaledImagePadding);
        JButton button = new JButton(icon);
        button.setFocusable(false);
        button.addActionListener(buttonListener);
        button.setPreferredSize(buttonSize);
        button.setMinimumSize(buttonSize);
        button.setMaximumSize(buttonSize);
        return button;
    }

    private static Image getScaledImage(Image srcImg, int destinationImageWidth, int destinationImageHeight, int padding) {
        BufferedImage resizedImg = new BufferedImage(destinationImageWidth, destinationImageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resizedImg.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(srcImg, padding, padding, destinationImageWidth-padding, destinationImageHeight-padding, 0, 0, srcImg.getWidth(null), srcImg.getHeight(null), null);
        g2.dispose();
        return resizedImg;
    }
}
