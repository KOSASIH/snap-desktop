/*
 * Copyright (C) 2016 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.snap.product.library.ui.v2;

import org.apache.http.auth.Credentials;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.util.PropertyMap;
import org.esa.snap.engine_utilities.util.Pair;
import org.esa.snap.graphbuilder.rcp.dialogs.BatchGraphDialog;
import org.esa.snap.product.library.ui.v2.preferences.RepositoriesCredentialsController;
import org.esa.snap.product.library.ui.v2.preferences.RepositoriesCredentialsControllerUI;
import org.esa.snap.product.library.ui.v2.repository.AbstractProductsRepositoryPanel;
import org.esa.snap.product.library.ui.v2.repository.RepositorySelectionPanel;
import org.esa.snap.product.library.ui.v2.repository.local.*;
import org.esa.snap.product.library.ui.v2.repository.output.OutputProductListModel;
import org.esa.snap.product.library.ui.v2.repository.output.OutputProductListPanel;
import org.esa.snap.product.library.ui.v2.repository.output.RepositoryOutputProductListPanel;
import org.esa.snap.product.library.ui.v2.repository.remote.DownloadProgressStatus;
import org.esa.snap.product.library.ui.v2.repository.remote.OpenDownloadedProductsRunnable;
import org.esa.snap.product.library.ui.v2.repository.remote.RemoteProductsRepositoryPanel;
import org.esa.snap.product.library.ui.v2.repository.remote.RemoteRepositoriesSemaphore;
import org.esa.snap.product.library.ui.v2.repository.remote.download.DownloadProductListTimerRunnable;
import org.esa.snap.product.library.ui.v2.repository.remote.download.DownloadProductListener;
import org.esa.snap.product.library.ui.v2.repository.remote.download.DownloadProductRunnable;
import org.esa.snap.product.library.ui.v2.repository.remote.download.DownloadRemoteProductsHelper;
import org.esa.snap.product.library.ui.v2.repository.remote.download.popup.DownloadingProductsPopupMenu;
import org.esa.snap.product.library.ui.v2.thread.AbstractProgressTimerRunnable;
import org.esa.snap.product.library.ui.v2.thread.ProgressBarHelperImpl;
import org.esa.snap.product.library.ui.v2.thread.ThreadListener;
import org.esa.snap.product.library.ui.v2.worldwind.PolygonMouseListener;
import org.esa.snap.product.library.ui.v2.worldwind.WorldMapPanelWrapper;
import org.esa.snap.product.library.v2.database.AllLocalFolderProductsRepository;
import org.esa.snap.product.library.v2.database.SaveDownloadedProductData;
import org.esa.snap.product.library.v2.database.SaveProductData;
import org.esa.snap.product.library.v2.database.model.LocalRepositoryFolder;
import org.esa.snap.product.library.v2.database.model.LocalRepositoryProduct;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.windows.ToolTopComponent;
import org.esa.snap.remote.products.repository.AbstractGeometry2D;
import org.esa.snap.remote.products.repository.RemoteProductsRepositoryProvider;
import org.esa.snap.remote.products.repository.RepositoryProduct;
import org.esa.snap.ui.AppContext;
import org.esa.snap.ui.loading.CustomFileChooser;
import org.esa.snap.ui.loading.CustomSplitPane;
import org.esa.snap.ui.loading.SwingUtils;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@TopComponent.Description(
        preferredID = "ProductLibraryTopComponentV2",
        iconBase = "org/esa/snap/productlibrary/icons/search.png",
        persistenceType = TopComponent.PERSISTENCE_ALWAYS
)
@TopComponent.Registration(
        mode = "rightSlidingSide",
        openAtStartup = true,
        position = 0
)
@ActionID(category = "Window", id = "org.esa.snap.product.library.ui.v2.ProductLibraryToolViewV2")
@ActionReferences({
        @ActionReference(path = "Menu/View/Tool Windows"),
        @ActionReference(path = "Menu/File", position = 17)
})
@TopComponent.OpenActionRegistration(
        displayName = "#CTL_ProductLibraryTopComponentV2Name",
        preferredID = "ProductLibraryTopComponentV2"
)
@NbBundle.Messages({
        "CTL_ProductLibraryTopComponentV2Name=Product Library v2",
        "CTL_ProductLibraryTopComponentV2Description=Product Library v2",
})
public class ProductLibraryToolViewV2 extends ToolTopComponent implements ComponentDimension, DownloadProductListener {

    private static final Logger logger = Logger.getLogger(ProductLibraryToolViewV2.class.getName());

    private static final String HELP_ID = "productLibraryTool";

    private static final String PREFERENCES_KEY_LAST_LOCAL_REPOSITORY_FOLDER_PATH = "last_local_repository_folder_path";

    private Path lastLocalRepositoryFolderPath;
    private RepositoryOutputProductListPanel repositoryProductListPanel;
    private RepositorySelectionPanel repositorySelectionPanel;
    private CustomSplitPane horizontalSplitPane;
    private DownloadingProductsPopupMenu downloadingProductsPopupMenu;

    private AbstractProgressTimerRunnable<?> searchProductListThread;
    private AbstractProgressTimerRunnable<?> localRepositoryProductsThread;
    private DownloadRemoteProductsHelper downloadRemoteProductsHelper;
    private int textFieldPreferredHeight;
    private WorldMapPanelWrapper worldWindowPanel;
    private boolean inputDataLoaded;
    private AppContext appContext;

    public ProductLibraryToolViewV2() {
        super();

        setDisplayName(Bundle.CTL_ProductLibraryTopComponentV2Name());
        this.inputDataLoaded = false;
    }

    @Override
    protected void componentOpened() {
        if (this.downloadRemoteProductsHelper == null) {
            initialize();
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();

        if (!this.inputDataLoaded) {
            this.inputDataLoaded = true;
            AllLocalFolderProductsRepository allLocalFolderProductsRepository = this.repositorySelectionPanel.getAllLocalProductsRepositoryPanel().getAllLocalFolderProductsRepository();
            LoadInputDataRunnable thread = new LoadInputDataRunnable(allLocalFolderProductsRepository) {
                @Override
                protected void onSuccessfullyExecuting(LocalParameterValues parameterValues) {
                    onFinishLoadingInputData(parameterValues);
                }
            };
            thread.executeAsync();
        }
    }

    @Override
    public int getGapBetweenRows() {
        return 5;
    }

    @Override
    public int getGapBetweenColumns() {
        return 5;
    }

    @Override
    public int getTextFieldPreferredHeight() {
        return this.textFieldPreferredHeight;
    }

    @Override
    public Color getTextFieldBackgroundColor() {
        return Color.WHITE;
    }

    @Override
    public void onFinishDownloadingProduct(DownloadProductRunnable downloadProductRunnable, DownloadProgressStatus downloadProgressStatus, SaveDownloadedProductData saveProductData, boolean hasProductsToDownload) {
        finishDownloadingProduct(downloadProductRunnable, downloadProgressStatus, saveProductData);
    }

    @Override
    public void onCancelDownloadingProduct(DownloadProductRunnable downloadProductRunnable, DownloadProgressStatus downloadProgressStatus) {
        finishDownloadingProduct(downloadProductRunnable, downloadProgressStatus, null);
    }

    @Override
    public void onUpdateProductDownloadProgress(RepositoryProduct repositoryProduct) {
        if (this.downloadingProductsPopupMenu != null) {
            this.downloadingProductsPopupMenu.onUpdateProductDownloadProgress(repositoryProduct);
        }
        OutputProductListModel productListModel = this.repositoryProductListPanel.getProductListPanel().getProductListModel();
        productListModel.refreshProductDownloadPercent(repositoryProduct);
    }

    private void finishDownloadingProduct(DownloadProductRunnable downloadProductRunnable, DownloadProgressStatus downloadProgressStatus, SaveDownloadedProductData saveProductData) {
        if (this.downloadingProductsPopupMenu != null) {
            this.downloadingProductsPopupMenu.onStopDownloadingProduct(downloadProductRunnable);
        }
        RepositoryProduct repositoryProduct = downloadProductRunnable.getProductToDownload();
        this.repositorySelectionPanel.finishDownloadingProduct(repositoryProduct, downloadProgressStatus, saveProductData);
        OutputProductListModel productListModel = this.repositoryProductListPanel.getProductListPanel().getProductListModel();
        productListModel.refreshProductDownloadPercent(repositoryProduct);
    }

    private void onFinishLoadingInputData(LocalParameterValues parameterValues) {
        this.repositorySelectionPanel.setInputData(parameterValues);
        this.repositoryProductListPanel.setVisibleProductsPerPage(parameterValues.getVisibleProductsPerPage());
        this.downloadRemoteProductsHelper.setUncompressedDownloadedProducts(parameterValues.isUncompressedDownloadedProducts());
    }

    private void refreshUserAccounts() {
        RepositoriesCredentialsController controller = RepositoriesCredentialsController.getInstance();
        this.repositorySelectionPanel.refreshUserAccounts(controller.getRepositoriesCredentials());
        this.repositoryProductListPanel.setVisibleProductsPerPage(controller.getNrRecordsOnPage());
        this.downloadRemoteProductsHelper.setUncompressedDownloadedProducts(controller.isAutoUncompress());
    }

    private void initialize() {
        this.appContext = SnapApp.getDefault().getAppContext();
        PropertyMap persistencePreferences = this.appContext.getPreferences();

        String lastFolderPath = persistencePreferences.getPropertyString(PREFERENCES_KEY_LAST_LOCAL_REPOSITORY_FOLDER_PATH, null);
        if (lastFolderPath != null) {
            this.lastLocalRepositoryFolderPath = Paths.get(lastFolderPath);
        }

        Insets defaultTextFieldMargins = new Insets(3, 2, 3, 2);
        JTextField productNameTextField = new JTextField();
        productNameTextField.setMargin(defaultTextFieldMargins);
        this.textFieldPreferredHeight = productNameTextField.getPreferredSize().height;

        RemoteProductsRepositoryProvider[] remoteRepositoryProductProviders = RemoteProductsRepositoryProvider.getRemoteProductsRepositoryProviders();

        createWorldWindowPanel(persistencePreferences);
        createRepositorySelectionPanel(remoteRepositoryProductProviders);
        createProductListPanel();

        this.repositorySelectionPanel.addComponents(this.repositoryProductListPanel.getProductListPaginationPanel());
        this.repositoryProductListPanel.addPageProductsChangedListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                outputProductsPageChanged();
            }
        });

        int gapBetweenRows = getGapBetweenRows();
        int gapBetweenColumns = getGapBetweenRows();
        int visibleDividerSize = gapBetweenColumns - 2;
        int dividerMargins = 0;
        float initialDividerLocationPercent = 0.5f;
        this.horizontalSplitPane = new CustomSplitPane(JSplitPane.HORIZONTAL_SPLIT, visibleDividerSize, dividerMargins, initialDividerLocationPercent, SwingUtils.TRANSPARENT_COLOR);
        this.horizontalSplitPane.setLeftComponent(this.repositorySelectionPanel.getSelectedProductsRepositoryPanel());
        this.horizontalSplitPane.setRightComponent(this.repositoryProductListPanel);

        setLayout(new BorderLayout(0, gapBetweenRows));
        setBorder(new EmptyBorder(gapBetweenRows, gapBetweenColumns, gapBetweenRows, gapBetweenColumns));
        add(this.repositorySelectionPanel, BorderLayout.NORTH);
        add(this.horizontalSplitPane, BorderLayout.CENTER);

        this.repositorySelectionPanel.getSelectedProductsRepositoryPanel().addInputParameterComponents();

        RemoteRepositoriesSemaphore remoteRepositoriesSemaphore = new RemoteRepositoriesSemaphore(remoteRepositoryProductProviders);
        ProgressBarHelperImpl progressBarHelper = this.repositoryProductListPanel.getProgressBarHelper();
        this.downloadRemoteProductsHelper = new DownloadRemoteProductsHelper(progressBarHelper, remoteRepositoriesSemaphore, this);

        this.repositorySelectionPanel.setDownloadingProductProgressCallback(this.downloadRemoteProductsHelper);
        this.repositoryProductListPanel.getProductListPanel().getProductListModel().setDownloadingProductProgressCallback(this.downloadRemoteProductsHelper);

        this.appContext.getApplicationWindow().addPropertyChangeListener(RepositoriesCredentialsControllerUI.REMOTE_PRODUCTS_REPOSITORY_CREDENTIALS, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        refreshUserAccounts();
                    }
                });
            }
        });

        progressBarHelper.addVisiblePropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent event) {
                Boolean visibleState = (Boolean)event.getNewValue();
                if (!visibleState.booleanValue()) {
                    onHideDownloadingProgressBar();
                }
            }
        });
        progressBarHelper.getProgressBar().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent mouseEvent) {
                showDownloadingProductsPopup((JComponent)mouseEvent.getSource());
            }
        });
    }

    private void onHideDownloadingProgressBar() {
        if (this.downloadingProductsPopupMenu != null) {
            this.downloadingProductsPopupMenu.setVisible(false);
            this.downloadingProductsPopupMenu = null;
        }
    }

    private void showDownloadingProductsPopup(JComponent invoker) {
        List<Pair<DownloadProductRunnable, DownloadProgressStatus>> downloadingProductRunnables = this.downloadRemoteProductsHelper.findDownloadingProducts();
        if (downloadingProductRunnables.size() > 0) {
            Color backgroundColor = getTextFieldBackgroundColor();
            int gapBetweenRows = getGapBetweenRows() / 2;
            int gapBetweenColumns = getGapBetweenColumns() / 2;
            DownloadingProductsPopupMenu popupMenu = new DownloadingProductsPopupMenu(downloadingProductRunnables, gapBetweenRows, gapBetweenColumns, backgroundColor);
            popupMenu.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent popupMenuEvent) {
                    downloadingProductsPopupMenu = (DownloadingProductsPopupMenu)popupMenuEvent.getSource();
                    downloadingProductsPopupMenu.refresh(); // refresh the texts in the panels after registering the listener
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent popupMenuEvent) {
                    ProductLibraryToolViewV2.this.downloadingProductsPopupMenu = null; // reset the listener
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent popupMenuEvent) {
                    // do nothing
                }
            });
            int x = invoker.getWidth() - popupMenu.getPreferredSize().width;
            int y = invoker.getHeight();
            popupMenu.show(invoker, x, y);
        }
    }

    private void createProductListPanel() {
        ActionListener stopDownloadingProductsButtonListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelDownloadingProducts();
            }
        };
        String text = DownloadRemoteProductsHelper.buildProgressBarDownloadingText(100, 100);
        JLabel label = new JLabel(text);
        int progressBarWidth = (int)(1.1f * label.getPreferredSize().width);
        this.repositoryProductListPanel = new RepositoryOutputProductListPanel(this.repositorySelectionPanel, this, stopDownloadingProductsButtonListener, progressBarWidth, false);
        this.repositoryProductListPanel.setBorder(new EmptyBorder(0, 1, 0, 0));
        this.repositoryProductListPanel.getProductListPanel().addDataChangedListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                productListChanged();
            }
        });
        this.repositoryProductListPanel.getProductListPanel().addSelectionChangedListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                newSelectedRepositoryProducts();
            }
        });
        addListeners();
    }

    private void createRepositorySelectionPanel(RemoteProductsRepositoryProvider[] remoteRepositoryProductProviders) {
        ItemListener productsRepositoryListener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent itemEvent) {
                onSelectedNewProductsRepository();
            }
        };
        MissionParameterListener missionParameterListener = new MissionParameterListener() {
            @Override
            public void newSelectedMission(String mission, AbstractProductsRepositoryPanel parentProductsRepositoryPanel) {
                if (parentProductsRepositoryPanel == repositorySelectionPanel.getSelectedProductsRepositoryPanel()) {
                    onSelectedNewProductsRepositoryMission();
                } else {
                    throw new IllegalStateException("The selected mission '"+mission+"' does not belong to the visible products repository.");
                }
            }
        };
        ActionListener searchButtonListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchButtonPressed();
            }
        };
        ActionListener stopDownloadingProductListButtonListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelSearchingProductList();
            }
        };
        ActionListener helpButtonListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                HelpCtx helpCtx = new HelpCtx(HELP_ID);
                helpCtx.display();
            }
        };

        String text = DownloadProductListTimerRunnable.buildProgressBarDownloadingText(1000, 1000);
        JLabel label = new JLabel(text);
        int progressBarWidth = (int)(1.1f * label.getPreferredSize().width);

        this.repositorySelectionPanel = new RepositorySelectionPanel(remoteRepositoryProductProviders, this, missionParameterListener, this.worldWindowPanel, progressBarWidth);
        this.repositorySelectionPanel.setRepositoriesItemListener(productsRepositoryListener);
        this.repositorySelectionPanel.setSearchButtonListener(searchButtonListener);
        this.repositorySelectionPanel.setHelpButtonListener(helpButtonListener);
        this.repositorySelectionPanel.setStopButtonListener(stopDownloadingProductListButtonListener);
        this.repositorySelectionPanel.setAllProductsRepositoryPanelBorder(new EmptyBorder(0, 0, 0, 1));
    }

    private void createWorldWindowPanel(PropertyMap persistencePreferences) {
        PolygonMouseListener worldWindowMouseListener = new PolygonMouseListener() {
            @Override
            public void leftMouseButtonClicked(List<Path2D.Double> polygonPaths) {
                ProductLibraryToolViewV2.this.leftMouseButtonClicked(polygonPaths);
            }
        };
        this.worldWindowPanel = new WorldMapPanelWrapper(worldWindowMouseListener, getTextFieldBackgroundColor(), persistencePreferences);
        this.worldWindowPanel.setPreferredSize(new Dimension(400, 250));
        this.worldWindowPanel.addWorldMapPanelAsync(false, true);
    }

    private void addListeners() {
        ActionListener openLocalProductListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                openLocalSelectedProducts();
            }
        };
        ActionListener deleteLocalProductListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                deleteLocalSelectedProducts();
            }
        };
        ActionListener batchProcessingListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                openBatchProcessingDialog();
            }
        };
        ActionListener showInExplorerListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                showSelectedLocalProductInExplorer();
            }
        };
        LocalProductsPopupListeners localProductsPopupListeners = new LocalProductsPopupListeners(openLocalProductListener, deleteLocalProductListener, batchProcessingListener, showInExplorerListener);

        ActionListener scanLocalRepositoryFoldersListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                scanAllLocalRepositoriesButtonPressed();
            }
        };
        ActionListener addLocalRepositoryFolderListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                addLocalRepositoryButtonPressed();
            }
        };
        ActionListener deleteLocalRepositoryFolderListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                deleteAllLocalRepositoriesButtonPressed();
            }
        };
        this.repositorySelectionPanel.setLocalRepositoriesListeners(localProductsPopupListeners, scanLocalRepositoryFoldersListener,
                                                                           addLocalRepositoryFolderListener, deleteLocalRepositoryFolderListener);

        ActionListener downloadRemoteProductListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                downloadSelectedRemoteProductsButtonPressed();
            }
        };
        ActionListener openDownloadedRemoteProductListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openDownloadedRemoteProductsButtonPressed();
            }
        };
        this.repositorySelectionPanel.setDownloadRemoteProductListener(downloadRemoteProductListener, openDownloadedRemoteProductListener);
    }

    private void scanAllLocalRepositoriesButtonPressed() {
        if (this.downloadRemoteProductsHelper.isRunning() || this.localRepositoryProductsThread != null) {
            StringBuilder message = new StringBuilder();
            message.append("The local repository folders cannot be refreshed.")
                    .append("\n\n")
                    .append("There is a running action.");
            JOptionPane.showMessageDialog(ProductLibraryToolViewV2.this, message.toString(), "Scan all local repositories", JOptionPane.ERROR_MESSAGE);
        } else {
            ProgressBarHelperImpl progressBarHelper = this.repositoryProductListPanel.getProgressBarHelper();
            int threadId = progressBarHelper.incrementAndGetCurrentThreadId();
            AllLocalFolderProductsRepository allLocalFolderProductsRepository = this.repositorySelectionPanel.getAllLocalProductsRepositoryPanel().getAllLocalFolderProductsRepository();
            this.localRepositoryProductsThread = new ScanAllLocalRepositoryFoldersTimerRunnable(progressBarHelper, threadId, allLocalFolderProductsRepository) {
                @Override
                protected void onFinishRunning() {
                    ProductLibraryToolViewV2.this.localRepositoryProductsThread = null; // reset
                    searchProductListLater();
                }

                @Override
                protected void onLocalRepositoryFolderDeleted(LocalRepositoryFolder localRepositoryFolder) {
                    ProductLibraryToolViewV2.this.deleteLocalRepositoryFolder(localRepositoryFolder);
                }
            };
            this.localRepositoryProductsThread.executeAsync(); // start the thread
        }
    }

    private void searchProductListLater() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                searchButtonPressed();
            }
        });
    }

    private void addLocalRepositoryButtonPressed() {
        if (this.downloadRemoteProductsHelper.isRunning() || this.localRepositoryProductsThread != null) {
            StringBuilder message = new StringBuilder();
            message.append("A local repository folder cannot be added.")
                    .append("\n\n")
                    .append("There is a running action.");
            JOptionPane.showMessageDialog(ProductLibraryToolViewV2.this, message.toString(), "Add local repository folder", JOptionPane.ERROR_MESSAGE);
        } else {
            Path selectedLocalRepositoryFolder = showDialogToSelectLocalFolder("Select folder to add the products");
            if (selectedLocalRepositoryFolder != null) {
                ProgressBarHelperImpl progressBarHelper = this.repositoryProductListPanel.getProgressBarHelper();
                int threadId = progressBarHelper.incrementAndGetCurrentThreadId();
                AllLocalFolderProductsRepository allLocalFolderProductsRepository = this.repositorySelectionPanel.getAllLocalProductsRepositoryPanel().getAllLocalFolderProductsRepository();
                this.localRepositoryProductsThread = new AddLocalRepositoryFolderTimerRunnable(progressBarHelper, threadId, selectedLocalRepositoryFolder, allLocalFolderProductsRepository) {
                    @Override
                    protected void onFinishRunning() {
                        ProductLibraryToolViewV2.this.localRepositoryProductsThread = null; // reset
                        searchProductListLater();
                    }

                    @Override
                    protected void onFinishSavingProduct(SaveProductData saveProductData) {
                        ProductLibraryToolViewV2.this.repositorySelectionPanel.finishSavingProduct(saveProductData);
                    }
                };
                this.localRepositoryProductsThread.executeAsync(); // start the thread
            }
        }
    }

    private void deleteAllLocalRepositoriesButtonPressed() {
        AllLocalProductsRepositoryPanel allLocalProductsRepositoryPanel = this.repositorySelectionPanel.getAllLocalProductsRepositoryPanel();
        List<LocalRepositoryFolder> localRepositoryFoldersToDelete = allLocalProductsRepositoryPanel.getLocalRepositoryFolders();
        if (localRepositoryFoldersToDelete.size() > 0) {
            // there are local repositories into the application
            String dialogTitle = "Delete local products";
            if (this.localRepositoryProductsThread != null || this.downloadRemoteProductsHelper.isRunning()) {
                StringBuilder message = new StringBuilder();
                message.append("The local repositories cannot be deleted.")
                        .append("\n\n")
                        .append("There is a running action.");
                JOptionPane.showMessageDialog(this, message.toString(), dialogTitle, JOptionPane.ERROR_MESSAGE);
            } else {
                StringBuilder message = new StringBuilder();
                message.append("All the local repositories will be deleted.")
                        .append("\n\n")
                        .append("Are you sure you want to continue?");
                int answer = JOptionPane.showConfirmDialog(this, message.toString(), dialogTitle, JOptionPane.YES_NO_OPTION);
                if (answer == JOptionPane.YES_OPTION) {
                    allLocalProductsRepositoryPanel.clearInputParameterComponentValues();
                    this.repositoryProductListPanel.clearOutputList(true);
                    ProgressBarHelperImpl progressBarHelper = this.repositorySelectionPanel.getProgressBarHelper();
                    int threadId = progressBarHelper.incrementAndGetCurrentThreadId();
                    AllLocalFolderProductsRepository allLocalFolderProductsRepository = this.repositorySelectionPanel.getAllLocalProductsRepositoryPanel().getAllLocalFolderProductsRepository();
                    this.localRepositoryProductsThread = new DeleteAllLocalRepositoriesTimerRunnable(progressBarHelper, threadId, localRepositoryFoldersToDelete, allLocalFolderProductsRepository) {
                        @Override
                        protected void onFinishRunning() {
                            onFinishDeletingLocalProducts(this);
                        }

                        @Override
                        protected void onLocalRepositoryFolderDeleted(LocalRepositoryFolder localRepositoryFolder) {
                            ProductLibraryToolViewV2.this.deleteLocalRepositoryFolder(localRepositoryFolder);
                        }
                    };
                    this.localRepositoryProductsThread.executeAsync(); // start the thread
                }
            }
        }
    }

    private void onFinishDeletingLocalProducts(Runnable invokerThread) {
        if (invokerThread == this.localRepositoryProductsThread) {
            this.localRepositoryProductsThread = null; // reset
            this.repositoryProductListPanel.updateProductListCountTitle();
            searchProductListLater();
        }
    }

    private void deleteLocalRepositoryFolder(LocalRepositoryFolder localRepositoryFolderToRemove) {
        AllLocalProductsRepositoryPanel allLocalProductsRepositoryPanel = this.repositorySelectionPanel.getAllLocalProductsRepositoryPanel();
        allLocalProductsRepositoryPanel.deleteLocalRepositoryFolder(localRepositoryFolderToRemove);
    }

    private void openLocalSelectedProducts() {
        RepositoryProduct[] unopenedSelectedProducts = processUnopenedSelectedProducts();
        OutputProductListModel productListModel = this.repositoryProductListPanel.getProductListPanel().getProductListModel();
        List<RepositoryProduct> productsToOpen = productListModel.addPendingOpenProducts(unopenedSelectedProducts);
        if (productsToOpen.size() > 0) {
            OpenLocalProductsRunnable runnable = new OpenLocalProductsRunnable(this.appContext, this.repositoryProductListPanel, productsToOpen);
            runnable.executeAsync(); // start the thread
        }
    }

    private void deleteLocalSelectedProducts() {
        RepositoryProduct[] unopenedSelectedProducts = processUnopenedSelectedProducts();
        if (unopenedSelectedProducts.length > 0) {
            // there are selected products into the output table
            StringBuilder message = new StringBuilder();
            if (unopenedSelectedProducts.length > 1) {
                message.append("The selected products");
            } else {
                message.append("The selected product");
            }
            message.append(" will be deleted.")
                    .append("\n\n")
                    .append("Are you sure you want to continue?");
            int answer = JOptionPane.showConfirmDialog(this, message.toString(), "Delete local products", JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) {
                OutputProductListModel productListModel = this.repositoryProductListPanel.getProductListPanel().getProductListModel();
                List<RepositoryProduct> productsToDelete = productListModel.addPendingDeleteProducts(unopenedSelectedProducts);
                if (productsToDelete.size() > 0) {
                    AllLocalFolderProductsRepository allLocalFolderProductsRepository = this.repositorySelectionPanel.getAllLocalProductsRepositoryPanel().getAllLocalFolderProductsRepository();
                    DeleteLocalProductsRunnable runnable = new DeleteLocalProductsRunnable(this.appContext, this.repositoryProductListPanel, productsToDelete, allLocalFolderProductsRepository);
                    runnable.executeAsync(); // start the thread
                }
            }
        }
    }

    private RepositoryProduct[] processUnopenedSelectedProducts() {
        RepositoryProduct[] selectedProducts = this.repositoryProductListPanel.getProductListPanel().getSelectedProducts();
        List<RepositoryProduct> availableLocalProducts = new ArrayList<>(selectedProducts.length);
        for (int i=0; i<selectedProducts.length; i++) {
            Product product = this.appContext.getProductManager().getProduct(selectedProducts[i].getName());
            if (product == null) {
                // the local product is not opened in the application
                availableLocalProducts.add(selectedProducts[i]);
            }
        }
        selectedProducts = new RepositoryProduct[availableLocalProducts.size()];
        availableLocalProducts.toArray(selectedProducts);
        return selectedProducts;
    }

    private void openBatchProcessingDialog() {
        RepositoryProduct[] selectedProducts = this.repositoryProductListPanel.getProductListPanel().getSelectedProducts();
        File[] selectedProductsFiles = new File[selectedProducts.length];
        for (int i = 0; i < selectedProducts.length; i++) {
            selectedProductsFiles[i] = ((LocalRepositoryProduct) selectedProducts[i]).getPath().toFile();
        }
        BatchGraphDialog batchDialog = new BatchGraphDialog(this.appContext, "Batch Processing", "batchProcessing", true);
        batchDialog.setInputFiles(selectedProductsFiles);
        batchDialog.show();
    }

    private void showSelectedLocalProductInExplorer() {
        RepositoryProduct[] selectedProducts = this.repositoryProductListPanel.getProductListPanel().getSelectedProducts();
        Path selectedProductPath = ((LocalRepositoryProduct) selectedProducts[0]).getPath();
        try {
            Desktop.getDesktop().open(selectedProductPath.toFile());
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Failed to open the product in the explorer.", exception);
        }
    }

    private void leftMouseButtonClicked(List<Path2D.Double> polygonPaths) {
        this.repositoryProductListPanel.getProductListPanel().selectProductsByPolygonPath(polygonPaths);
    }

    private void productListChanged() {
        Path2D.Double[] polygonPaths = this.repositoryProductListPanel.getProductListPanel().getPolygonPaths();
        this.worldWindowPanel.setPolygons(polygonPaths);
    }

    private void newSelectedRepositoryProducts() {
        RepositoryProduct[] selectedProducts = this.repositoryProductListPanel.getProductListPanel().getSelectedProducts();
        int totalPathCount = 0;
        for (int i = 0; i < selectedProducts.length; i++) {
            totalPathCount += selectedProducts[i].getPolygon().getPathCount();
        }
        Path2D.Double[] polygonPaths = new Path2D.Double[totalPathCount];
        for (int i = 0, index=0; i < selectedProducts.length; i++) {
            AbstractGeometry2D productGeometry = selectedProducts[i].getPolygon();
            for (int p=0; p<productGeometry.getPathCount(); p++) {
                polygonPaths[index++] = productGeometry.getPathAt(p);
            }
        }
        this.worldWindowPanel.highlightPolygons(polygonPaths);
        if (polygonPaths.length == 1) {
            this.worldWindowPanel.setEyePosition(polygonPaths[0]);
        }
    }

    private void cancelSearchingProductList() {
        this.repositorySelectionPanel.getProgressBarHelper().hideProgressPanel();
        if (this.searchProductListThread != null) {
            this.searchProductListThread.cancelRunning(); // stop the thread
        }
        this.repositoryProductListPanel.updateProductListCountTitle();
        this.downloadRemoteProductsHelper.cancelDownloadingProductsQuickLookImage();
    }

    private void onSelectedNewProductsRepository() {
        cancelSearchingProductList();
        int dividerLocation = this.horizontalSplitPane.getDividerLocation();
        AbstractProductsRepositoryPanel selectedProductsRepositoryPanel = this.repositorySelectionPanel.getSelectedProductsRepositoryPanel();
        this.horizontalSplitPane.setLeftComponent(selectedProductsRepositoryPanel);
        this.horizontalSplitPane.setDividerLocation(dividerLocation);
        this.horizontalSplitPane.revalidate();
        this.horizontalSplitPane.repaint();
        selectedProductsRepositoryPanel.addInputParameterComponents();
        boolean refreshed = selectedProductsRepositoryPanel.refreshInputParameterComponentValues();
        if (refreshed) {
            this.repositoryProductListPanel.refreshOutputList();
        } else {
            this.repositoryProductListPanel.clearOutputList(true);
        }
    }

    private void onSelectedNewProductsRepositoryMission() {
        cancelSearchingProductList();
        AbstractProductsRepositoryPanel selectedProductsRepositoryPanel = this.repositorySelectionPanel.getSelectedProductsRepositoryPanel();
        selectedProductsRepositoryPanel.addInputParameterComponents();
        selectedProductsRepositoryPanel.resetInputParameterValues();
        this.repositoryProductListPanel.clearOutputList(true);
    }

    private void cancelDownloadingProducts() {
        this.downloadRemoteProductsHelper.cancelDownloadingProducts(); // stop the thread
        this.repositoryProductListPanel.getProgressBarHelper().hideProgressPanel();
    }

    private Path showDialogToSelectLocalFolder(String dialogTitle) {
        CustomFileChooser fileChooser = buildFileChooser(dialogTitle, false, JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        if (this.lastLocalRepositoryFolderPath != null) {
            fileChooser.setCurrentDirectoryPath(this.lastLocalRepositoryFolderPath);
        }
        int result = fileChooser.showDialog(this, "Select");
        if (result == JFileChooser.APPROVE_OPTION) {
            this.lastLocalRepositoryFolderPath = fileChooser.getSelectedPath();
            // save the folder path into the preferences
            this.appContext.getPreferences().setPropertyString(PREFERENCES_KEY_LAST_LOCAL_REPOSITORY_FOLDER_PATH, this.lastLocalRepositoryFolderPath.toString());
            return this.lastLocalRepositoryFolderPath;
        }
        return null;
    }

    private void openDownloadedRemoteProductsButtonPressed() {
        RepositoryProduct[] unopenedSelectedProducts = processUnopenedSelectedProducts();
        OutputProductListModel productListModel = this.repositoryProductListPanel.getProductListPanel().getProductListModel();
        Map<RepositoryProduct, Path> productsToOpen = productListModel.addPendingOpenDownloadedProducts(unopenedSelectedProducts);
        if (productsToOpen.size() > 0) {
            OpenDownloadedProductsRunnable runnable = new OpenDownloadedProductsRunnable(this.appContext, this.repositoryProductListPanel, productsToOpen);
            runnable.executeAsync(); // start the thread
        }
    }

    private void outputProductsPageChanged() {
        this.downloadRemoteProductsHelper.cancelDownloadingProductsQuickLookImage();
        if (this.localRepositoryProductsThread == null) {
            AbstractProductsRepositoryPanel selectedProductsRepositoryPanel = this.repositorySelectionPanel.getSelectedProductsRepositoryPanel();
            if (selectedProductsRepositoryPanel instanceof RemoteProductsRepositoryPanel) {
                OutputProductListModel productListModel = this.repositoryProductListPanel.getProductListPanel().getProductListModel();
                List<RepositoryProduct> productsWithoutQuickLookImage = productListModel.findProductsWithoutQuickLookImage();
                if (productsWithoutQuickLookImage.size() > 0) {
                    if (logger.isLoggable(Level.FINE)) {
                        int currentPageNumber = selectedProductsRepositoryPanel.getOutputProductResults().getCurrentPageNumber();
                        String repositoryName = selectedProductsRepositoryPanel.getRepositoryName();
                        logger.log(Level.FINE, "Start downloading the quick look images for " + productsWithoutQuickLookImage.size()+" products from page number " + currentPageNumber + " using the '" +repositoryName+"' remote repository.");
                    }

                    RemoteProductsRepositoryPanel remoteProductsRepositoryPanel = (RemoteProductsRepositoryPanel)selectedProductsRepositoryPanel;
                    Credentials selectedCredentials = remoteProductsRepositoryPanel.getSelectedAccount();
                    RemoteProductsRepositoryProvider productsRepositoryProvider = remoteProductsRepositoryPanel.getProductsRepositoryProvider();
                    this.downloadRemoteProductsHelper.downloadProductsQuickLookImageAsync(productsWithoutQuickLookImage, productsRepositoryProvider, selectedCredentials, this.repositoryProductListPanel);
                }
            }
        }
    }

    private void downloadSelectedRemoteProductsButtonPressed() {
        OutputProductListPanel productListPanel = this.repositoryProductListPanel.getProductListPanel();
        RepositoryProduct[] selectedProducts = productListPanel.getSelectedProducts();
        if (selectedProducts.length > 0) {
            // there are selected products into the output table
            if (this.localRepositoryProductsThread == null) {
                // there is no running thread for the local repository products
                Path selectedLocalRepositoryFolder = showDialogToSelectLocalFolder("Select folder to download the product");
                if (selectedLocalRepositoryFolder != null) {
                    AbstractProductsRepositoryPanel selectedRepository = this.repositorySelectionPanel.getSelectedProductsRepositoryPanel();
                    if (selectedRepository instanceof RemoteProductsRepositoryPanel) {
                        RemoteProductsRepositoryPanel remoteProductsRepositoryPanel = (RemoteProductsRepositoryPanel) selectedRepository;
                        Credentials selectedCredentials = remoteProductsRepositoryPanel.getSelectedAccount();
                        RemoteProductsRepositoryProvider remoteProductsRepositoryProvider = remoteProductsRepositoryPanel.getProductsRepositoryProvider();
                        AllLocalFolderProductsRepository allLocalFolderProductsRepository = this.repositorySelectionPanel.getAllLocalProductsRepositoryPanel().getAllLocalFolderProductsRepository();
                        this.downloadRemoteProductsHelper.downloadProductsAsync(selectedProducts, remoteProductsRepositoryProvider, selectedLocalRepositoryFolder,
                                                                                selectedCredentials, allLocalFolderProductsRepository);
                        // refresh the products in the output panel
                        productListPanel.getProductListModel().refreshProducts();
                    } else {
                        throw new IllegalStateException("The selected repository is not a remote repository.");
                    }
                }
            } else {
                StringBuilder message = new StringBuilder();
                if (selectedProducts.length > 1) {
                    message.append("The selected products");
                } else {
                    message.append("The selected product");
                }
                message.append(" cannot be downloaded.")
                        .append("\n\n")
                        .append("There is a running action .");
                JOptionPane.showMessageDialog(ProductLibraryToolViewV2.this, message.toString(), "Download products", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onFinishSearchingProducts(Runnable invokerThread) {
        if (invokerThread == ProductLibraryToolViewV2.this.searchProductListThread) {
            this.searchProductListThread = null; // reset
            this.repositoryProductListPanel.updateProductListCountTitle();
        }
    }

    private void searchButtonPressed() {
        cancelSearchingProductList();
        ThreadListener threadListener = new ThreadListener() {
            @Override
            public void onStopExecuting(Runnable invokerThread) {
                onFinishSearchingProducts(invokerThread);
            }
        };
        ProgressBarHelperImpl progressBarHelper = this.repositorySelectionPanel.getProgressBarHelper();
        int threadId = progressBarHelper.incrementAndGetCurrentThreadId();
        AbstractProductsRepositoryPanel selectedProductsRepositoryPanel = this.repositorySelectionPanel.getSelectedProductsRepositoryPanel();
        RemoteRepositoriesSemaphore remoteRepositoriesSemaphore = this.downloadRemoteProductsHelper.getRemoteRepositoriesSemaphore();
        AbstractProgressTimerRunnable<?> thread = selectedProductsRepositoryPanel.buildSearchProductListThread(progressBarHelper, threadId, threadListener,
                                                                                            remoteRepositoriesSemaphore, this.repositoryProductListPanel);
        if (thread != null) {
            this.repositoryProductListPanel.clearOutputList(true);
            progressBarHelper.updateProgressBarText(threadId, getSearchingProductListMessage() + "...");
            this.searchProductListThread = thread;
            this.searchProductListThread.executeAsync(); // start the thread
        }
    }

    public static String getSearchingProductListMessage() {
        return "Searching product list";
    }

    private static CustomFileChooser buildFileChooser(String dialogTitle, boolean multiSelectionEnabled, int fileSelectionMode) {
        boolean previousReadOnlyFlag = UIManager.getDefaults().getBoolean(CustomFileChooser.FILE_CHOOSER_READ_ONLY_KEY);
        CustomFileChooser fileChooser = new CustomFileChooser(previousReadOnlyFlag);
        fileChooser.setDialogTitle(dialogTitle);
        fileChooser.setMultiSelectionEnabled(multiSelectionEnabled);
        fileChooser.setFileSelectionMode(fileSelectionMode);
        return fileChooser;
    }
}
